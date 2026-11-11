# Database tests (pgTAP)

These tests encode the booking invariants the tablet app and the admin page rely on but the
repository never defined (the `bookings` table itself was created by hand in the Supabase
dashboard and is not under version control).

They are written for the Supabase CLI test runner:

```bash
supabase start                 # local stack (Docker)
psql "$(supabase status -o env | grep DB_URL | cut -d= -f2-)" -f supabase/tests/01_schema_assumed.sql
psql ... -f supabase/01_admin_portal.sql
supabase test db               # runs every *.test.sql in this folder
```

Before `supabase/02_booking_integrity.sql` and `supabase/03_booking_rls_hardening.sql` are applied,
the tests under `-- GAP:` comments **fail**. That is the point: each failing test is a double-booking
or integrity gap from `audit/README.md`.

Because the live schema is unknown, `01_schema_assumed.sql` recreates the minimum `sports`, `courts`
and `bookings` tables the app code reads. If your real DDL differs, replace that file with a dump
(`supabase db dump --schema public`) so the tests run against the real thing.

To check the production project without running anything destructive, paste
`supabase/00b_check_booking_integrity.sql` into the SQL editor: it prints PASS/FAIL per expectation.

The audit sandbox had no Docker, psql or Supabase CLI and no network, so these tests were written
but **not executed**. Treat the first run as part of the verification.
