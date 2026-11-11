-- pgTAP: booking integrity. Run with `supabase test db`.
-- Each `-- GAP:` test FAILS on the schema as deployed today (audit, Nov 2026) and passes after
-- supabase/02_booking_integrity.sql.
begin;
select plan(13);

-- fixtures ----------------------------------------------------------------------------------
insert into public.sports (id, name, slot_duration_minutes, open_time, close_time)
values ('00000000-0000-0000-0000-00000000aa01', 'Padel', 60, '08:00', '22:00');
insert into public.courts (id, name, sport_id)
values ('00000000-0000-0000-0000-00000000cc01', 'Court A', '00000000-0000-0000-0000-00000000aa01'),
       ('00000000-0000-0000-0000-00000000cc02', 'Court B', '00000000-0000-0000-0000-00000000aa01');
-- villa accounts (auth.users exists on a Supabase stack; is_admin() needs none of them to be admins)
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000f1', 'v1@myapp.local'),
  ('00000000-0000-0000-0000-0000000000f2', 'v2@myapp.local')
on conflict do nothing;

-- a club day far in the future, 10:00 Africa/Cairo (winter, +02:00)
create temp table t as select
  '00000000-0000-0000-0000-00000000cc01'::uuid as court_a,
  '00000000-0000-0000-0000-00000000cc02'::uuid as court_b,
  '00000000-0000-0000-0000-0000000000f1'::uuid as v1,
  '00000000-0000-0000-0000-0000000000f2'::uuid as v2,
  timestamptz '2030-01-15 10:00+02' as ten,
  timestamptz '2030-01-15 11:00+02' as eleven,
  timestamptz '2030-01-15 12:00+02' as twelve;

-- I1 two villas, same slot -----------------------------------------------------------------
select lives_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_a, v1, ten, eleven, 'confirmed' from t $q$, 'first booking of 10:00 is accepted');

-- GAP B-01: without an exclusion constraint the second insert succeeds -> double booking
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_a, v2, ten, eleven, 'confirmed' from t $q$, '23P01',
  null, 'B-01: a second confirmed row on the same court and hour must be rejected (23P01)');

-- adjacent slots must NOT collide (half-open bounds)
select lives_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_a, v2, eleven, twelve, 'confirmed' from t $q$, 'adjacent 11:00 slot is still free');

-- I3 cancel, re-book, un-cancel ------------------------------------------------------------
update public.bookings set status = 'cancelled' where user_id = (select v1 from t);
select lives_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_a, v2, ten, eleven, 'confirmed' from t $q$, 'B-03: a cancelled slot can be booked again (exclusion must be partial)');
-- GAP B-01/B-03: un-cancelling onto an occupied slot must fail
select throws_ok($q$ update public.bookings set status = 'confirmed'
  where user_id = (select v1 from t) and status = 'cancelled' $q$, '23P01',
  null, 'B-03: un-cancelling onto an occupied slot is rejected');

-- I4 status spelled differently -------------------------------------------------------------
-- GAP B-02: no CHECK on status; 'Confirmed' escapes a partial exclusion and is invisible to both UIs
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, ten, eleven, 'Confirmed' from t $q$, '23514',
  null, 'B-02: status outside confirmed/cancelled is rejected');

-- I6 empty / inverted ranges ---------------------------------------------------------------
-- GAP B-05: start == end is an EMPTY tstzrange, overlaps nothing, blocks nobody
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, ten, ten, 'confirmed' from t $q$, '23514',
  null, 'B-05: end_time must be after start_time');
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, eleven, ten, 'confirmed' from t $q$, '23514',
  null, 'B-05: inverted range is rejected with 23514, not with a tstzrange error');

-- I5 misaligned rows -----------------------------------------------------------------------
-- GAP B-04: nothing pins rows to the sport grid; 10:30-11:30 blocks two slots the tablets show as free
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, ten + interval '30 min', eleven + interval '30 min', 'confirmed' from t $q$, 'P0001',
  null, 'B-04: start must sit on the sport grid');
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, ten, twelve, 'confirmed' from t $q$, 'P0001',
  null, 'B-04: a booking must be exactly one slot long');
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, timestamptz '2030-01-15 22:00+02', timestamptz '2030-01-15 23:00+02', 'confirmed' from t $q$, 'P0001',
  null, 'B-04: a booking cannot start at/after closing time');

-- I7 per-villa limit ----------------------------------------------------------------------------
-- GAP B-06: "max 2 active bookings per sport" lives only in BookingViewModel.confirmBooking
set local role authenticated;
select set_config('request.jwt.claims', json_build_object('sub', (select v1::text from t), 'role', 'authenticated')::text, true);
select lives_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, ten, eleven, 'confirmed' from t $q$, 'villa v1 active booking #1');
select lives_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, eleven, twelve, 'confirmed' from t $q$, 'villa v1 active booking #2');
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status)
  select court_b, v1, twelve, twelve + interval '1 hour', 'confirmed' from t $q$, 'P0001',
  null, 'B-06: a third active booking for the same sport is rejected by the database');
reset role;

select * from finish();
rollback;
