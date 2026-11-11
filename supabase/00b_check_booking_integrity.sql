-- Kayan Club · READ-ONLY check of what the database actually enforces on public.bookings.
-- Supabase Dashboard → SQL Editor → paste → Run. Changes nothing.
-- One row per expectation: PASS / FAIL plus the live definition, so the audit findings can be
-- confirmed against the real project (the schema is not in the repository).
with cons as (
  select conname, contype, pg_get_constraintdef(oid) as def
  from pg_constraint where conrelid = to_regclass('public.bookings')
),
excl as (select * from cons where contype = 'x'),
trg as (
  select tgname from pg_trigger
  where tgrelid = to_regclass('public.bookings') and not tgisinternal
),
pol as (
  select policyname, cmd, permissive, qual, with_check
  from pg_policies where schemaname = 'public' and tablename = 'bookings'
),
checks as (
  select 'EXCL-1 overlap exclusion exists (court_id =, tstzrange &&)' as expectation,
         exists (select 1 from excl where def ilike '%court_id%' and def ilike '%&&%') as pass,
         coalesce((select string_agg(conname || ': ' || def, ' | ') from excl), '<none>') as detail
  union all
  select 'EXCL-2 exclusion is partial on status = confirmed (else cancelled rows block re-booking)',
         exists (select 1 from excl where def ilike '%where%status%confirmed%'),
         coalesce((select string_agg(def, ' | ') from excl), '<none>')
  union all
  select 'EXCL-3 range bounds are half-open [) (else 10-11 and 11-12 collide)',
         -- tstzrange(a, b) defaults to [) and pg_get_constraintdef omits the default, so only a closed
         -- upper bound literal is a failure
         not exists (select 1 from excl where def ilike '%tstzrange(%' and (def ilike '%''[]''%' or def ilike '%''(]''%')),
         coalesce((select string_agg(def, ' | ') from excl), '<none>')
  union all
  select 'CHECK-1 status restricted to confirmed/cancelled',
         exists (select 1 from cons where contype = 'c' and def ilike '%status%confirmed%cancelled%'),
         coalesce((select string_agg(conname || ': ' || def, ' | ') from cons where contype = 'c'), '<none>')
  union all
  select 'CHECK-2 end_time > start_time',
         exists (select 1 from cons where contype = 'c' and def ilike '%end_time%>%start_time%'),
         coalesce((select string_agg(conname || ': ' || def, ' | ') from cons where contype = 'c'), '<none>')
  union all
  select 'TRIG-1 slot grid trigger (kayan_check_slot_grid)',
         exists (select 1 from trg where tgname ilike '%slot_grid%'),
         coalesce((select string_agg(tgname, ', ') from trg), '<none>')
  union all
  select 'TRIG-2 per-villa limit trigger (kayan_check_booking_limit)',
         exists (select 1 from trg where tgname ilike '%booking_limit%'),
         coalesce((select string_agg(tgname, ', ') from trg), '<none>')
  union all
  select 'RLS-1 villa INSERT policy pins user_id = auth.uid()',
         exists (select 1 from pol where cmd = 'INSERT' and coalesce(with_check, '') ilike '%auth.uid()%'),
         coalesce((select string_agg(policyname || ' [' || cmd || '] check=' || coalesce(with_check, '-'), ' | ') from pol), '<none>')
  union all
  select 'RLS-2 a RESTRICTIVE UPDATE policy stops villas un-cancelling / re-timing rows',
         exists (select 1 from pol where permissive = 'RESTRICTIVE' and cmd = 'UPDATE'),
         coalesce((select string_agg(policyname || ' [' || cmd || '/' || permissive || ']', ' | ') from pol), '<none>')
  union all
  select 'UPD-1 clients cannot write app_versions / app_patches',
         not exists (select 1 from information_schema.role_table_grants
                     where table_schema = 'public' and table_name in ('app_versions', 'app_patches')
                       and grantee in ('anon', 'authenticated') and privilege_type in ('INSERT', 'UPDATE', 'DELETE')),
         coalesce((select string_agg(table_name || ':' || grantee || ':' || privilege_type, ', ') from information_schema.role_table_grants
                   where table_schema = 'public' and table_name in ('app_versions', 'app_patches') and grantee in ('anon', 'authenticated')), '<none>')
  union all
  select 'UPD-2 app_versions / app_patches have RLS enabled',
         coalesce((select bool_and(relrowsecurity) from pg_class where oid in (to_regclass('public.app_versions'), to_regclass('public.app_patches'))), false),
         coalesce((select string_agg(relname || '=' || relrowsecurity, ', ') from pg_class where oid in (to_regclass('public.app_versions'), to_regclass('public.app_patches'))), '<tables missing>')
  union all
  select 'DATA-1 no overlapping confirmed rows exist today (would make adding the exclusion fail)',
         not exists (select 1 from public.bookings a join public.bookings b
                     on a.court_id = b.court_id and a.id < b.id and a.status = 'confirmed' and b.status = 'confirmed'
                     and tstzrange(a.start_time, a.end_time, '[)') && tstzrange(b.start_time, b.end_time, '[)')),
         (select count(*)::text || ' overlapping pairs' from public.bookings a join public.bookings b
            on a.court_id = b.court_id and a.id < b.id and a.status = 'confirmed' and b.status = 'confirmed'
           and tstzrange(a.start_time, a.end_time, '[)') && tstzrange(b.start_time, b.end_time, '[)'))
  union all
  select 'DATA-2 no rows with status outside confirmed/cancelled, or end_time <= start_time',
         not exists (select 1 from public.bookings where status not in ('confirmed', 'cancelled') or end_time <= start_time),
         (select count(*)::text || ' bad rows' from public.bookings where status not in ('confirmed', 'cancelled') or end_time <= start_time)
)
select case when pass then 'PASS' else 'FAIL' end as result, expectation, detail from checks;
