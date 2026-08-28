# Production APK Backend Audit — Master Plan

**Symptom:** Debug build (Android Studio → USB device) is clean. Signed release APK installed independently on multiple physical phones produces bugs.

**Root-cause space:** This symptom signature is almost never "random device weirdness." It's a small, well-known set of debug/release asymmetries plus, if the failures correlate with *simultaneous* usage across devices, server-side concurrency bugs. Work top-down — items are ordered by strike rate for this exact symptom.

---

## 0. Triage — Classify the Failure Before Auditing

Before touching code, determine which bucket you're in. This changes where you start.

| Signal | Bucket |
|---|---|
| Each phone fails the same way, independently, even used alone | **Client build issue** (Sections 1–4, 6) |
| Failures only appear/worsen when devices are used concurrently (e.g., two people booking the same slot window) | **Backend concurrency issue** (Section 5) |
| Crashes immediately on launch or on first network call | **Section 1.1 (R8) or Section 3 (API keys/certs)** — highest hit rate |
| Silent wrong data / stale state, no crash | **Section 4 (persistence) or Section 5 (race conditions)** |

Action: pull `adb logcat` from at least two affected devices running the *release* build (not debug). Filter by your package name and grep for `FATAL EXCEPTION`, `AndroidRuntime`, and your app's tag. Do this before anything else — guessing wastes more time than reading the actual stack trace.

```
adb logcat --pid=$(adb shell pidof -s com.yourpackage.name)
```

---

## 1. Build Configuration Layer (start here — highest probability)

### 1.1 R8/ProGuard minification
This is the single most common cause of "works in debug, breaks in release." Debug builds skip minification/obfuscation by default; release builds run R8, which renames and strips classes/methods it thinks are unused — including ones only referenced via reflection.

Check `app/build.gradle`:
```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

Libraries that break silently without explicit keep rules:
- **Gson/Moshi** — model field names get obfuscated, JSON deserialization returns nulls. Add `-keep class com.yourpackage.model.** { *; }` for every DTO/model package.
- **Retrofit** — keep rules needed for interfaces + generic signatures (`-keepattributes Signature,*Annotation*`).
- **Room** — entities and DAOs need keep rules if you use raw queries or reflection-based type converters.
- **Hilt/Dagger** — usually handled by the plugin, but custom modules with reflection can break.
- **Firebase / payment SDK** — most ship their own consumer ProGuard rules, but verify with the SDK's official rules page; version mismatches cause silent stripping.

Verification: build the release APK, unzip it, and inspect `classes.dex` with a tool like `jadx` to confirm your model classes still have expected field names.

### 1.2 BuildConfig / product flavor fields
If `API_BASE_URL`, `API_KEY`, or payment gateway keys are set via `buildConfigField` per build type, confirm the **release** values are correct — a common bug is release silently pointing at a staging/dev backend, or missing a key entirely because it's injected via a local `gradle.properties`/CI secret that isn't present on your machine's release build.

```gradle
buildTypes {
    release {
        buildConfigField "String", "API_BASE_URL", "\"https://api.yourdomain.com\""
    }
    debug {
        buildConfigField "String", "API_BASE_URL", "\"http://10.0.2.2:8080\""
    }
}
```

### 1.3 Signing config & fingerprint-bound services
Anything registered against a SHA-1/SHA-256 fingerprint (Firebase Auth, Google Sign-In, Google Maps API key, App Check, payment gateway merchant config) is bound to the **signing certificate**. Your debug builds sign with the auto-generated debug keystore; release APKs sign with your release keystore. If the release keystore's fingerprint isn't registered wherever these services check it, those features fail only in release.

```bash
keytool -list -v -keystore your-release-key.jks -alias your-alias
```
Cross-check that SHA-1 against Firebase Console → Project Settings → Your App, and against any API key restrictions in Google Cloud Console.

### 1.4 `debuggable` flag side effects
Confirm no logic branches on `BuildConfig.DEBUG` in a way that disables a real code path in release (e.g., a mock payment flow that only runs in debug, or a `StrictMode` policy that masks a main-thread network call in debug but crashes in release).

---

## 2. Network Layer

### 2.1 Cleartext traffic blocking
Since API 28, HTTP (non-TLS) traffic is blocked by default. Debug builds often get a permissive `usesCleartextTraffic="true"` override via a debug-only manifest, masking the issue. If your production API is HTTPS this is moot — but if any endpoint (image CDN, webhook callback, local network device) is HTTP, it will silently fail in release.

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.yourdomain.com</domain>
    </domain-config>
</network-security-config>
```

### 2.2 Hardcoded local/emulator endpoints
Search the codebase for `10.0.2.2`, `localhost`, `192.168.`, or any hardcoded LAN IP. These work when tethered via USB/same network as your dev machine and fail on any phone not on that network — this alone reproduces "works on my phone, fails everywhere else."

### 2.3 Certificate pinning
If you pin certs, confirm the pin matches your **production** cert chain, not a staging cert or your local reverse-proxy's self-signed cert used during development.

### 2.4 Timeout/retry tuning
Kiosk tablets on villa WiFi will see different latency/packet loss than your dev machine's connection. Confirm connect/read timeouts are generous enough (not left at defaults meant for local testing) and that you have retry-with-backoff on transient failures, not just a hard fail.

---

## 3. Auth & API Key Restrictions

- **API key restrictions**: Google Cloud/Firebase API keys are often restricted by `package name + SHA-1`. The debug cert's SHA-1 is registered (maybe from early testing); the release cert's isn't. Result: every call using that key returns 403 only in release.
- **OAuth/Sign-In**: same fingerprint dependency as 1.3.
- **Token refresh races**: if multiple app components independently trigger a token refresh on 401, concurrent refresh calls can invalidate each other's token — more likely to surface under real multi-device load than single-device debug testing. Centralize refresh behind a mutex/single-flight pattern.

---

## 4. Persistence Layer

### 4.1 Room database migrations
Every phone gets a **fresh install** of the release APK — if your schema has changed since the last version you tested and you don't have a defined `Migration` path (or `fallbackToDestructiveMigration()` is masking it in debug), fresh installs can behave differently than your dev device, which likely has an app already carrying old data through iterative debug installs. Check this asymmetry specifically: your debug device has *upgrade* history; the new phones have *fresh install* history — these are different code paths.

### 4.2 Android Keystore-backed encryption
If you encrypt local data (tokens, cached PII) using Android Keystore-derived keys, these keys are generated per-device on first run. Race conditions in first-run initialization (e.g., a background sync firing before the key is generated) will manifest inconsistently across devices and never on your dev device once it's past first-run.

### 4.3 SharedPreferences/DataStore defaults
Confirm default values assumed by business logic actually match what's set on a truly fresh install, not values that happen to already be set on your long-lived debug device.

---

## 5. Backend Concurrency & Booking-Specific Logic

This section matters most if failures correlate with **simultaneous** use — e.g., two villas' tablets both hitting the booking endpoint for overlapping slots. Single-device debug testing structurally cannot expose these bugs; they only appear once you have real concurrent load, which "installing on multiple phones" gives you for the first time.

### 5.1 Slot reservation race conditions
If slot availability is checked and then written as two separate operations (read-then-write) without a database-level constraint, two concurrent requests can both pass the availability check and both book the same slot.

Fix pattern: push the uniqueness constraint into the database itself, not application logic.
```sql
CREATE UNIQUE INDEX uq_slot_booking ON bookings (facility_id, slot_start_time)
WHERE status != 'cancelled';
```
Let the insert fail on constraint violation and handle that as "slot no longer available," rather than trusting an application-level availability check.

### 5.2 Idempotency on booking/payment requests
Mobile networks on real devices retry more aggressively than your dev environment (dropped packets, app backgrounding mid-request). Without an idempotency key on booking/payment creation endpoints, a retried request can create duplicate bookings or double-charge. Require the client to generate a UUID per booking attempt and have the backend dedupe on it.

### 5.3 Transaction isolation level
Confirm your booking-write transaction uses at minimum `READ COMMITTED` with the unique constraint above, or `SERIALIZABLE` if you need stronger guarantees and can tolerate retries on serialization failures. Don't rely on ORM-level optimistic locking alone unless every write path actually goes through it.

### 5.4 Payment gateway environment/cert mismatch
Separately from app signing (1.3): confirm the payment SDK is initialized with **production** merchant credentials in the release build, not sandbox/test keys left over from development — and that your release signing certificate (if the gateway does cert-pinning on their SDK) is registered with the payment provider, not just Firebase.

---

## 6. Device/OS Fragmentation

- **API level gating**: grep for any API-26+/29+/31+/34+ calls not wrapped in `Build.VERSION.SDK_INT >=` checks. Your dev phone is one API level; the other phones may not be.
- **OEM background restrictions**: Samsung, Xiaomi, Huawei, and others aggressively kill background services/WorkManager jobs unless the app is whitelisted. If any booking-sync or payment-confirmation logic runs in the background, it may silently never complete on some OEMs. For kiosk-mode tablets specifically, confirm battery optimization is disabled for the app (this should be part of your kiosk provisioning, not left to end-user settings).
- **Foreground service requirements**: API 34+ requires declared foreground service types; missing this throws at runtime only on newer OS versions you may not have tested against.

---

## 7. Observability for Release Builds

You can't `adb logcat` a tablet mounted in a villa. Before shipping the next round of fixes, add:
- **Crashlytics or Sentry**, enabled specifically for release build type, so fatal and non-fatal errors from field devices reach you.
- **Structured breadcrumb logging** around the booking → payment → confirmation flow specifically, since that's your highest-value and highest-risk path.
- **Remote config kill-switch** for the payment flow, so a bad release doesn't block bookings entirely while you patch.

---

## 8. Reproduction & Verification Protocol

1. **Debuggable-release hybrid build**: create a build variant with `minifyEnabled true` but `debuggable true`, so you get real R8 behavior while still being able to attach `adb logcat`. This isolates "is it R8" fastest.
2. **Multi-device concurrent test**: install the *actual signed release APK* (not the hybrid) on 2+ physical devices, and deliberately trigger overlapping booking attempts at the same time to force the race conditions in Section 5.
3. **Fresh-install matrix**: always test fresh installs, not upgrades from your dev device — this is the only way to catch the Section 4 migration/first-run issues.
4. **Regression checklist before every release**: fresh install → booking → payment → confirmation, run on at least one non-primary OEM device, with Crashlytics/Sentry confirmed reporting.

---

## Suggested Order of Attack

1. Pull release-build logcat from a failing device (Section 0)
2. Check R8 keep rules against your model/DTO/DAO classes (1.1)
3. Verify release keystore SHA-1 is registered everywhere it needs to be (1.3, 3)
4. Grep for hardcoded local/emulator endpoints (2.2)
5. If failures correlate with concurrent use, jump straight to Section 5
6. Add Crashlytics/Sentry regardless, before doing anything else in production
