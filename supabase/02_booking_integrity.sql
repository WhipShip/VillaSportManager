-- Kayan Club · booking integrity (audit fix PR 1: findings B-01 … B-06, B-08)
-- Run once in Supabase Dashboard → SQL Editor, AFTER 01_admin_portal.sql. Safe to re-run.
--
-- What it does and why (see audit/README.md):
--   1. CHECK status in ('confirmed','cancelled')     a row spelled 'Confirmed' escapes a partial
--                                                      exclusion constraint and is invisible to both UIs
--   2. CHECK end_time > start_time                     start = end is an EMPTY range: blocks nobody, but
--                                                      the admin page draws it as a 24-hour bar
--   3. EXCLUDE overlap, partial on confirmed rows      THE double-booking rule. The table was created in
--                                                      the dashboard; the repo never recorded the rule.
--                                                      Created if missing; replaced if it also covers
--                                                      cancelled rows (they would block re-booking).
--   4. trigger kayan_check_slot_grid                   rows must be exactly one slot of the sport, start
--                                                      on the grid, inside opening hours; villas cannot
--                                                      book a slot that has already ended
--   5. trigger kayan_check_booking_limit               "max 2 active bookings per sport" moves from the
--                                                      tablet (TOCTOU: two tablets, one account) into
--                                                      the DB, serialised with an advisory lock
--   6. column client_request_id (unique)               idempotency key so a kiosk killed mid-request
--                                                      (e.g. by an app update) can retry safely
--
-- BEFORE running: paste supabase/00b_check_booking_integrity.sql; DATA-1 and DATA-2 must be PASS,
-- otherwise steps 1-3 refuse to add the constraints. Fix those rows first (set status = 'cancelled').
--
-- Error codes the clients map to messages: 23P01 (overlap), 23514 (CHECK), P0001 with HINT in
-- {slot_length, outside_hours, misaligned, past_slot, booking_limit} (triggers).

create extension if not exists btree_gist;

-- ---------------------------------------------------------------------------
-- 1. status vocabulary
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_constraint
                 where conrelid = 'public.bookings'::regclass and conname = 'bookings_status_check') then
    alter table public.bookings
      add constraint bookings_status_check check (status in ('confirmed', 'cancelled'));
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 2. ranges must be non-empty and forward
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_constraint
                 where conrelid = 'public.bookings'::regclass and conname = 'bookings_time_order_check') then
    alter table public.bookings
      add constraint bookings_time_order_check check (end_time > start_time);
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 3. the overlap rule: one confirmed row per court per instant, half-open bounds
-- ---------------------------------------------------------------------------
do $$
declare
  r record;
  keep boolean := false;
begin
  for r in
    select conname, pg_get_constraintdef(oid) as def
    from pg_constraint
    where conrelid = 'public.bookings'::regclass and contype = 'x'
  loop
    if r.def ilike '%where%status%confirmed%'
       and r.def not ilike '%''[]''%' and r.def not ilike '%''(]''%' then
      raise notice 'keeping existing exclusion constraint %: %', r.conname, r.def;
      keep := true;
    else
      -- Not partial (cancelled rows keep blocking the slot) or closed bounds (10-11 collides with 11-12).
      raise notice 'replacing exclusion constraint % (%)', r.conname, r.def;
      execute format('alter table public.bookings drop constraint %I', r.conname);
    end if;
  end loop;

  if not keep then
    alter table public.bookings
      add constraint bookings_no_overlap_confirmed
      exclude using gist (court_id with =, tstzrange(start_time, end_time, '[)') with &&)
      where (status = 'confirmed');
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. rows must be real slots of their sport
-- ---------------------------------------------------------------------------
create or replace function public.kayan_check_slot_grid()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  s            record;
  local_start  timestamp;
  local_end    timestamp;
  open_ts      timestamp;
  close_ts     timestamp;
  mins         integer;
begin
  if new.status <> 'confirmed' then
    return new;
  end if;

  select sp.slot_duration_minutes, sp.open_time, sp.close_time
    into s
    from public.courts c
    join public.sports sp on sp.id = c.sport_id
   where c.id = new.court_id;
  if not found then
    raise exception 'slot_grid: unknown court %', new.court_id using errcode = 'P0001', hint = 'unknown_court';
  end if;

  -- exact length, measured in real elapsed time (safe across DST changes)
  if new.end_time - new.start_time <> make_interval(mins => s.slot_duration_minutes) then
    raise exception 'slot_grid: a booking must be exactly % minutes long', s.slot_duration_minutes
      using errcode = 'P0001', hint = 'slot_length';
  end if;

  -- grid and opening hours are wall-clock rules in the club's zone (same zone as both clients)
  local_start := new.start_time at time zone 'Africa/Cairo';
  local_end   := new.end_time   at time zone 'Africa/Cairo';
  open_ts  := local_start::date + s.open_time;
  close_ts := local_start::date + s.close_time;
  if s.close_time <= s.open_time then
    -- sport closes after midnight: a slot after midnight belongs to the previous club day
    if local_start::time < s.open_time then
      open_ts  := open_ts  - interval '1 day';
      close_ts := close_ts;               -- already the "next" midnight-relative close
    else
      close_ts := close_ts + interval '1 day';
    end if;
  end if;
  if local_start < open_ts or local_end > close_ts then
    raise exception 'slot_grid: % - % is outside opening hours % - %', local_start, local_end, s.open_time, s.close_time
      using errcode = 'P0001', hint = 'outside_hours';
  end if;

  mins := (extract(epoch from (local_start - open_ts)) / 60)::integer;
  if mins % s.slot_duration_minutes <> 0 then
    raise exception 'slot_grid: start % is not on the % minute grid', local_start::time, s.slot_duration_minutes
      using errcode = 'P0001', hint = 'misaligned';
  end if;

  -- villas cannot book a slot that already ended; admins and the SQL editor/service role may (backfill)
  if new.end_time <= now() and auth.uid() is not null and not public.is_admin() then
    raise exception 'slot_grid: that slot has already ended' using errcode = 'P0001', hint = 'past_slot';
  end if;

  return new;
end;
$$;

drop trigger if exists kayan_check_slot_grid on public.bookings;
create trigger kayan_check_slot_grid
  before insert or update of start_time, end_time, court_id, status on public.bookings
  for each row execute function public.kayan_check_slot_grid();

-- ---------------------------------------------------------------------------
-- 5. max 2 active bookings per villa per sport (villa-made bookings only)
-- ---------------------------------------------------------------------------
create or replace function public.kayan_check_booking_limit()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sport   public.sports.id%type;
  v_active  integer;
  v_limit   constant integer := 2;
begin
  if new.status <> 'confirmed' or coalesce(new.kind, 'booking') <> 'booking' then
    return new;
  end if;
  -- Admin page bookings for a villa and SQL-editor/service-role rows bypass the limit by design.
  if auth.uid() is null or public.is_admin() then
    return new;
  end if;

  select sport_id into v_sport from public.courts where id = new.court_id;

  -- Serialise concurrent inserts for the same villa + sport; READ COMMITTED means the count below
  -- sees whatever the previous holder committed.
  perform pg_advisory_xact_lock(hashtext('kayan_booking_limit'), hashtext(new.user_id::text || ':' || v_sport::text));

  select count(*) into v_active
    from public.bookings b
    join public.courts c on c.id = b.court_id
   where b.user_id = new.user_id
     and c.sport_id = v_sport
     and b.status = 'confirmed'
     and coalesce(b.kind, 'booking') = 'booking'
     and b.end_time > now()
     and b.id <> new.id;

  if v_active >= v_limit then
    raise exception 'booking_limit: already % active bookings for this sport (max %)', v_active, v_limit
      using errcode = 'P0001', hint = 'booking_limit';
  end if;
  return new;
end;
$$;

drop trigger if exists kayan_check_booking_limit on public.bookings;
create trigger kayan_check_booking_limit
  before insert or update of status, user_id, court_id on public.bookings
  for each row execute function public.kayan_check_booking_limit();

-- ---------------------------------------------------------------------------
-- 6. idempotency key for kiosk retries
-- ---------------------------------------------------------------------------
alter table public.bookings add column if not exists client_request_id uuid;
create unique index if not exists bookings_client_request_id_key
  on public.bookings (client_request_id) where client_request_id is not null;
