-- Minimum schema the app code reads. ONLY used by the pgTAP tests when no dump of the real schema
-- is available. Column names/types come from Models.kt, api.ts and 01_admin_portal.sql.
-- Deliberately WITHOUT the overlap exclusion: the tests must show what happens without it, and
-- 02_booking_integrity.sql must add it idempotently.
create extension if not exists pgcrypto;
create table if not exists public.sports (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slot_duration_minutes int not null default 60,
  open_time time not null default '08:00',
  close_time time not null default '22:00'
);
create table if not exists public.courts (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  sport_id uuid not null references public.sports (id),
  is_active boolean not null default true
);
create table if not exists public.bookings (
  id uuid primary key default gen_random_uuid(),
  court_id uuid not null references public.courts (id),
  user_id uuid not null,
  start_time timestamptz not null,
  end_time timestamptz not null,
  status text not null default 'confirmed',
  created_at timestamptz not null default now()
);
alter table public.sports enable row level security;
alter table public.courts enable row level security;
alter table public.bookings enable row level security;
-- Policies as described in 01_admin_portal.sql comments ("Users can view all bookings",
-- "villas insert/update their OWN rows"). The real policy text is unknown.
create policy "Users can view all bookings" on public.bookings for select to authenticated using (true);
create policy "Users can insert own bookings" on public.bookings for insert to authenticated with check (user_id = auth.uid());
create policy "Users can update own bookings" on public.bookings for update to authenticated using (user_id = auth.uid());
create policy "read sports" on public.sports for select to authenticated using (true);
create policy "read courts" on public.courts for select to authenticated using (true);
