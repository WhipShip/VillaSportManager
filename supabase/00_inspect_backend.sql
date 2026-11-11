-- Kayan Club · backend inspection (read-only, one query)
-- Supabase Dashboard → SQL Editor → paste → Run.
-- Returns ONE cell of JSON: click it, copy the whole value, and send it back.
-- Nothing here changes data.

select json_build_object(
  -- where villa names live today
  'users', (
    select json_agg(json_build_object(
      'username',     split_part(email, '@', 1),
      'domain',       split_part(email, '@', 2),
      'display_name', raw_user_meta_data ->> 'display_name',
      'created_at',   created_at
    ) order by created_at)
    from auth.users
  ),
  'user_metadata_keys', (
    select json_agg(distinct k)
    from auth.users, jsonb_object_keys(coalesce(raw_user_meta_data, '{}'::jsonb)) as k
  ),
  -- public tables / views and whether row-level security is on
  'tables', (
    select json_agg(json_build_object(
      'name', c.relname,
      'kind', case c.relkind when 'v' then 'view' else 'table' end,
      'rls_enabled', c.relrowsecurity
    ) order by c.relname)
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r', 'p', 'v')
  ),
  -- existing RLS policies
  'policies', (
    select json_agg(json_build_object(
      'table', tablename, 'name', policyname, 'cmd', cmd,
      'roles', roles, 'using', qual, 'check', with_check
    ) order by tablename, policyname)
    from pg_policies
    where schemaname = 'public'
  ),
  -- columns of every public table
  'columns', (
    select json_agg(json_build_object(
      'table', table_name, 'column', column_name, 'type', data_type,
      'nullable', is_nullable, 'default', column_default
    ) order by table_name, ordinal_position)
    from information_schema.columns
    where table_schema = 'public'
  ),
  -- constraints on bookings (incl. the overlap rule)
  'booking_constraints', (
    select json_agg(json_build_object('name', conname, 'definition', pg_get_constraintdef(oid)))
    from pg_constraint
    where conrelid = to_regclass('public.bookings')
  ),
  -- custom triggers already on auth.users
  'auth_user_triggers', (
    select json_agg(tgname)
    from pg_trigger
    where tgrelid = to_regclass('auth.users') and not tgisinternal
  ),
  -- tables broadcast over realtime
  'realtime_tables', (
    select json_agg(schemaname || '.' || tablename)
    from pg_publication_tables
    where pubname = 'supabase_realtime'
  ),
  -- postgres version (the admin view needs 15+)
  'postgres_version', current_setting('server_version')
) as backend;
