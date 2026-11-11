# Correctness audit: slot booking + Supabase update pipeline

Date: 2026-11-11 · Scope: Android tablet app (`app/`), Supabase backend (`supabase/`), admin page (`admin-web/`).

## Scope notes (read first)

- **There is no payment or checkout module.** "Checkout" in this product is `BookingViewModel.confirmBooking()` → one `INSERT` into `bookings` → the success screen. No price, no payment state, no gateway. Nothing to audit under that heading; the booking confirm path is covered below.
- **There is no kiosk / dedicated-device code.** No `DevicePolicyManager`, no `startLockTask`, no HOME intent filter. Dedicated-device mode, if used, is provided outside this repo (launcher or MDM). Its interaction with the update flow is in U-05 and could not be verified.
- **The database schema is not in the repository and the live project was unreachable** from the audit sandbox (DNS blocked; no Docker, psql or Supabase CLI; no device on adb). The `bookings` table and the overlap constraint the README names (`bookings_court_id_tstzrange_excl`) exist only in the dashboard. Every statement below about what the DB enforces is therefore **"believed, not verified"** until `supabase/00b_check_booking_integrity.sql` is run in the SQL editor (read-only, prints PASS/FAIL per expectation).

## Ranked findings

Severity: Critical = double booking or data loss possible today · High = integrity rule missing, exploitable with a villa password · Medium = wrong behaviour, bounded impact · Low = edge case / UX.

| # | Sev | Finding | Where | Evidence | Fix |
|---|-----|---------|-------|----------|-----|
| B-01 | Critical | **All double-booking protection is one unversioned DB constraint whose existence and shape are unverified.** The tablet never re-checks availability before `INSERT`; the admin page checks only its cache. If the exclusion is absent → two tablets or tablet+admin double-book (I1, I2). If it is not partial on `status = 'confirmed'` → cancelled slots are blocked forever (I3). | `BookingRepository.createBooking`, `BookingViewModel.confirmBooking`, `admin-web/src/lib/api.ts` | model I1–I3; pgTAP B-01/B-03 | **PR 1** creates or replaces it; `00b` checker |
| B-02 | High | No `CHECK` on `status`. A row with `status = 'Confirmed'` (any villa JWT via PostgREST) escapes a partial exclusion, is filtered out by both UIs (`eq status confirmed`) yet shows as **CONFIRMED** in that villa's My Bookings. Second villa books the same hour (I4). | DB | model I4; pgTAP B-02 | **PR 1** |
| B-03 | High | Villa write surface is unknown and nothing prevents **un-cancelling** or **re-timing** a row (`UPDATE status='confirmed'`, `UPDATE start_time`). Also spoofing `user_id` and creating `kind='block'` rows cannot be ruled out. | RLS (dashboard) | pgTAP R-01…R-04 | **PR 2** restrictive policies |
| B-04 | High | Rows are not pinned to the sport grid. A `10:30–11:30` row (API) blocks two hours, but the tablet keys "booked" by **exact start time**, so 10:00 and 11:00 look free and every tap fails with 23P01 (I5). | `BookingViewModel` `bookedByTime` | model I5; pgTAP B-04 | **PR 1** trigger |
| B-05 | High | `end_time <= start_time` accepted. An empty `tstzrange` overlaps nothing, so the "same" 10:00 can be booked twice; admin page draws it as a 24-hour bar (I6, B-07). | DB | model I6; pgTAP B-05; admin test fails | **PR 1** `CHECK` |
| B-06 | High | "Max 2 active bookings per sport" exists only in the tablet: `SELECT count` then `INSERT`, two round trips. One account on two tablets (or a retry) → 3+ bookings (I7). Admin bookings bypass it (by design, keep). | `BookingViewModel.confirmBooking` | model I7; pgTAP B-06 | **PR 1** trigger + advisory lock; **PR 3** maps the error |
| U-01 | High | **No integrity check anywhere in the update path.** Patch chosen by `from_version_code` only; `bspatch` is applied to whatever `sourceDir` holds with no base hash. A patch built against a different build of the same versionCode yields garbage; installer rejects; **Retry re-downloads the same patch forever** (no fallback to full APK). | `UpdateRepository`, `UpdateUtils.applyPatch`, `UpdaterViewModel.downloadAndApply` | code | **PR 4** |
| U-02 | High | Download is not atomic: streams straight into `update.apk`, no temp+rename, no `Content-Length` check; the only guard is `length() > 1024`. Storage path uses `downloadPublic()` which buffers the **whole APK in memory** (OOM risk on 1–2 GB tablets). | `UpdateRepository.downloadFile` | code | **PR 4** |
| B-08 | Medium | Tablet shows **every slot of today as Available**, including ones that already ended (only the scroll position uses "now"). Admin page hides them. | `BookingViewModel.generateSlots`, `BookingScreen.ScrollToNow` | model I8 | **PR 3** + **PR 1** |
| B-09 | Medium | Lost-response bookings: no idempotency key. If the process dies after the `INSERT` was sent (Wi-Fi drop, **update install killing the app**), the villa sees no confirmation and typically books another slot → ghost booking, eats the limit. | `createBooking` | code | **PR 1** column + **PR 3** |
| U-03 | Medium | Version check = highest `version_code` wins. No `is_published`, no min-version, no staged rollout, **no rollback path** (Android refuses downgrades; only a new higher version helps). Grants/RLS on `app_versions`/`app_patches` unknown: if villas can write, any villa password pushes a "version" to every kiosk (APK signature check bounds this to a DoS, not code execution). | `UpdateRepository.getLatestVersion` | code | **PR 2** grants; **PR 4** hash columns; `is_published` recommended |
| U-04 | Medium | Mid-booking: silent check runs on **every Activity creation** and the `UpdateAvailable` dialog pops over any screen, including the booking flow. `checkForUpdates()` during `Downloading` overwrites state → second "Update Now" starts a **second concurrent download into the same file**. | `MainActivity`, `UpdaterViewModel` | code | **PR 5** |
| U-05 | Medium | After **Install**, the system installer kills the process; nothing relaunches the app (no device-owner silent install, no HOME role). Kiosk sits on the launcher until a human reopens it. | `UpdateUtils.installApk` | not verifiable without device | recommend DPC `PackageInstaller` session or HOME role; not fixed |
| B-07 | Medium | Admin page: `bookingMinutes()` turns `end <= start` into `end + 1440` → 24 h bar and **all free slots of that court vanish** from "Book for a villa". | `admin-web/src/lib/time.ts` | `time.test.ts`, `schedule.test.ts` fail | prevented upstream by PR 1; 3-line UI fix recommended |
| B-11 | Low | Realtime: if the socket dies without throwing, `flow.collect` never returns → cache stale until restart (every stale tablet relies on the DB saying no). `isRealtimeInitialized=false` on error lets a second loop start on the next `sessionStatus` emission. | `BookingRepository.initializeGlobalRealtime` | code | recommend `realtime.status` watcher + periodic refresh; not fixed |
| B-10 | Low | Egypt DST fall-back night: 23:00 occurs twice. Tablet (`java.time`) picks the earlier offset, admin `clubIso()` the later one → two different instants for "23:00", exclusion cannot relate them. | `admin-web/src/lib/time.ts` | `time.test.ts` fails | not fixed (once a year, only if open at 23:00) |
| B-12 | Low | `close_time <= open_time` (overnight): tablet generates **zero** slots, admin generates a full night. | `generateSlots` vs `slotStarts` | code | not fixed |
| B-14 | Low | A villa can cancel an already-played booking through the API (UI hides the button) → history drift. | RLS | code | PR 2 could add `start_time > now()`; not included |
| U-06 | Low | `installApk` returns silently if the cached APK was evicted or `REQUEST_INSTALL_PACKAGES` is not granted (opens Settings, flow never resumes). | `UpdateUtils.installApk` | code | UX; not fixed |
| U-07 | Low | Patching holds old APK + patch + new APK in memory (~3× APK size). | `UpdateUtils.applyPatch` | code | not fixed |

## Formal model of the slot booking state machine

**Resources.** A sport `S` has `slot_duration_minutes d`, `open_time`, `close_time` and courts `c`. A *slot* is `R = (c, [t, t+d))` with `t` on the grid `open + k·d` inside opening hours. All four sports use the same machinery (padel/tennis/football/pool differ only in `d` and hours).

**Persistent state.** Rows `(id, court, user, kind ∈ {booking, block}, status, [start, end))`. Everything else (tablet caches, admin bars, selections) is derived and may be stale.

**Derived slot state.** `Occ(R) = { rows r : r.status = 'confirmed' ∧ r.court = c ∧ r.range ∩ R ≠ ∅ }`.
Slot is `FREE` iff `Occ(R) = ∅`, `BOOKED` iff `|Occ(R)| = 1`, **`DOUBLE`** iff `|Occ(R)| ≥ 2`.

**Actors.** `T1, T2` tablets (any villa accounts, possibly the same one), `A` admin page, `API` anything holding a villa JWT + the publishable key, `DB`.

**Transitions.**

| op | actor | precondition (client-side only) | effect |
|----|-------|---------------------------------|--------|
| `refresh` | T, A | – | cache := confirmed rows (`eq status 'confirmed'`) |
| `select(R)` | T | no cached confirmed row **starting exactly at** `t` | selection := R |
| `select(R)` | A | no cached row **overlapping** R | selection := R |
| `countActive` | T | – | `n := SELECT count(confirmed, end > now, same user & sport)`; abort if `n ≥ 2` |
| `insert(R)` | T, A | selection = R | `INSERT (c, user, confirmed, [t, t+d))` |
| `block(R1..Rn)` | A | no overlap in cache | one multi-row `INSERT` (atomic) |
| `cancel(id)` | owner, A | – | `UPDATE status='cancelled'` |
| `uncancel(id)`, `retime(id)`, `insert(any shape)` | API | – | arbitrary `UPDATE`/`INSERT` within RLS |

**Invariants.**
- **INV1** (no double booking) ∀R: `|{ live rows overlapping R }| ≤ 1`, where *live* = `status ≠ 'cancelled'`.
- **INV2** (visibility) every live row is rendered "Booked" on every tablet for every grid slot it touches, after one refresh.
- **INV3** (business rule) per villa and sport, villa-made confirmed rows with `end > now` ≤ 2.
- **INV4** (liveness) a slot whose only row was cancelled is bookable again.

**DB enforcement models.** `D0` none · `D1` `EXCLUDE (court_id =, tstzrange &&)` over all rows · `D2` same, `WHERE status='confirmed'` (what the README claims) · `D3` = D2 + PR 1 (`CHECK status`, `CHECK end > start`, grid trigger, limit trigger).

**Interleavings and results** (all interleavings enumerated by `audit/model/booking-model.test.ts`, per-actor order preserved):

| # | interleaving (one violating trace) | violates | D0 | D1 | D2 | D3 |
|---|------------------------------------|----------|----|----|----|----|
| I1 | `T1.refresh · T2.refresh · T1.select(R) · T2.select(R) · T1.insert · T2.insert` | INV1 | ✖ | ✔ | ✔ | ✔ |
| I2 | `T1.refresh · A.refresh · T1.select(R) · A.book(R) · T1.insert` | INV1 | ✖ | ✔ | ✔ | ✔ |
| I3 | `T1 books R · T1.cancel · T2 books R · API.uncancel(T1)` — and `T1.cancel · T2 books R` alone under D1 | INV1 / INV4 | ✖ | ✖ (INV4) | ✔ | ✔ |
| I4 | `API.insert(R, status='Confirmed') · T2.refresh · T2.select(R) · T2.insert` | INV1, INV2 | ✖ | ✖ | ✖ | ✔ |
| I5 | `API.insert([10:30,11:30)) · T2.refresh · T2.select(11:00)` → shown free, insert fails 23P01 | INV2 | ✖ | ✖ | ✖ | ✔ |
| I6 | `T2.refresh · API.insert([10:00,10:00)) · T2.select(10:00) · T2.countActive · T2.insert` | INV1 | ✖ | ✖ | ✖ | ✔ |
| I7 | `T1.countActive(=1) · T2.countActive(=1) · T1.insert · T2.insert` (same account) | INV3 | ✖ | ✖ | ✖ | ✔ |
| I8 | `T1.select(08:00 at 09:30) · T1.insert` | business rule | ✖ | ✖ | ✖ | ✔ |
| I9 | `A.block(R1..Rn)` vs `T1.insert(Rk)` | INV1 | ✖ | ✔ | ✔ | ✔ (single statement; exclusion checks within the statement too) |
| I10 | `T1.insert` sent · process killed by update install · response lost · villa books R′ | ghost booking | ✖ | ✖ | ✖ | ✔ with PR 3 (`client_request_id` lookup) |
| I11 | realtime socket silently dead · `T2` cache stale for hours · `T2.insert(R)` | INV2 only | ✖ | safe (23P01) | safe | safe |

Model run: 33 cases, **15 fail** (all 8 under D0, 6 under D1, 5 under D2, 0 under D3). Run `node --test audit/model/booking-model.test.ts`.

**What the DB does / does not enforce (as far as can be told).** Transaction isolation is irrelevant here: PostgREST issues single-statement transactions, so the only tools that work are constraints checked at write time (exclusion, unique, check) and triggers with explicit serialisation. There is no row lock or `SELECT … FOR UPDATE` anywhere, and none is needed if PR 1 is applied. The exclusion constraint, if present, is also enforced on `UPDATE`, which is why I3's uncancel is safe under D2.

## Failing tests delivered

| Suite | Run | Status (on `master` schema/code) |
|-------|-----|-----------------------------------|
| `audit/model/booking-model.test.ts` | `node --test audit/model/booking-model.test.ts` | 15 / 33 fail (the matrix above) |
| `admin-web/src/lib/time.test.ts`, `schedule.test.ts` | `cd admin-web && npm test` | 3 / 8 fail (B-07 ×2, B-10) |
| `supabase/tests/booking_integrity.test.sql` (pgTAP) | `supabase test db` | 9 of 13 expected to fail before PR 1 — **not executed here** |
| `supabase/tests/booking_rls.test.sql` (pgTAP) | `supabase test db` | 4 of 5 expected to fail before PR 2 — **not executed here** |
| `app/src/test/.../BookingErrorMapperTest.kt` (PR 3), `UpdateIntegrityTest.kt` (PR 4) | `./gradlew :app:testDebugUnitTest` | pass with their PR (regression guards, not gap tests) |

## Update pipeline review (Supabase `app_versions` / `app_patches` / bucket `updates`)

**Version check.** `MainActivity.onCreate` → `checkForUpdates(isSilent = true)` on every creation (rotation included). `getLatestVersion()` = `ORDER BY version_code DESC LIMIT 1`; update if `> BuildConfig.VERSION_CODE` (17). There is no published flag, no minimum version, no rollout percentage, no device targeting. A mistyped row is pushed to every tablet at next launch. In silent mode a found update still raises the modal dialog, so the "silent" check is not silent. Who may write these tables is unknown (U-03).

**Patch selection.** `app_patches WHERE from = current AND to = latest`, first row. There is no base-hash column, so a patch is matched to a versionCode, not to the bytes actually installed (`sourceDir`). Any rebuild with the same versionCode, or a split/bundle install, produces a corrupt output (U-01).

**Download.** `downloadFile()` writes straight into the final file, with `HttpURLConnection` for `http*` paths (no timeout configured) or `downloadPublic()` (whole file in RAM) otherwise. No size or hash check; `length() > 1024` is the acceptance test. Cleartext is blocked by `network_security_config` only for the Supabase domain; a plain `http://` `apk_path` is blocked by the platform default (targetSdk ≥ 28), which is good (U-02).

**Partial download / rollback behaviour.** An exception → `Error` state; the partial file stays and is truncated on the next attempt (fine). A silent truncation → "App not installed" from the system installer with no diagnostic. After a patch failure the ViewModel never falls back to the full APK: **Retry** repeats the same failing patch. There is no rollback: Android refuses to install a lower versionCode without `adb -d`, so a bad release can only be fixed forward by publishing a higher one; meanwhile every kiosk that installed it is stuck (U-01, U-03).

**Install.** `ACTION_VIEW` + `FileProvider` → system installer UI; needs `REQUEST_INSTALL_PACKAGES` granted once per device (otherwise the app opens Settings and the flow is abandoned). The installer kills the process. Without device-owner / `PackageInstaller` session APIs nothing brings the kiosk app back (U-05, U-06).

**Kiosk mid-booking during an update.**
1. `UpdateAvailable` dialog can appear over the booking screen; "Close" dismisses it, booking state is untouched. With `Downloading`/`Patching` there is no Close button, but tapping outside still sets `Idle` while the download continues and later re-raises `ReadyToInstall` (U-04).
2. A second `checkForUpdates()` (Activity recreation) during `Downloading` resets state to `UpdateAvailable`; a second **Update Now** starts a second download into the same `update.apk` while the first is still writing (U-04).
3. If **Install** is tapped while `confirmBooking()` is in flight, the process dies after the `INSERT` left the device. The row is committed; the tablet never shows the success screen; after relaunch the slot appears booked and the villa, lacking confirmation, books another one (B-09, I10). No duplicate row results because the client never retries, but the limit is consumed.
4. During the system installer the realtime socket dies; on relaunch the cache rebuilds from scratch (`preloadData`) — correct.

**Fixes.** PR 4 adds `sha256`/`base_sha256`/`target_sha256`/`size_bytes` (nullable, verified when present), streams downloads to a `.part` file and renames atomically, verifies the installed APK before patching and the output after, falls back to the full APK when the patch path fails, and ignores update checks while a download is running. PR 5 defers the prompt and the install while a booking write is in flight or the user is not on the home screen, adds a persisted "Later" snooze, and makes the silent check truly silent. Not done: `is_published`/min-version columns, device-owner silent install, server-side signing of `app_versions`.

## Fix PRs (branches, all off `master`)

| PR | Branch | Closes | Verification |
|----|--------|--------|--------------|
| 1 | `fix/1-db-booking-integrity` | B-01, B-02, B-04, B-05, B-06, B-08 (DB side), B-09 (column) | SQL reviewed; pgTAP written, **not run** (no Postgres here) |
| 2 | `fix/2-db-rls-villa-write-surface` | B-03, R-01…R-04, U-03 (grants) | SQL reviewed; pgTAP written, **not run** |
| 3 | `fix/3-android-booking-hardening` | B-06 (UX), B-08, B-09, error mapping | `:app:testDebugUnitTest` (see table below) |
| 4 | `fix/4-android-updater-integrity` | U-01, U-02, U-03 (hash columns), U-04 (double download) | `:app:testDebugUnitTest` |
| 5 | `fix/5-android-updater-mid-booking` | U-04, B-09 (install while writing) | `:app:compileDebugKotlin` |

Order of application: run `00b` checker → PR 1 SQL → PR 2 SQL → ship PR 3+4+5 as the next APK (version 18), whose `app_versions` row carries `sha256`.

## What was NOT verified

1. **The live database.** Existence, name and definition of the overlap exclusion constraint; RLS policies on `bookings`, `sports`, `courts`; grants and RLS on `app_versions`/`app_patches`; the `supabase_realtime` publication; storage policies on bucket `updates`. The sandbox could not resolve `*.supabase.co`. → Run `supabase/00b_check_booking_integrity.sql` (read-only).
2. **pgTAP tests were written, not executed.** No Docker/psql/Supabase CLI available. The assumed DDL in `supabase/tests/01_schema_assumed.sql` may differ from the real tables (e.g. `court_id` type).
3. **Android runtime behaviour.** No device/emulator: installer flow, `FileProvider` paths, `REQUEST_INSTALL_PACKAGES`, behaviour after install, realtime reconnects, actual PostgREST error text/fields in `supabase-kt 3.8.0` (the error mapper therefore matches on message text, which Postgres always includes).
4. **Release build / R8.** Only `compileDebugKotlin` and JVM unit tests were run. `assembleRelease` with minification was not.
5. **Admin page build.** `npm run build` (tsc 7 + vite) was not run; the new tests are excluded from `tsc -b` via `tsconfig` and run with `node --test` only.
6. **Egypt DST rules** on the tablets' tzdata vs the browsers' ICU; B-10 assumes current rules (last Friday of April → last Thursday of October).
7. **Dedicated-device provisioning** (lock task, HOME role, auto-relaunch) — none of it is in the repo.
8. **Realtime delivery guarantees** (RLS-filtered broadcasts require the publication to include `bookings`; the tablet's loop assumes it).
