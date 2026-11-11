-- Kayan Club · admin portal backend
-- Run once in Supabase Dashboard → SQL Editor. Safe to re-run.
-- Only ADDS tables, columns, policies and a view; existing app behaviour is unchanged.
-- Checked against your project (Oct 2026): RLS is already ON for sports, courts and bookings,
-- there is no profiles table yet, and Postgres is 17.6 (the security_invoker view needs 15+).

-- ---------------------------------------------------------------------------
-- 1. Admins
-- ---------------------------------------------------------------------------
create table if not exists public.admins (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security;

-- SECURITY DEFINER so policies can call it without recursive RLS checks.
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.admins where user_id = auth.uid());
$$;
revoke all on function public.is_admin() from public;
grant execute on function public.is_admin() to authenticated;

drop policy if exists "kayan: admins read admins" on public.admins;
create policy "kayan: admins read admins"
  on public.admins for select to authenticated
  using (public.is_admin());

-- ---------------------------------------------------------------------------
-- 2. Profiles: one row per account with its villa name
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id         uuid primary key references auth.users (id) on delete cascade,
  villa_name text not null,
  created_at timestamptz not null default now()
);
alter table public.profiles enable row level security;

drop policy if exists "kayan: read own profile or admin" on public.profiles;
create policy "kayan: read own profile or admin"
  on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_admin());

drop policy if exists "kayan: admins manage profiles" on public.profiles;
create policy "kayan: admins manage profiles"
  on public.profiles for all to authenticated
  using (public.is_admin())
  with check (public.is_admin());

-- New accounts get a profile automatically (display_name, else the username part of the email).
create or replace function public.kayan_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, villa_name)
  values (new.id, coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), split_part(new.email, '@', 1)))
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists kayan_on_auth_user_created on auth.users;
create trigger kayan_on_auth_user_created
  after insert on auth.users
  for each row execute function public.kayan_handle_new_user();

-- Backfill existing accounts.
insert into public.profiles (id, villa_name)
select id, coalesce(nullif(raw_user_meta_data ->> 'display_name', ''), split_part(email, '@', 1))
from auth.users
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Bookings: court blocks + admin access
-- ---------------------------------------------------------------------------
-- kind = 'block' marks a slot an admin closed (maintenance, events).
-- Blocks are ordinary confirmed rows, one per slot, so the tablet app shows them as
-- "Booked" with no app update, and the existing overlap constraint still applies.
alter table public.bookings add column if not exists kind       text not null default 'booking';
alter table public.bookings add column if not exists note       text;
alter table public.bookings add column if not exists created_by uuid references auth.users (id);

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'bookings_kind_check') then
    alter table public.bookings
      add constraint bookings_kind_check check (kind in ('booking', 'block'));
  end if;
end;
$$;

-- Reading: the existing "Users can view all bookings" policy already lets every signed-in
-- account (admins included) read bookings, so no extra SELECT policy is needed.
-- Writing: existing policies only allow a villa to insert/update its OWN rows. These add admins.
drop policy if exists "kayan: admins create bookings" on public.bookings;
create policy "kayan: admins create bookings"
  on public.bookings for insert to authenticated
  with check (public.is_admin());

drop policy if exists "kayan: admins update bookings" on public.bookings;
create policy "kayan: admins update bookings"
  on public.bookings for update to authenticated
  using (public.is_admin())
  with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- 4. Schedule view for the admin site (runs with the caller's permissions)
-- ---------------------------------------------------------------------------
create or replace view public.admin_schedule
with (security_invoker = true) as
select
  b.id,
  b.start_time,
  b.end_time,
  b.status,
  b.kind,
  b.note,
  b.court_id,
  c.name       as court_name,
  s.id         as sport_id,
  s.name       as sport_name,
  b.user_id,
  p.villa_name,
  b.created_by
from public.bookings b
join public.courts  c on c.id = b.court_id
join public.sports  s on s.id = c.sport_id
left join public.profiles p on p.id = b.user_id
-- Villas can already read raw bookings (existing policy), but this joined view is admin-only.
where public.is_admin();

grant select on public.admin_schedule to authenticated;

-- ---------------------------------------------------------------------------
-- 5. Make an account an admin (edit the email, then run this line)
--    Create the account first: Authentication → Users → Add user → admin@myapp.local
-- ---------------------------------------------------------------------------
-- insert into public.admins (user_id)
-- select id from auth.users where email = 'admin@myapp.local'
-- on conflict do nothing;
