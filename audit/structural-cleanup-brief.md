# Task: structural cleanup of VillaSportManager

## Context
- Repo: `D:\Work\sport_club_managerV2\VillaSportManager`.
- `app/` is an Android tablet app (Kotlin, Compose, supabase-kt). Sources are under
  `app/src/main/java/com/example/villasportmanager/`.
- `admin-web/` is the front-desk site (React, Vite, supabase-js).
- Both talk straight to one Supabase project. The sandbox has no network or database
  access: you write SQL files, the owner runs them in the Supabase SQL Editor.
- `audit/README.md` holds an earlier correctness audit. Read it first.
- Line numbers below were taken on master before Phase 0. After the merges, find the
  code by symbol name, not by line.

## Facts confirmed by the owner (do not re-open)
- `supabase/03_booking_rls_hardening.sql` and `04_update_integrity.sql` are already
  applied to the live database.
- Hash columns on `app_versions` and `app_patches` are deliberately left empty. Do not
  add code or docs that require them.
- The `instruments` table is unused and may be dropped.
- The `frontdesk` account is admin-only and never signs in on a tablet.
- The old permissive policies on `bookings` ("Users can ...") grant access; the
  `kayan:` policies only narrow it. Never drop or alter any `bookings` policy.

## Rules
- Work on a branch off master. One commit per phase. Do not push unless asked.
- No rewrites, no new dependencies, no DI framework.
- Behaviour must not change except where a step says so.
- After every phase run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`.
  If web files changed, also run `cd admin-web && npm run typecheck && npm test`.
- Three web tests fail by design (audit findings B-07 and B-10). Do not edit those
  tests to make them pass.
- Do not change `applicationId`, the Supabase URL or key, or any SQL file already on
  master except `00_inspect_backend.sql` (Phase 1 step 3).
- Before deleting any symbol, grep for callers. If one exists, stop and report.

## Phase 0: bring master up to date
1. Merge `fix/5-android-updater-mid-booking` (commit `f1013ae`). It touches
   `BookingRepository.kt`, `UpdaterViewModel.kt`, `UpdaterScreen.kt`,
   `AppNavigation.kt` and `MainActivity.kt`, which later phases also edit.
2. Merge `origin/fix/2-db-rls-villa-write-surface` (commit `61f3279`) to bring
   `supabase/03_booking_rls_hardening.sql` and its test onto the branch.
Both merge cleanly into master.

## Phase 1: small fixes and SQL files
1. Wrong greeting. `HomeScreen.kt:162` falls back to the literal `"Villa 14!"`, which
   is a real villa's name. Fall back to the username (the email part before `@`).
2. One source for the villa name. Add `suspend fun villaName()` to `AuthRepository`:
   read `profiles.villa_name` for the current user, fall back to the `display_name`
   auth metadata. Use it for the home greeting in place of `getDisplayName()` at
   `AppNavigation.kt:104`. Row-level security already lets a villa read its own
   profile row.
3. Extend `supabase/00_inspect_backend.sql` with three new keys in its JSON output:
   - non-internal triggers on `public.bookings` (name and function);
   - the `permissive` value for every policy, from `pg_policies`;
   - index definitions on `bookings` and `sports`, from `pg_indexes`.
4. Write `supabase/05_cleanup.sql`. It must be safe to re-run. It does exactly this:
   - drop `public.instruments` (owner confirmed it is unused);
   - leave one read-all SELECT policy each on `app_versions` and `app_patches`
     (three duplicates exist on each today);
   - set `bookings.status` NOT NULL, guarded so it raises a clear notice and skips
     if any row has a null status.
   It must not touch any `bookings` policy.
5. Stale comment. `BookingErrorMapper.kt:10` names `bookings_no_overlap_confirmed`;
   the live constraint is `bookings_court_id_tstzrange_excl`.

## Phase 2: delete dead code
- The `court/...` route (`AppNavigation.kt:212-239`), `CourtScreen.kt` and
  `CourtViewModel.kt`. Nothing navigates to that route.
- `BookingRepository`: `getBookingsForCourtFromCache`, `getBookingsForCourt`,
  `subscribeToBookingChanges`.
- `BookingViewModel`: `cancelLastBooking`, `resetLastBookingStatus`,
  `getEndTimeForSlot`, and `lastCreatedBookingId` if nothing reads it afterwards.
- `AuthRepository.getDisplayName` (after Phase 1 step 2), the four legacy aliases at
  `Color.kt:28-31`, `KayanIcons.Close` and `KayanIcons.Calendar`.
- `MyApplication.kt`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
- `install(Storage)` in `SupabaseModule.kt`, plus the `supabase-storage` and the three
  `material3.adaptive` entries in `app/build.gradle.kts` and `libs.versions.toml`.
- Keep `multiplatform-settings`; supabase-kt may need it for session storage.

## Phase 3: one tested home for slot and "upcoming" logic
- Create `util/SlotGrid.kt` with pure functions: slot generation (from
  `BookingViewModel.kt:169-191`) and the booked / mine / past mapping (`:126-141`).
  Take `now` as a parameter; do not read the clock inside.
- Create one pure `isUpcoming(status, end, now)` and use it in
  `HomeScreen.kt:124-129` and `MyBookingsScreen.kt:213`.
- Add `SlotGridTest.kt` covering: a normal day; a duration that does not divide the
  opening hours; close before open (record current behaviour); a booked slot, a past
  slot, and a slot that is "mine".

## Phase 4: stop bypassing the repository
- Turn `BookingRepository` into one shared instance (a `val bookingRepository` beside
  the client in `SupabaseModule`). Move everything in its `companion object` onto the
  instance.
- Add `currentUserId()`, `sessionStatus` and `signOut()` to `AuthRepository`, also as
  one shared instance.
- Remove every `SupabaseModule.client` and `BookingRepository.<static>` use from the
  ViewModels and `AppNavigation.kt`. ViewModels use their constructor repository;
  navigation calls the two repositories.
- Replace `println` and `printStackTrace` in `BookingRepository.kt` with `Log`, as the
  updater code does.
- Add `private set` to every public `var ... by mutableStateOf` in the ViewModels,
  except `AuthViewModel.username` and `password`, which the form writes.

## Phase 5: identify sports by id, gather constants
- Route by sport id, not name: `booking/{sportId}` in `AppNavigation.kt`, with
  `filterBySport` and `switchSport` in `BookingViewModel` keyed on id. Names remain
  for display and for `sportImageRes`.
- `ClubService.Tennis` (`HomeScreen.kt:101`): look the sport up by name once, in
  navigation, and do nothing if it is absent.
- Move to `AppConstants`: the status strings `"confirmed"` and `"cancelled"`, the
  limit of 2 active bookings, the 7-day booking window, the login domain
  `myapp.local`. Replace the literals (`BookingRepository.kt`, `BookingViewModel.kt`,
  `AuthRepository.kt:13`).
- Add a one-line comment in `AppConstants.kt` and `admin-web/src/lib/time.ts`, each
  pointing at the other side's copy of the time zone.

## Phase 6: admin web tidy-up
- Move `groupBySport` from `BookDialog.tsx:84` to `lib/schedule.ts`; update the import
  in `BlockDialog.tsx:6`.
- Move the three direct Supabase calls (`Login.tsx:19`, `SchedulePage.tsx:68,128`)
  into functions in `lib/api.ts`.
- Remove the `eslint-disable` comment at `Timeline.tsx:41`; ESLint is not installed.

## Phase 7: repo hygiene
- Add a short top-level `README.md`: what the two apps are, how to build and test
  each, the order to run the `supabase/` files, and a pointer to `audit/README.md`.
- Untrack `.artifacts/` and `app/.../ui/navigation/implementation_plan.artifact.md`
  with `git rm --cached` and add them to `.gitignore`. Leave `.idea/` alone.

## Deliberately out of scope
- Update hashes (owner decision, see above).
- Reading the Supabase URL and key from `local.properties`.
- Renaming `applicationId`: Android would treat it as a different app and the
  self-updater could not upgrade existing tablets.
- A web router, CI, and a separate ViewModel for the booking success screen.

## Final report
- For each phase: what changed and the test output.
- Anything skipped, with the reason.
- The two things the owner must now do in the Supabase SQL Editor, in this order:
  1. run the extended `00_inspect_backend.sql` and share the output;
  2. review and run `05_cleanup.sql`, only if step 1 shows nothing unexpected about
     the `bookings` policies.
- A reminder that Phases 2 to 5 need a tablet smoke test: sign in, book, cancel from
  the success screen, cancel from My Bookings, switch sport, open the update dialog.
