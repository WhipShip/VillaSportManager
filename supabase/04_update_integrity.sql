-- Kayan Club · update pipeline integrity (audit fix PR 4: findings U-01, U-02, U-03)
-- Run once in Supabase Dashboard → SQL Editor. Safe to re-run. Only ADDS nullable columns.
--
-- The kiosk (from app version 18) verifies every download against these values when present:
--   app_versions.sha256        SHA-256 of the full APK            app_versions.size_bytes  its size
--   app_patches.sha256         SHA-256 of the .patch file
--   app_patches.base_sha256    SHA-256 of the APK the patch was generated FROM (what is installed)
--   app_patches.target_sha256  SHA-256 of the APK the patch produces (defaults to the version's sha256)
--
-- Publishing checklist (PowerShell):  (Get-FileHash app-release.apk -Algorithm SHA256).Hash.ToLower()
--                        (bash):      sha256sum app-release.apk | cut -d' ' -f1
-- Rows without hashes keep working (nothing is verified for them), so this can be rolled out gradually.

do $$
begin
  if to_regclass('public.app_versions') is not null then
    alter table public.app_versions add column if not exists sha256     text;
    alter table public.app_versions add column if not exists size_bytes bigint;
    if not exists (select 1 from pg_constraint where conname = 'app_versions_sha256_format') then
      alter table public.app_versions
        add constraint app_versions_sha256_format check (sha256 is null or sha256 ~ '^[0-9a-f]{64}$');
    end if;
  else
    raise notice 'public.app_versions does not exist, skipping';
  end if;

  if to_regclass('public.app_patches') is not null then
    alter table public.app_patches add column if not exists sha256        text;
    alter table public.app_patches add column if not exists base_sha256   text;
    alter table public.app_patches add column if not exists target_sha256 text;
    if not exists (select 1 from pg_constraint where conname = 'app_patches_sha256_format') then
      alter table public.app_patches
        add constraint app_patches_sha256_format check (
          (sha256        is null or sha256        ~ '^[0-9a-f]{64}$') and
          (base_sha256   is null or base_sha256   ~ '^[0-9a-f]{64}$') and
          (target_sha256 is null or target_sha256 ~ '^[0-9a-f]{64}$'));
    end if;
  else
    raise notice 'public.app_patches does not exist, skipping';
  end if;
end;
$$;
