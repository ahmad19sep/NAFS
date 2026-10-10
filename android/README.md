# NAFS Native Android

This directory is the pure Kotlin/Jetpack Compose rebuild. The former Capacitor shell is preserved at `../android-capacitor-legacy/`.

## Current milestone

The native rebuild now includes product workflows and the first server API integration:

- Kotlin and Jetpack Compose with Material 3
- API 36 target and API 37 compile SDK
- Navigation Compose
- Hilt dependency injection
- Room cache/outbox foundation
- DataStore-backed system/light/dark preference
- account-day Deen prayer logging with missed/alone/jamaat status, extra activities, and a saved faith-mode preference
- encrypted prayer cache with response-loss replay handling and cross-device conflict protection
- morning priorities and evening review with desired task completion, account-day identity, encrypted Room v9 cache, and optimistic write protection
- saved server AI verdicts remain readable and are retained by manual check-in writes
- saved evening notes can request a server-generated AI review with a stable retry intent and cross-device version protection
- HTTPS API client with verified account identity, proactive token refresh, one 401 refresh retry, timeouts, and sanitized errors
- native Coach chat from Home, with recent saved conversations, account-scoped history, and explicit send/retry/reload states
- encrypted Room v10 conversation history and durable pending requests that recover across process restarts
- Coach report archive with server-side type filters, stable paging, and native Markdown detail screens
- encrypted Room v11 cache for recent report lists and the last 20 opened report bodies, cleared on sign-out/account changes
- native future-self letter archive, new-letter editor, saved replies, and account-calendar revisit dates
- encrypted Room v12 drafts, recent letters, and exact pending create intents with stable UUID recovery
- due-letter coach replies with encrypted stable requests, guarded server saves, restart/timeout recovery, and partial-context disclosure
- recovery-plan previews from a problem or saved Coach chat, with explicit selection before adding tasks, habits, or challenges
- encrypted Room v13 plan drafts, generation retries, stable step IDs, and per-item dates/progress for partial-save recovery
- bearer-authenticated coach replies saved under the exact loaded conversation version, with duplicate-generation claims and partial-context disclosure
- WorkManager dependency ready for real synchronization work
- pure Kotlin rules and tests for account dates, habit/challenge streaks, task periods, and goal progress
- Supabase Kotlin Auth and PostgREST clients
- explicit initializing, signed-out, authenticated, refresh-failure, and missing-configuration states
- email/password sign-in, sign-up metadata, password-reset email, and sign-out
- AES-GCM session encryption with an app-specific Android Keystore key
- authenticated `users` profile resolution with an isolated Room cache and a versioned migration
- two-step onboarding with deterministic, retry-safe starter habit IDs
- authenticated live dashboard backed by real task, habit, goal, and profile repositories
- RLS-backed daily, weekly, and monthly task lists using the account timezone
- task creation with a retry-safe stable UUID, due times, and priority validation
- task editing for title, note, priority, and daily due time
- desired-state complete/reopen plus soft delete and Snackbar Undo restore
- best-effort local WorkManager reminders 1 hour and 30 minutes before daily due times
- Android 13+ notification permission handling and a dedicated task reminder channel
- a user-isolated Room task cache with explicit offline, loading, empty, and error states
- native habit creation/editing for simple, counter, duration, and subject types with retry-safe UUID intents
- daily, selected-day, and per-week schedule controls plus pause/resume management
- scheduled, rest-day, and paused habit sections with typed progress logging
- live daily, weekday, and per-week streak calculation from 400 days of logs
- atomic subject progress through the `log_subject_habit` database RPC
- Room schema v4 habit/log cache with user-isolated offline reads
- native weekly through long-term goals with categories, deadlines, numeric targets, and milestones
- milestone-derived true progress, retry-safe goal creation, confirmed deletion, and Room schema v5 caching
- native daily, weekly, monthly, and yearly challenges with definition editing and active/completed filters
- completed/missed period check-ins, cadence-aware streaks, retry-safe creation, confirmed deletion, and Room schema v6 caching
- system Photo Picker proof uploads with no gallery permission, plus optional sadqa pledges and paid-state tracking
- native Health baseline/BMI plus daily hydration, steps, multiple timed sleep periods, meals, exercise, weight, mood, notes, and configurable metrics
- Room schema v7 Health fallback with body metrics and notes encrypted by a dedicated Android Keystore key
- production-style dashboard with account-day metrics, next-up task, active goals, and partial/offline states
- five-destination navigation, branded light/dark design system, and modern cards
- notification management in Profile plus task-notification deep links
- stale/expired reminder suppression and app-start reminder reconciliation
- account-midnight, resume, date, clock, and timezone refresh handling
- one-tap account timezone alignment with the device timezone, without location permission

The native screens are connected to real repositories and verified server routes. Live account mutations are intentionally skipped at the user's request, so production RLS, destructive account deletion, cross-device conflicts and authenticated AI generation remain acceptance work. The newer backend and additive migrations must be deployed before their native workflows can succeed against the production origin.

Additional native workflows now include the global Quick Add sheet and feature library; dated habit notes; owned goal/habit links; challenge restart with retained attempt history; dream, rich daily log, Quran, screen-time and bug/idea records; record photo previews; 30-day history; canonical weekly/monthly reports; native PDF export/share/print; profile name/avatar/timezone editing; badges/levels; manual-city prayer times; and per-device task/habit/letter reminders. Coach advice offers validated task, habit and challenge suggestions that the user explicitly chooses to create. Health recommendations retain the existing baseline prerequisite.

Room v14 encrypts generic records, drafts, exact AI retry requests and confirmed sync receipts. Guarded record saves are persisted before a network request and retried through account-scoped WorkManager jobs. Other feature writes retain their established explicit retry paths. Uploads require connectivity; a failed record save retains the uploaded URL in its draft. Sign-out/account changes cancel user work and clear per-user caches. Reminders use best-effort WorkManager delivery and do not request exact-alarm, continuous-location or UsageStats permissions.

## Requirements

- Android Studio compatible with Android Gradle Plugin 9.4
- JDK 17
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0 or newer

Create `local.properties` if Android Studio does not create it automatically:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
```

Provide the public Supabase connection values in your user-level Gradle properties file (`%USERPROFILE%\.gradle\gradle.properties`):

```properties
NAFS_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
NAFS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_KEY
```

For compatibility with the existing Expo setup, the build also accepts the environment variables `EXPO_PUBLIC_SUPABASE_URL` and `EXPO_PUBLIC_SUPABASE_ANON_KEY`. These are public client values embedded in the APK. Never use the service-role key or any AI/provider secret here.

The server API origin defaults to `https://nafs-one.vercel.app`. Override it with the public Gradle property `NAFS_API_BASE_URL` or environment variable `EXPO_PUBLIC_API_BASE_URL`. Use an HTTPS origin with no path, credentials, query, or fragment. API redirects are disabled to keep bearer tokens on the configured origin.

AI evening reviews require the updated `web-legacy` backend to be deployed to that origin. The endpoint accepts verified bearer sessions as well as existing browser cookies, requires a saved native check-in, and persists its review before returning success. Server provider keys stay in the backend environment. See [the native API contract](../web-legacy/NATIVE_API.md) for deployment dependencies and retry behavior.

Native Coach additionally requires `supabase/coach_conversation_versions.sql` to be applied before deploying the updated backend. It retains existing conversations and creation dates while adding strictly advancing server versions. Chat generation requires an online connection; recent history and exact failed-send intents are encrypted locally. New conversations retain a composed draft, and switching existing conversations is disabled while a draft or unresolved send is present. Recovery plans now use the explicit preview/apply workflow described below.

Future-self replies require `supabase/future_self_replies.sql` before deploying the updated future-self endpoint. The native detail screen can request a reply once the account-calendar revisit date arrives, retains an encrypted stable retry intent, and reloads authoritative saved replies after a timeout or restart. The server prevents duplicate generation across devices and compares all original letter fields before saving. Recent letters and drafts remain usable without this reply migration. Opt-in Android reminders now notify when a letter is ready to revisit; replies remain explicit server requests. Authenticated reply acceptance remains pending.

Open **Coach → Reports** to browse saved daily verdicts, weekly tribunals, gap analyses, letter replies, and growth reviews. The All filter also includes future report types. Lists query only metadata through authenticated, user-scoped PostgREST, with 20-item pages ordered by `generated_at` and `id`; report bodies load when opened. Dates use the account timezone and weekly start dates remain calendar dates. Each filter keeps its most recent page offline, and the last 20 opened bodies use a dedicated Android Keystore key. Offline lists cannot load older pages, and unopened details require a connection. The report archive reads the existing `ai_reports` table independently of chat's conversation-version migration and makes no AI generation call.

## Build and test

Open **Coach → Future-self letters** to write a new letter or read your saved words and replies. Drafts save locally after a short debounce and are flushed when closing the editor. Offline writing is supported; saving to your account requires a connection. A stable letter UUID and encrypted pending intent recover a committed save after a lost response without inserting another letter. Failed saves remain locked until retried or checked online, and resuming an unsaved draft keeps its original UUID. The editor writes new letters; saved letters retain their original content. Server-owned written times, delivery stamps, and AI replies are preserved. Due letters can request a reply through the deployed server route; opt-in local reminders can notify when letters are ready to revisit.

Open **Coach → Recovery plan** to describe a setback, or use **Build a plan from this saved chat** from a saved conversation. Coach returns a preview with reasons, schedules, targets, and photo proof before you select and add steps. Unsent Coach messages remain in their composer. Each step is created through the existing task, habit, or challenge repository only after tapping **Add selected steps**. Encrypted pending requests and deterministic step UUIDs survive a restart; retries keep the original first-add date and skip confirmed steps. Reload adopts matching saved items after an uncertain response and leaves changed items for inspection. Local storage must succeed before any request or item write. Drafts and previews stay available offline, and clearing a preview keeps already-added items.

Native planning requires deploying the updated `/api/ai/plan` and `/api/ai/plan-from-chat` routes. Saved-chat planning also requires the existing conversation-version SQL migration; problem planning adds no SQL migration. The server derives ownership from verified Auth, checks full saved chat versions before/after generation and on replay, and claims native requests before contacting AI. See `../web-legacy/NATIVE_API.md` for contracts and deployment prerequisites. Account-free Keystore and device tests passed; authenticated generation and RLS acceptance remain pending.

From this directory on Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug application ID is `com.ahmad.nafs.debug`; release remains `com.ahmad.nafs`.

Do not add signing keys, service-role keys, AI provider keys, or other server credentials to this project.

## Gemini backend

All AI requests use Google Gemini through the configured Next.js backend. Production now has `GEMINI_API_KEY` and `AI_PROVIDER=gemini`, and the activation deployment is Ready; see [AI setup](../docs/AI.md). The Android app holds no Gemini secret. Recovery validation accepts Gemini responses and encrypted saved previews, with a regression covering both paths. The updated debug build passes 272 unit tests across 42 classes. Live synthetic Coach chat and recovery-plan checks pass with Gemini 3.5 Flash-Lite; authenticated save/replay acceptance remains pending; existing unsigned release artifacts need rebuilding before publishing this change.

## Native records and deployment

Apply `../supabase/native_records.sql` only after reviewing the deployed schema and its `dreams.sql`, `schema_additions.sql` and `app_notes.sql` prerequisites. It supplies the missing `quran_log` table and the ownership-scoped `native_save_record` RPC. Saved rows are compared against the full loaded row; a peer edit retains the draft and requires reloading. Exact retries adopt a matching committed row.

Challenge restart additionally needs `../supabase/native_challenge_restart.sql`. It archives overlapping old-attempt check-ins before opening a new attempt; best streaks and older history are retained. Photo uploads need `../supabase/native_storage_policies.sql`, which adds the missing daily-log photo bucket and ownership guards compatible with existing web avatars. Stored advice and report review require the updated backend. Deployment order and route contracts are in [NATIVE_API.md](../web-legacy/NATIVE_API.md). The reviewed six-migration transaction was applied to production and the backend cloud deployment completed on 2026-10-10. The verified project targets, acceptance evidence and rollback target are documented in [the production deployment plan](../output/deployment/PLAN.md).

Prayer times use an explicit city/country request to the public Aladhan API with a calculation method and school. The response must match the requested date and account timezone. The encrypted cache is valid only for the same date/settings. Location permission is unnecessary. Uploaded avatars, proofs, daily-log photos and screenshots use the existing public buckets; the picker screens disclose that visibility.

## Release signing

The release package remains `com.ahmad.nafs`. Without signing configuration, release APK/AAB outputs are unsigned. To use the existing production keystore, set `NAFS_SIGNING_PROPERTIES_FILE` as a Gradle property or environment variable to an external private properties file containing `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Use an absolute keystore path, escape Windows separators in Java properties, and keep both files outside the repository. An incomplete configuration fails with property names only. No replacement production signing key is generated.

Run `./gradlew.bat assembleRelease bundleRelease` for release validation. Reuse the existing Play signing identity and supply the correct next `versionCode` before publishing. Debug install/test commands are `adb install -r app/build/outputs/apk/debug/app-debug.apk`, `adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` and `adb shell am instrument -w com.ahmad.nafs.debug.test/androidx.test.runner.AndroidJUnitRunner`. On phones that restrict USB installs, approve both app and test-app prompts.

The account-free device suite checks large-font navigation, dark-theme faith filtering, Quick Add, PDF rendering, selected-page printing, real Keystore encryption, account-switch isolation, corrupt-draft retention and Android timestamp/version compatibility. All 12 tests passed on the connected Android 16 phone. All 271 unit tests, debug/test assembly, lint and unsigned R8 release APK/AAB assembly also passed after the timestamp fix. Device tests use synthetic content and do not mutate account records. See [the parity checklist](../ANDROID_FEATURE_PARITY.md) for the latest validation evidence and remaining external acceptance.

The initial read-only live-account check found HTTP 404 for the production `/api/progress`, `/api/recognition` and `/api/ai/native` routes. After deployment on 2026-10-10, ten production HTTP checks passed, including those routes rejecting unauthenticated requests with HTTP 401. The corrected debug APK passed read-only phone checks for saved Coach history/reload, weekly/monthly reports, the history chart, badges and the empty report archive. The Coach loading failure was traced to Android timestamp validation; database timestamps now use the shared `ServerTimestamp` parser for numeric timezone offsets while retaining their original strings for exact version checks. The temporary phone stay-awake setting was restored. Authenticated AI generation and account-write acceptance remain separate work.
