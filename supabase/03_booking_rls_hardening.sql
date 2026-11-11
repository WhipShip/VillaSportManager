-- Kayan Club · what a villa account may write (audit fix PR 2: findings R-01 … R-04, U-05)
-- Run once in Supabase Dashboard → SQL Editor, AFTER 01_admin_portal.sql. Safe to re-run.
--
-- The tablet signs in with ordinary villa credentials and the publishable key. Anyone who knows a
-- villa's password can therefore call PostgREST directly and is limited ONLY by RLS and constraints.
-- The existing villa policies were created in the dashboard and are not in this repo; the audit could
-- not read them. Everything below is a RESTRICTIVE policy: it is AND-ed with whatever permissive
-- policies exist, so it can only narrow access and never grants anything new.
--
--   villa INSERT   only its own user_id, kind = 'booking', status = 'confirmed'
--   villa UPDATE   only its own rows, and the result must be status = 'cancelled'
--                  (no un-cancelling, no re-timing, no moving to another court)
--   villa DELETE   never
--   admins         unchanged (public.is_admin())
--
-- Update-pipeline tables: clients must never be able to publish a version or a patch.

-- ---------------------------------------------------------------------------
-- 1. bookings
-- ---------------------------------------------------------------------------
alter table public.bookings enable row level security;

drop policy if exists "kayan: villas insert only their own bookings" on public.bookings;
create policy "kayan: villas insert only their own bookings"
  on public.bookings as restrictive for insert to authenticated
  with check (
    public.is_admin()
    or (
      user_id = auth.uid()
      and coalesce(kind, 'booking') = 'booking'
      and status = 'confirmed'
      and (created_by is null or created_by = auth.uid())
    )
  );

drop policy if exists "kayan: villas may only cancel their own bookings" on public.bookings;
create policy "kayan: villas may only cancel their own bookings"
  on public.bookings as restrictive for update to authenticated
  using (public.is_admin() or user_id = auth.uid())
  with check (public.is_admin() or (user_id = auth.uid() and status = 'cancelled'));

drop policy if exists "kayan: only admins delete bookings" on public.bookings;
create policy "kayan: only admins delete bookings"
  on public.bookings as restrictive for delete to authenticated
  using (public.is_admin());

-- ---------------------------------------------------------------------------
-- 2. app_versions / app_patches: read-only for every client role
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['app_versions', 'app_patches'] loop
    if to_regclass('public.' || t) is null then
      raise notice 'table public.% does not exist, skipping', t;
      continue;
    end if;
    execute format('revoke insert, update, delete, truncate on public.%I from anon, authenticated', t);
    execute format('alter table public.%I enable row level security', t);
    -- The kiosk checks for updates before anyone signs in, so anon must keep SELECT.
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = t and cmd = 'SELECT') then
      execute format('create policy %I on public.%I for select to anon, authenticated using (true)',
                     'kayan: clients read ' || t, t);
    end if;
  end loop;
end;
$$;

-- Storage: the `updates` bucket must stay public-read but client-write must be disabled. Bucket
-- policies live in storage.objects and cannot be inspected from here; check in Dashboard → Storage →
-- Policies that only the service role / dashboard can INSERT into bucket 'updates'.
