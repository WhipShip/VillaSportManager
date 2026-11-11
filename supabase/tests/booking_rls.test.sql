-- pgTAP: what a villa JWT can do to bookings through PostgREST. Run with `supabase test db`.
-- The existing policies were created in the dashboard and are not in the repo; the audit could not
-- read them. These tests state what MUST hold; `-- GAP:` ones fail until
-- supabase/03_booking_rls_hardening.sql is applied (restrictive policies, so they are safe to add
-- next to whatever permissive policies already exist).
begin;
select plan(5);

insert into public.sports (id, name) values ('00000000-0000-0000-0000-00000000aa01', 'Padel');
insert into public.courts (id, name, sport_id) values ('00000000-0000-0000-0000-00000000cc01', 'Court A', '00000000-0000-0000-0000-00000000aa01');
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000f1', 'v1@myapp.local'),
  ('00000000-0000-0000-0000-0000000000f2', 'v2@myapp.local') on conflict do nothing;
insert into public.bookings (id, court_id, user_id, start_time, end_time, status) values
  ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-00000000cc01', '00000000-0000-0000-0000-0000000000f2',
   '2030-01-15 10:00+02', '2030-01-15 11:00+02', 'confirmed');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000f1","role":"authenticated"}', true);

-- GAP R-01: a villa must not be able to create rows for another villa (user_id spoofing)
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status) values
  ('00000000-0000-0000-0000-00000000cc01', '00000000-0000-0000-0000-0000000000f2', '2030-01-15 12:00+02', '2030-01-15 13:00+02', 'confirmed') $q$,
  '42501', null, 'R-01: villa cannot insert a booking with someone else''s user_id');

-- GAP R-02: a villa must not be able to create admin "blocks"
select throws_ok($q$ insert into public.bookings (court_id, user_id, start_time, end_time, status, kind) values
  ('00000000-0000-0000-0000-00000000cc01', '00000000-0000-0000-0000-0000000000f1', '2030-01-15 12:00+02', '2030-01-15 13:00+02', 'confirmed', 'block') $q$,
  '42501', null, 'R-02: villa cannot insert kind = block');

-- a villa cannot touch another villa's row (RLS USING should hide it: 0 rows updated, no error)
update public.bookings set status = 'cancelled' where id = '00000000-0000-0000-0000-0000000000b2';
select is((select status from public.bookings where id = '00000000-0000-0000-0000-0000000000b2'), 'confirmed',
  'R-03: villa cannot cancel another villa''s booking');

-- own row: cancel is fine, un-cancel / re-time is not
reset role;
insert into public.bookings (id, court_id, user_id, start_time, end_time, status) values
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-00000000cc01', '00000000-0000-0000-0000-0000000000f1',
   '2030-01-15 14:00+02', '2030-01-15 15:00+02', 'cancelled');
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000f1","role":"authenticated"}', true);
-- GAP R-04: nothing stops a villa from flipping its own cancelled row back to confirmed, or moving it
select throws_ok($q$ update public.bookings set status = 'confirmed' where id = '00000000-0000-0000-0000-0000000000b1' $q$,
  '42501', null, 'R-04: villa cannot un-cancel its own booking');
select throws_ok($q$ update public.bookings set start_time = '2030-01-15 16:00+02', end_time = '2030-01-15 17:00+02'
  where id = '00000000-0000-0000-0000-0000000000b1' $q$,
  '42501', null, 'R-04: villa cannot re-time a booking');

select * from finish();
rollback;
