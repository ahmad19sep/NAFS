# NAFS Native Android Migration Plan

Last audited: 2026-10-10  
Repository branch: `native-andriod`  
Milestone: 3/4 - native workflows implemented; release and live acceptance pending

## 1. Objective

Build a production-oriented Android application in Kotlin and Jetpack Compose without wrapping the website or reusing React Native, Expo, Capacitor, Cordova, Flutter, or embedded HTML as the application UI.

The existing repository is the source of truth for product behavior. The migration must preserve the existing web application, server routes, Supabase data, user accounts, and production data. A feature is not complete on Android until the real data path and important states have been implemented and validated.

## 2. Executive findings

The repository currently contains three overlapping client implementations:

1. The repository root is an Expo SDK 52 / React Native application using Expo Router and NativeWind. It contains 14 protected mobile screens plus authentication and onboarding.
2. `web-legacy/` is a Next.js 14 application. Despite its name, it contains the broadest feature set and all server-side API routes. It remains required for AI, scheduled jobs, web push, secure account deletion, and other operations that require server secrets.
3. `android/` is an existing Capacitor WebView shell with Groovy Gradle files and application ID `com.ahmad.nafs`. It is not a Kotlin/Compose project.

The requested final directory is also `android/`, so the current shell creates a migration collision. It must not be overwritten silently. The recommended reversible strategy is:

- preserve the current shell as `android-capacitor-legacy/` in Git history or in a dedicated archival commit;
- create the native project at `android/` only after that move/replacement is explicitly approved;
- retain the root Expo and `web-legacy/` trees during the migration;
- decide the final application ID before a release build. The brief suggests `com.example.nafs`, the Expo configuration uses `com.nafs.app`, and the Capacitor shell uses `com.ahmad.nafs`.

This opening inventory records the initial audit. The Capacitor shell is now preserved at `android-capacitor-legacy/`, and `android/` contains the native Kotlin/Compose implementation. See `ANDROID_FEATURE_PARITY.md` for current status and validation.

## 3. Source-of-truth order

When repository sources disagree, use this order and record the decision:

1. Database constraints, RLS policies, migrations, and deployed backend contract.
2. Tested domain behavior in `web-legacy/src/lib/` and authenticated API routes in `web-legacy/src/app/api/`.
3. Current user-visible behavior in `web-legacy/src/app/` and the root Expo screens.
4. README/setup prose, which currently contains conflicting architecture descriptions.

Important conflicts already found:

- `README.md` describes Expo as the mobile application, while `MOBILE.md` describes the Android/iOS applications as live-site Capacitor shells.
- Root `types/database.ts` still models habit types as `boolean | count | duration`, while later SQL and the web domain model use `simple | counter | duration | subject` plus schedules and pause state.
- Root mobile models omit later challenge, goal, health, profile, badge, notification, and AI fields added by migrations.
- `quran_log` is queried by both clients and appears in generated web types, but no repository SQL migration creates it.
- Root mobile account deletion removes `public.users` and signs out; the web app uses `/api/user/delete` with the server service role to delete the authentication account safely.
- The web APIs contain idempotency, atomic subject progress, soft delete/restore, and optimistic write-conflict protections that the root Expo screens often bypass with direct writes.

## 4. Current architecture

### 4.1 Root Expo client

Technology:

- Expo SDK 52, React Native 0.76, React 18
- Expo Router 4 and file-based routing
- NativeWind/Tailwind styling
- Supabase JavaScript client with `AsyncStorage` session persistence
- Direct Supabase reads/writes for most features
- Bearer-authenticated calls to a Vercel base URL for selected AI operations
- Expo Print/FileSystem/Sharing for reports

Entry and session flow:

- `app/_layout.tsx` initializes the Supabase session, loads `public.users`, and routes to `/auth`, `/onboarding`, or `/(app)/dashboard`.
- `app/auth.tsx` provides email/password sign-in, sign-up, and reset email. Sign-up also upserts a public profile.
- `app/onboarding.tsx` creates selected starter habits and marks onboarding complete.
- `app/(app)/_layout.tsx` provides Home, History, Add, Coach, and Profile tabs plus a quick-action sheet.

Configuration:

- `lib/supabase.ts` uses `EXPO_PUBLIC_SUPABASE_URL` and `EXPO_PUBLIC_SUPABASE_ANON_KEY`.
- `lib/api.ts` uses `EXPO_PUBLIC_API_BASE_URL` and forwards the Supabase access token.
- `app.json` declares package `com.nafs.app`, dark-only UI, camera/photo/notification permissions, and placeholder EAS project configuration.

### 4.2 Next.js web client and backend

`web-legacy/` contains:

- Next.js App Router screens and client components;
- Supabase browser/server/service-role clients;
- authenticated API routes for tasks, habits, challenges, goals, AI, notifications, screen-time analysis, coach notes, and account deletion;
- cron routes for task notifications, daily reports, and tribunal reports;
- Cloudflare Workers AI behind `src/lib/ai.ts`;
- optional Anthropic deep analysis for report review;
- Vitest domain and contract tests.

Server-only secrets include `SUPABASE_SERVICE_ROLE_KEY`, `CLOUDFLARE_APP_KEY`, `ANTHROPIC_API_KEY`, VAPID private keys, `CRON_SECRET`, and optional WhatsApp credentials. These must never enter the Android application.

### 4.3 Supabase

Supabase supplies:

- email/password authentication;
- Postgres tables and RPC functions;
- row-level security based on `auth.uid()`;
- public storage buckets for avatars, challenge evidence, log photos, and screen-time screenshots;
- the `log_subject_habit` atomic progress RPC;
- server-side service-role access for cron and account deletion.

The Android client should continue using Supabase Auth and RLS-protected user-scoped data. Operations requiring service credentials remain behind authenticated server endpoints.

### 4.4 Current Android shell

Evidence:

- `android/settings.gradle` includes Capacitor modules.
- `android/app/build.gradle` applies Capacitor scripts and has no Kotlin or Compose dependencies.
- `capacitor.config.ts` loads `https://nafs-one.vercel.app` in a WebView and names the product `Ascend`.
- `android/variables.gradle` currently sets min SDK 24 and target/compile SDK 36.
- The root dependency manifest does not include `@capacitor/cli` or `@capacitor/android`; after the locked root install, neither package exists in `node_modules`. The current shell is therefore not reproducibly buildable from the checked-in root manifest.

The target SDK is already aligned with the Google Play rule effective 31 August 2026: new apps and app updates must target Android 16 / API 36 or higher. Re-check before release against the official policy: <https://support.google.com/googleplay/android-developer/answer/11926878>.

## 5. Screen and workflow inventory

| Surface | Current behavior | Main dependencies | Native migration notes |
|---|---|---|---|
| Entry/session guard | Resolves session and onboarding state | Supabase Auth, `users` | Splash-safe auth state machine; avoid navigation races |
| Authentication | Sign in, sign up, reset email | Supabase Auth, `users` | Secure token persistence; browser callback only if OAuth is later retained |
| Onboarding | Starter habit packs and profile completion | `habits`, `users` | Make insertion and completion transactional/idempotent |
| Dashboard | Greeting, identity score, prayers, habits, tasks, challenges, goals, shortcuts | Most daily tables | Use repository/domain score rules, not the simplified Expo formula without review |
| Tasks | Daily/weekly/monthly lists, create, complete, delete, missed derivation | `tasks`, task API | Port soft delete/restore and idempotent desired-state writes from web API |
| Habits | Simple/counter/duration/subject habits, schedules, notes, pause, logs, streaks, starter packs | `habits`, `habit_logs`, RPC and APIs | Web implementation is more complete than Expo; use atomic logging and live streak calculation |
| Challenges | Cadence, durations, evidence photos, sadqa, check-ins, streak, restart/completion, AI starter | `challenges`, `challenge_checkins`, storage, APIs | Preserve daily/weekly/monthly/yearly semantics and evidence upload behavior |
| Goals | Typed goals, categories, milestones, progress, plans, linked habits/tasks, AI alignment/starter | `goals`, `goal_milestones`, APIs | Milestones can determine true progress; root Expo is only a subset |
| Health | Profile opt-in/setup, BMI, body metrics, exercise, multiple sleep sessions, hydration, mood, notes, meals/extras, AI recommendation | `users`, `health_logs`, APIs | Web screen is authoritative for full scope; medical copy needs careful disclaimers |
| Deen | Five prayer status values, jamaat scoring, extras, Quran log, prayer time support, feature toggle | `prayer_logs`, `quran_log`, profile, external prayer-time API | Faith mode can be disabled; missing Quran migration must be resolved first |
| Daily check-in | Morning tasks, evening narrative, completion review, AI verdict | `daily_checkins`, AI API | Preserve one row per user/date and conflict detection |
| Dream and mapping | Dream statement, why, target date, required/focused hours, trajectory | `dreams`, `daily_logs` | Web has focused-hours logging beyond Expo dream editor |
| Daily log | Rich daily log, photos, Quran pages, mapping/pull calculation, AI narrator | `daily_logs`, storage, `quran_log`, AI API | Web-only feature; required for full parity |
| Past logs | Browse and inspect previous daily logs | `daily_logs` | Web-only feature |
| History | 30-day tabs/charts for overall, habits, deen, tasks, challenges, health | multiple tables and pure history functions | Root Expo is a smaller summary; port full web calculations and denominator rules |
| Reports | Weekly/monthly ranges, score/KPI sections, print/PDF, comparison, AI report review | report domain, many tables, `report_reviews` | Native PDF/export should not rely on HTML/WebView UI; server can still return data/text |
| AI coach | Authenticated chat, saved conversation, reports, future-self and plan flows | `ai_conversations`, `ai_reports`, `future_self_letters`, AI APIs | Keep AI on server; add timeout/retry/error mapping and message limits |
| Screen time | Manual screenshot/upload and AI analysis | `screentime_logs`, storage, API | Web-only; Android UsageStats would require sensitive permission and is not implied by current product |
| Improve | Submit/manage bug and idea notes | `app_notes` | Web-only internal/product feature; clarify whether it ships publicly |
| Profile/settings | Name, avatar, timezone, health/deen toggles, notifications, reports/history links, badges, sign out, account deletion | `users`, storage, notification APIs, delete API | Use server deletion endpoint; support system theme and privacy controls |

## 6. Data inventory

| Table / resource | Purpose | Evidence / issues |
|---|---|---|
| `users` | Public profile and preferences | Many additive migrations; root model is incomplete |
| `habits` | Habit definition and schedule | Later migrations add subject progress, pause, schedule, notes, starter pack |
| `habit_logs` | One habit result per date | Unique `(user_id, habit_id, date)`; supports value/duration/notes |
| `tasks` | Daily/weekly/monthly tasks | Later migration adds reminders and soft deletion |
| `challenges` | Challenge definition/state | Later migrations add cadence/restart/starter pack |
| `challenge_checkins` | Per-period evidence/completion | Unique challenge/date; ownership is through challenge |
| `goals` | Goal definition/progress | Later migrations add type/category/status/start/alignment/starter fields |
| `goal_milestones` | Goal milestones | Ownership is through goal; later metadata added |
| `daily_checkins` | Morning/evening review | Unique user/date |
| `daily_logs` | Mapping and focused-hour log | Unique user/date; schema is narrower than web usage and needs verification |
| `dreams` | One dream per user | Unique user; later client fields require schema verification |
| `prayer_logs` | Prayer and extra-prayer state | Converted from booleans to 0/1/2 integers by migration |
| `quran_log` | Quran pages/surah/reflection | Referenced by code/types but creation migration is missing |
| `health_logs` | Daily health record | Extended by metrics, sleep session, meal, and profile migrations |
| `screentime_logs` | Daily screen-time capture/analysis | Defined in `schema_additions.sql` |
| `ai_conversations` | Persisted coach chat | JSON messages; needs client-side size policy |
| `ai_reports` | Tribunal/pull/gap/letter/growth outputs | Type constraint extended by migration |
| `future_self_letters` | Scheduled letters and AI replies | Base schema |
| `report_reviews` | Stored AI review per period/start | Unique `(user_id, period, start_date)` |
| `coach_notes` | Context notes for coaching | User/date indexed and RLS protected |
| `app_notes` | Bug/idea capture | User-scoped RLS |
| `mutations` | Request idempotency ledger | Required by protected create operations |
| Supabase Storage | Avatar, challenge/log/screen evidence | Bucket creation and policies are split across docs/migrations |

Before coding DTOs, generate a fresh schema from the deployed Supabase project and reconcile it with every SQL file. Repository migrations are additive and not a single authoritative ordered migration set.

## 7. Business rules that must be ported

### 7.1 Dates and time zones

- User-facing day identity must use the saved IANA timezone, not device/server default time.
- Pure `YYYY-MM-DD` calendar arithmetic must not round-trip through a local `Date` at midnight.
- Daily records are unique by user/date (or parent/date).
- Today remains pending until its window closes; missing historical scheduled records must not automatically be described as completed or failed.
- Test month/year boundaries, leap years, DST transitions, timezone changes, and travel.

The root Expo app currently uses device-local `todayString()` in most screens even though it stores a profile timezone. The tested web helper `todayInTZ` and pure calendar shifting are the safer specification.

### 7.2 Habits and streaks

- Scheduled rest days do not break a streak.
- Today with no log is pending and does not immediately erase yesterday's streak.
- Counter/duration completion requires meeting the stored target.
- Subject progress writes an absolute daily amount; the running position changes only by the difference from the previous value for that day.
- Subject progress must use the `log_subject_habit` database RPC to prevent concurrent double counting.
- Stored streak columns can be stale; user-visible streaks should be derived from logs where practical.

Evidence: `web-legacy/src/lib/streak.ts`, `habit-progress.ts`, related tests, and `supabase/atomic_habit_progress.sql`.

### 7.3 Tasks

- A missed task is derived: active daily task with a period date before today.
- Completion is a desired state, not a blind toggle, so retries cannot undo the action.
- Web behavior includes soft delete and a short restore window; root Expo permanently deletes.
- Creation should use request IDs/idempotency where supported.

### 7.4 Challenges

- Cadence can be daily, weekly, monthly, or yearly.
- Check-in validity, units elapsed, streak, auto-restart, and completion are cadence-aware.
- Evidence photo and sadqa flows must reflect the existing challenge configuration.

Evidence: `web-legacy/src/lib/challenges.ts` and `ChallengesClient.tsx`.

### 7.5 Goals

- Goal types have default horizons; goals have categories, status, milestones, optional linked habits, and AI metadata.
- Where milestones exist, their completion ratio is the true progress source.
- AI plan/starter/alignment responses remain server-generated and schema validated.

### 7.6 Scoring, history, and reports

- Prayer status is 0 missed, 1 alone, 2 jamaat.
- Weighted habits and prayer maximums feed scores in the tested web domain.
- Report calculations distinguish scheduled opportunity, pending, completed, partial, missed, unknown, and excused.
- A percentage must retain its denominator and coverage; no data must not become zero performance silently.
- The root dashboard's simplified 40/40/20 identity formula is not the only scoring implementation and requires a product decision before being treated as canonical.

## 8. Backend and authentication plan

### Authentication

- Use Supabase Auth with email/password first because it is the confirmed cross-platform flow.
- Persist refresh/access tokens using an encrypted Android-compatible session store; do not log tokens.
- Refresh sessions automatically and expose explicit authenticated, unauthenticated, refreshing, and failure states.
- If Google OAuth is retained, use an external/custom tab and deep-link callback, never an embedded WebView.
- Account deletion must call the authenticated server endpoint so both `auth.users` and cascaded public data are deleted.

### Data access

- Use repositories that separate remote, local, and synchronization sources.
- Direct Supabase requests are acceptable only with the public anon key and RLS.
- Use existing authenticated server APIs for secret-backed AI, account deletion, notification orchestration, idempotent protected mutations, and server-side workflows.
- Do not invent endpoints. Add a server endpoint only when the existing direct database operation cannot be implemented safely in Android, and document it first.

### Network contract

- Forward the Supabase bearer token only over HTTPS to the configured NAFS API origin.
- Add typed error mapping for unauthorized, validation, conflict, rate limit, unavailable, and malformed response states.
- Apply bounded timeouts and retry only safe/idempotent operations.
- Preserve AI schema validation on the server; Android consumes validated results.

## 9. Proposed native architecture

Use a Gradle version catalog and Kotlin DSL. Pin stable compatible versions at implementation time.

Suggested package/module boundaries:

```text
android/
  app/                    application, activity, root navigation, DI
  core/common/            result types, dispatchers, clock, date rules
  core/model/             domain models independent of transport/storage
  core/network/           Supabase/API clients, auth interceptor, DTOs
  core/database/          Room entities, DAOs, outbox, migrations
  core/datastore/         preferences and non-sensitive settings
  core/designsystem/      Material 3 theme, typography, reusable Compose UI
  core/testing/           fixtures and test fakes
  feature/auth/
  feature/onboarding/
  feature/dashboard/
  feature/tasks/
  feature/habits/
  feature/challenges/
  feature/goals/
  feature/health/
  feature/deen/
  feature/checkin/
  feature/dreams/
  feature/history/
  feature/reports/
  feature/coach/
  feature/profile/
```

If initial Gradle complexity blocks fast validation, begin with the same package boundaries inside one `app` module, then extract stable core modules. Do not create one ViewModel per small widget or a global mutable state store.

UI state should be immutable and exposed as `StateFlow`. Collect with lifecycle awareness. Model loading, empty, content, recoverable error, authentication-expired, and sync-conflict states explicitly.

## 10. Local persistence and synchronization

Remote Supabase remains authoritative for account data. Room is a cache plus a durable outbox, not a second source of truth.

| Data class | Offline read | Offline write | Conflict rule |
|---|---|---|---|
| Profile/preferences | Cached | Limited | Compare server version; never overwrite silently |
| Tasks | Yes | Not yet queued | Stable UUID create intent/row ID; desired state; soft-delete rules |
| Habit logs | Yes | Absolute daily value/state | Unique user/habit/date; subject RPC for progress |
| Prayer/Quran/check-in/health daily rows | Yes | Upsert whole or field patch only with version | Use `updated_at` guard; surface conflicts |
| Goals/challenges definitions | Yes | Queue only after validation | Request ID for create; version checks for edits |
| Photos/evidence | Metadata only | Queue file upload separately | Keep pending/error state until remote URL is committed |
| AI/chat/report generation | Cached responses | No fake offline success | Require server; retain draft and expose retry |

Outbox items require a stable UUID, user ID, operation type, canonical payload, created time, attempt count, next retry time, and last error. WorkManager should run with network constraints and exponential backoff. Signing out must isolate or clear the correct user's cached private data.

## 11. UI mapping

| Existing pattern | Compose replacement |
|---|---|
| Expo Router stack/tabs | Navigation Compose with typed routes and state restoration |
| NativeWind cards/buttons | Material 3 components wrapped by NAFS design-system components |
| React state/effects | Screen ViewModel plus immutable `StateFlow` state |
| ScrollView lists | `LazyColumn` / `LazyRow` with stable keys |
| RN modal/bottom sheets | Material 3 dialog and modal bottom sheet |
| Pull to refresh | Material pull-to-refresh with explicit refresh state |
| Alert confirmations | `AlertDialog` with destructive-action wording |
| Expo Print/Sharing | Native print framework and FileProvider/share intent for generated PDF |
| Expo image picker | Android Photo Picker; camera only for a confirmed feature |
| Web push | Android notification channels plus FCM/server integration if approved |
| Dark-only palette | Material 3 light/dark color schemes retaining navy/teal/gold identity |

Accessibility requirements include 48dp touch targets, content descriptions for meaningful icons, semantic headings, screen-reader announcements for save/sync errors, font-scale resilience, contrast checks, and no color-only status communication.

## 12. Security and privacy requirements

- Keep service-role, Cloudflare, Anthropic, VAPID private, cron, signing, and WhatsApp secrets server-side.
- Store only the public Supabase URL/anon key and public API base URL in client build configuration.
- Redact auth headers and personal data from logs.
- Enforce authorization in RLS/server routes; hiding UI is not authorization.
- Use HTTPS only and restrict cleartext traffic.
- Use Photo Picker where possible to avoid broad storage permission.
- Do not request notification, camera, or usage access until the related user action needs it.
- Treat health, faith, journal, coaching, and progress data as sensitive personal data in cache, analytics, backup, and export decisions.

## 13. Test strategy

### Local JVM tests

- timezone date resolution and pure date shifting;
- task period keys and missed-state derivation;
- habit schedule/target completion and streaks;
- subject progress idempotency and clamping;
- challenge cadence and restart logic;
- goal milestone progress;
- prayer and weighted habit scoring;
- report coverage/denominator calculations;
- ViewModel state transitions and retry behavior;
- repository conflict/idempotency decisions.

### Database/network tests

- DTO/schema decoding against representative Supabase responses;
- RLS ownership for every direct table path;
- duplicate daily writes and request replay;
- expired/refreshing sessions;
- API error classification;
- Room migrations and outbox recovery.

### Instrumented and UI tests

- auth/onboarding/dashboard flow;
- task and habit completion including process recreation;
- midnight/timezone-change refresh;
- offline write then sync;
- conflict presentation;
- system back behavior and navigation restoration;
- keyboard, font scaling, TalkBack semantics, light/dark themes;
- notification permission and deep-link behavior if implemented.

## 14. Milestones

### Milestone 1 - Repository audit

- [x] Inspect root Expo, web/backend, SQL, domain tests, and current Android shell.
- [x] Create this migration plan.
- [x] Create `ANDROID_FEATURE_PARITY.md`.
- [x] Preserve the Capacitor shell at `android-capacitor-legacy/` and retain `com.ahmad.nafs` as the release application ID.

### Milestone 2 - Native foundation

- [x] Move/archive the existing Capacitor shell.
- [x] Create Android Studio Kotlin/Compose project with Kotlin DSL and API 36 target.
- [x] Establish light/dark theme, navigation, Hilt, Room/DataStore foundations, date/domain rules, and baseline tests.
- [x] Compile debug and run JVM tests.

### Milestone 3 - First vertical slices

- [x] Implement the session guard and email/password authentication UI/data path.
- [x] Implement authenticated profile resolution, onboarding, and the dashboard shell.
- [x] Implement tasks with real Supabase data, cached reads, and safe mutations.
- [ ] Implement habits end to end with real Supabase data and safe mutations.
- [x] Add loading/empty/error/offline states and tests to the task workflow.
- [ ] Extend those states and tests across the remaining first product workflows.

### Milestone 4 - Backend and account integration

- [x] Wire the authenticated Vercel API client with token refresh and an initial evening-review integration; deployment/device validation remains.
- Implement secure account deletion, AI error contract, and server-only boundaries.
- Validate session refresh and RLS behavior.

### Milestone 5 - Complete feature migration

- Goals, challenges, health, deen, check-in, dreams/mapping/logs, history, reports, coach, screen time, improve, profile/settings.
- Keep the parity file current per workflow.

### Milestone 6 - Persistence and sync

- Room cache, DataStore preferences, outbox, WorkManager retries, conflict UI, user isolation.

### Milestone 7 - Performance and quality

- Baseline profiles/macrobenchmarks where tooling allows.
- Startup, recomposition, scrolling, database, network, battery, and APK/AAB review.
- Expand unit, integration, instrumented, and accessibility coverage.

### Milestone 8 - Release preparation

- Release build, R8/resource shrinking, AAB instructions, signing documentation, icons, screenshots, privacy policy, Data Safety, content rating, permissions, internal testing, and known limitations.

## 15. Risks and decisions required

| Priority | Risk / decision | Recommended response |
|---|---|---|
| Resolved | Existing Capacitor project occupied `android/` | Preserved at `android-capacitor-legacy/` before creating the native project |
| Resolved | Final application ID conflicted across configurations | Native release identity retains `com.ahmad.nafs`; debug uses `com.ahmad.nafs.debug` |
| Blocker | Deployed database may differ from additive SQL files | Export deployed schema and reconcile before DTO/Room design |
| High | `quran_log` migration is missing | Recover/export table definition and add a safe migration |
| High | Expo and web behavior diverge | Use tested web/server rules for domain behavior; document product choices |
| High | Direct mobile writes bypass safety APIs | Prefer existing server mutation routes or add equivalent guarded semantics |
| High | Root profile deletion may leave Auth user | Use `/api/user/delete` equivalent only |
| High | Device-local date use can corrupt daily boundaries | Centralize account-timezone clock/date service |
| Medium | Public storage buckets hold sensitive evidence | Review bucket visibility and migrate to signed/private access if needed |
| Medium | Screen-time access could require sensitive permission | Keep manual upload parity unless explicit UsageStats approval is given |
| Medium | Dark-only current app conflicts with light/dark requirement | Build both themes from the NAFS palette |
| Resolved | Android toolchain discovery | Android Studio JBR and the SDK at `%LOCALAPPDATA%\Android\Sdk` successfully build the project |

## 16. Milestone 1 validation record

Checks performed:

- enumerated root, web, API, SQL, and Android sources with `rg`;
- traced root screen Supabase/API calls;
- inventoried backend route methods and environment variables;
- inspected database creation/alteration files and typed models;
- inspected tested domain rules for dates, streaks, progress, tasks, challenges, scoring, idempotency, and write conflicts;
- inspected Android Gradle/manifest/Capacitor configuration;
- verified the current Google Play API 36 requirement from the official Play policy.
- installed the locked root and `web-legacy` npm dependencies;
- ran the complete `web-legacy` Vitest suite: 25 files and 289 tests passed;
- ran `npx tsc --noEmit` in `web-legacy`: passed;
- ran the root Expo lint command: failed because no compatible local ESLint package/config is present, so `npx` fetched ESLint 10 and the legacy `.eslintrc.json` was rejected;
- ran root `npx tsc --noEmit`: failed because `capacitor.config.ts` imports missing package `@capacitor/cli`.

Dependency audit output from installation reported 75 vulnerabilities in the root dependency tree (1 low, 23 moderate, 49 high, 2 critical) and 20 in `web-legacy` (5 moderate, 12 high, 3 critical). No automatic audit fix was applied because it could introduce breaking dependency changes; triage belongs in a controlled dependency milestone.

Not run in Milestone 1:

- production Expo/Next builds;
- Gradle build or Android tests (no Android SDK was discovered at the standard local path);
- emulator/device validation;
- live Supabase schema/auth/RLS validation (no credentials were accessed).

## 17. Milestone 2 validation and next action

The former Capacitor shell is preserved at `android-capacitor-legacy/`. The native project at `android/` keeps the release application ID `com.ahmad.nafs`, targets API 36, compiles with API 37, and uses the debug ID `com.ahmad.nafs.debug`.

On 2026-10-09, `testDebugUnitTest` and `assembleDebug` completed successfully using Android Studio's bundled JBR and the installed Android SDK. Milestone 3 has now started with a real Supabase session/authentication state machine, Keystore-encrypted token persistence, and email/password authentication screens. Live backend/device validation and authenticated profile/onboarding resolution remain before the workflow can be marked implemented.

## 18. Milestone 3 tasks validation and next action

The first real product data slice is implemented in the native Tasks destination: daily, weekly, and monthly current/grace lists use the saved account timezone; reads and mutations remain behind the authenticated Supabase session and RLS; Room schema version 3 stores a user-isolated cache and sync marker.

Creation uses one stable UUID for both the client intent and task row. An exact retry after a lost response resolves the existing row, while different content under the same UUID is rejected as a conflict. Completion sends the desired state, deletion writes `deleted_at`, and Snackbar Undo restores the original row.

The Android client deliberately does not call the current legacy task HTTP routes: those routes resolve authentication from browser cookies, while the native app holds a bearer-authenticated Supabase session. Equivalent guarded behavior is implemented directly against RLS-protected tables without adding a server secret to the APK.

Validation on 2026-10-09:

- `testDebugUnitTest`: 21 tests passed across 9 classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schema version 3 was generated and inspected against migration 2 to 3.

Task editing and a best-effort local reminder strategy are now implemented. WorkManager schedules account-timezone alerts 60 and 30 minutes before incomplete daily tasks, with Android 13+ permission handling and mutation-driven reconciliation. The combined unit, debug assembly, and lint checks pass. Live backend/RLS, runtime permission, reboot persistence, notification delivery, and device UI checks remain pending because no public Supabase configuration was available in the workspace and the attached device was not authorized for ADB. The next implementation slice is schedule-aware habits and atomic subject progress.

The native habits slice now includes RLS-scoped active habits and 400 days of logs, schedule-aware daily/weekday/per-week presentation, live streaks, typed daily progress, optional log notes, user-isolated Room schema v4 caching, and atomic subject updates through `log_subject_habit`. Habit caches are cleared on sign-out. A full native definition editor now creates and edits simple, counter, duration, and subject habits; stable UUID create intents make response-loss retries safe; validation enforces type and cadence invariants; and all habits remain manageable in scheduled, rest-day, and paused sections. Pause/resume is user-scoped and updates the local cache only after server confirmation.

Validation on 2026-10-09: `testDebugUnitTest` passed 33 tests across 11 classes; `assembleDebug` and `lintDebug` passed. Live create/edit/pause/log behavior against the deployed RLS schema and visual/device interaction remain the next checks before the habits workflow is marked implemented.

## 19. Native goals slice validation and next action

The five-destination bottom bar now includes a real Goals workflow rather than a nonfunctional Coach placeholder. It loads RLS-scoped goals and milestones, preserves them in a user-isolated Room v5 cache, and exposes explicit loading, empty, offline, and recoverable error states. Weekly, monthly, quarterly, yearly, and long-term horizons use pure calendar defaults; categories and deadlines are validated; optional numeric targets are supported; and milestone completion is the authoritative displayed progress whenever milestones exist.

Goal creation uses a stable UUID plus deterministic milestone IDs so a lost response can be retried without duplicating the goal or milestones. Editing preserves the original start date, milestone writes send the desired state, numeric progress is clamped for percentage display, and permanent cascade deletion requires an explicit confirmation. Goal cache rows are removed on sign-out. Migration 4→5 was checked against Room's exported schema.

Validation on 2026-10-09: `testDebugUnitTest` passed 40 tests across 12 classes; `assembleDebug` and `lintDebug` passed. The next checks are live deployed-schema/RLS validation and on-device interaction for goal create, milestone completion, numeric updates, edit, delete, and offline fallback. Linked habits/tasks and server-only AI goal features remain separate follow-up slices.

## 20. Live dashboard aggregation

Home now loads tasks, habits, and goals concurrently through their authenticated repositories instead of presenting static navigation copy. Its account-day metrics include only current daily tasks, apply the same schedule and target rules used by the Habits screen, derive goal percentages from the canonical goal model, and select the earliest timed unfinished task before untimed work. Partial refresh failures retain the latest available data and are disclosed; any cached source marks the dashboard offline.

The refreshed Material 3 layout includes a live account-zone clock/date, three compact progress indicators, a next-up task, data-backed destination subtitles, and the nearest unfinished goals. Resume, midnight, date, clock, and timezone events use the existing account-day refresh effect.

Validation on 2026-10-09: `testDebugUnitTest` passed 42 tests across 13 classes; `assembleDebug` and `lintDebug` passed. Live visual and deployed-data validation remains pending on the connected device.

The native shell has since been hardened around real-device behavior and visual quality. It now has a coherent Material 3 design system and useful dashboard, managed notification permission/settings, deep-linked and stale-safe task reminders, startup reminder reconciliation, lifecycle/midnight/system-clock refresh, and authenticated device-timezone adoption. It deliberately requests no location permission: an IANA account timezone is sufficient for the current product, while continuous location would add privacy and Play-policy cost without product value. The automated gate passes; an authorized device remains required for visual, runtime permission, dark-theme, reboot, and delivery-timing validation.

## 21. Native Deen and daily check-in foundations

The native Deen destination now logs the five prayers for the saved account timezone, preserves web and legacy extra-activity JSON, and respects the saved faith-mode preference. Prayer writes use loaded-content compare-and-set protection because the checked-in prayer table has no `updated_at` column. Exact retries can adopt the saved row after a lost response; conflicts require an explicit online reload and retain the local form when reloading fails.

The subsequent native Daily check-in destination supports morning priorities and evening notes against the existing `daily_checkins` contract. Stable row IDs prevent duplicate creation, exact `updated_at` filters protect peer edits, and client saves omit `ai_verdict` so they retain server-generated reviews. Both features cache only AES-GCM-encrypted payloads under dedicated Android Keystore keys and clear user-specific records on sign-out. Room schema v9 and the migration chain from v7 were checked against all exported tables and indexes.

The combined unit gate passed 86 tests across 18 classes, debug assembly passed, and lint passed with the four existing toolchain/target warnings. Read-only deployed-column checks succeeded for prayer, faith mode, and daily check-in resources. The connected Xiaomi phone still rejects installation with `INSTALL_FAILED_USER_RESTRICTED`, so authenticated backend writes, two-device conflicts, and runtime/visual behavior remain unvalidated. The next data slice is a bearer-authenticated server API contract plus native client before adding AI evening verdict generation; the legacy evening-verdict endpoint currently resolves authentication from browser cookies only.

## 22. Native server API and AI evening review

The native API client now uses a configured HTTPS origin, attaches an account-verified Supabase token, refreshes near expiry, and retries a 401 once with the identical intent. Redirects are disabled, external cancellation propagates, and network/provider responses are classified without exposing raw infrastructure text. The backend evening-review route verifies bearer sessions while preserving existing cookie authentication and legacy response consumption. Invalid bearer credentials never fall back to a browser session.

Native generation requires saved current-account-day notes and priorities matching the exact server version. The existing mutations ledger claims the stable request ID before any provider call and replays completed results. The server persists the AI review using an exact `updated_at` filter; a peer edit before or during generation is a conflict. Replayed results are also checked against the current saved version. The authoritative check-in survives secondary report-history failure, and unconfirmed ledger completion retains the claim and requires a reload. Generated records enter the existing encrypted cache without an additional client write.

Validation on 2026-10-10: 96 Android unit tests across 20 classes, debug assembly, and lint passed; all 329 backend tests across 28 files and TypeScript checking passed. The local Next.js endpoint compiled and rejected missing, malformed, and invalid sessions with 401. The four existing Android target/toolchain warnings remain. Deployment and authenticated live generation/device validation are pending; see `web-legacy/NATIVE_API.md` for the existing database/environment dependencies. The next implementation slice is native AI coach chat with saved conversation handling.

## 23. Native coach chat and saved conversations

The native Coach destination now supports recent saved conversations, new chats, retained drafts, explicit sending/retry/reload states, and selectable message text. Outgoing messages carry the complete saved transcript, exact server version, stable conversation UUID, and stable send request ID. Recent history and unresolved sends are encrypted with a dedicated Android Keystore key in Room v10 before generation. A restarted process can retry the same request or recover a matching saved reply. Concurrent refreshes retain newly cached sends and confirmed replies; sign-out clears the user's coach cache.

The chat backend now verifies bearer sessions alongside legacy cookies, builds coaching context from the verified account's records and faith preference, discloses partial context, and guards updates with the loaded conversation version. Durable generation claims prevent duplicate provider calls on retry. Ambiguous save responses and unconfirmed ledger completion retain the claim and require loading saved history. The additive `supabase/coach_conversation_versions.sql` migration preserves existing rows and creation dates while stamping strictly advancing database versions. The cookie response contract remains compatible, shortened web bubble history retains earlier saved turns, and web errors return to the composer without becoming assistant replies.

Validation on 2026-10-10: all 120 Android unit tests across 22 classes, debug assembly, and lint passed. Lint retains five target/dependency warnings and no errors. All 357 backend tests across 29 files, TypeScript checking, and ESLint for the changed backend/web files passed. Room v10 preserves every v9 entity and adds an encrypted coach cache. A read-only check requested no rows and confirmed that the deployed `ai_conversations.updated_at` column is missing (HTTP 400 / SQLSTATE 42703), while the existing ID column is accessible (HTTP 200). The final debug APK installation was rejected by the connected Xiaomi phone with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. Production migration/deployment and authenticated/visual device acceptance remain pending. The next implementation slice is native coach AI report list/detail browsing; see `web-legacy/NATIVE_API.md` for the required deployment order and recovery contract.

## 24. Native coach report archive

Coach now opens a native report archive and detail reader. Each current report type, including growth, has a server-side filter, and All retains unknown future types. Authenticated PostgREST queries are explicitly scoped to the current account. The list fetches metadata in 20-item pages ordered by exact generation timestamp and UUID; the second key prevents gaps when reports share a timestamp. Narrative content loads only when opened. Detail screens render selectable Markdown headings, bold text, lists, quotes, and literal code/HTML, display the stored model, use the account timezone for written dates, and preserve calendar week dates.

Room v11 adds a dedicated AES-GCM/Android Keystore-encrypted report snapshot. It keeps the recent metadata page for each visited filter and the last 20 opened bodies. Offline lists disable older-page loading, and unopened details explain that a connection is needed. Online empty lists replace stale cached pages; missing reports clear their detail copy and archive metadata. Unreadable caches can be rebuilt online, cache-write failures keep authoritative online reads usable, and cancellation/account changes cannot replace a newer screen with an old response. Sign-out and account switches clear report caches. Separate navigation scopes preserve the Coach composer and pending-send state while browsing reports.

Validation on 2026-10-10: all 148 Android tests across 26 classes passed, including 28 new domain, repository, ViewModel, and SDK request tests. Debug assembly and lint passed with the same five warnings and no errors. Room's exported v11 schema preserves all 19 v10 entities and adds only an encrypted report cache. A deployed `ai_reports` column check requested no rows and returned HTTP 200. The archive requires no backend change and remains usable independently of chat's undeployed conversation-version migration. Authenticated RLS, actual Android Keystore recovery, and visual/device acceptance remain pending because the phone previously rejected installation.

The next local slice is native future-self letters with delivery dates and saved replies, keeping reply generation on the server. Chat/evening-review deployment and authenticated/device acceptance remain tracked separately in `web-legacy/NATIVE_API.md` and `ANDROID_FEATURE_PARITY.md`.

## 25. Native future-self letters and draft recovery

Coach now includes a native future-self letter archive, new-letter editor, and original-letter/reply reader. Account-calendar revisit dates and account-zone written timestamps distinguish future targets, letters ready to revisit, and saved replies. Lists use 20-item timestamp/UUID keyset pages. The editor keeps drafts locally, supports offline writing, flushes the latest text before closing, and confirms draft discard. Oversized pasted text remains available for editing.

Room v12 encrypts recent letters, local drafts, and stable pending create intents with a dedicated Android Keystore key. An exact intent is stored before the remote create. Retry/reload recovers the same UUID after a lost response, preserves server reply/delivery metadata, and blocks duplicate taps or a second draft while a save is unresolved. An online missing-record check can reopen the same draft; accepting a different saved version refreshes it before clearing the retained draft. Cache cleanup failures keep the confirmed saved letter visible and retain retry state. Creation sends only ID, user ID, content, and target date; existing saved letters retain their original words. Sign-out and account switches clear letter caches.

Validation on 2026-10-10: all 185 Android tests across 30 classes, debug assembly, and lint passed. The 37 new cases cover rules, repository recovery/account isolation, draft and UI state, and actual SDK requests. Five existing lint warnings remain. The exported v12 schema preserves all 20 v11 entities and adds only an encrypted letter cache. A deployed-column check requested no rows and returned HTTP 200 for the existing letter contract. Authenticated RLS, Keystore recovery, and visual/device acceptance remain pending. The next slice is a bearer-authenticated, retry-safe future-self reply endpoint and native request action; revisit dates do not currently schedule automatic delivery.

## 26. Native future-self reply requests

Due letters now have a native coach reply action with encrypted stable intents in the existing Room v12 snapshot. Exact retries, offline/process-restart retention, online saved-answer recovery, current-detail refresh, cache cleanup failures, clock rollback, and account changes preserve the correct state. Older refresh responses retain newly confirmed replies, and startup recovery opens the saved answer directly.

The future-self endpoint verifies bearer sessions alongside legacy cookies and derives ownership from Auth. Android and Node share a length-prefixed UTF-8 SHA-256 fingerprint of all original letter fields, including exact timestamp precision. Every device claims the same letter version in the existing mutations ledger before contacting AI. The additive `supabase/future_self_replies.sql` function checks authenticated RLS ownership and the account day, atomically compares the original content/time/target and empty delivery fields, and updates only reply/delivery metadata. Original content travels in RPC POST bodies rather than query URLs. A read-only preflight refuses generation when the migration or eligibility is unavailable. Provider failures release claims before persistence; uncertain writes/completions retain them, and saved-row recovery avoids regeneration. Secondary report-history failure preserves the authoritative reply.

Replies use recorded coach context and the saved faith preference, disclose unavailable sources, and treat unrecorded data as unknown. Invented progress/ranking inputs and the stale provider label were removed from this endpoint. Cookie clients retain `{ letterId }` requests and `{ reply }` consumption under the same due-date/save rules. Replies are explicitly requested; automatic delivery and notifications remain separate work.

Validation on 2026-10-10: all 207 Android tests across 31 classes passed with no failures or skips; debug assembly and lint passed with five existing warnings and no errors. All 374 backend tests across 30 files, TypeScript checking, and changed-file ESLint passed. The 22 new native cases and 17 backend cases cover Unicode fingerprint agreement, account/date/faith rules, required local persistence, concurrent devices, original-field conflicts, uncertain writes, restart/reload recovery, cache failures, account changes, stale refreshes, and actual bearer payloads. Room remains v12 and older encrypted snapshot JSON remains readable. No production migration, deployment, or phone installation was performed. SQL/RLS execution, authenticated live generation, Keystore recovery, and visual/device acceptance remain pending.

The next local slice is native coach recovery-plan preview and explicit apply with validated plan shapes and bearer-authenticated routes. Production acceptance requires the conversation-version and future-self-reply SQL migrations plus backend deployment as documented in `web-legacy/NATIVE_API.md`.

## 27. Native recovery-plan preview and selected-step apply

Coach now opens a separate recovery-plan composer from a described problem or a saved conversation. The preview shows the problem, approach, reasons, schedules/targets, due times, photo proof, and context/provider disclosures. Unsent Coach messages remain in their composer. The user selects steps and explicitly adds them through the normal native task, habit, and challenge repositories. Generation performs no feature writes, and clearing a preview keeps items already added.

Room v13 stores encrypted drafts, exact pending generation requests, deterministic per-request/index step UUIDs, selection, and apply progress. Local persistence is required before generation and before every item create. Each step retains its first-add account date across midnight/restart retries. Owned UUID inspection adopts matching committed records, preserves conflicts without overwriting them, and allows retrying only remaining selected steps. Offline previews/drafts remain available; required storage failures block writes. Account changes and sign-out clear the correct private cache.

The shared backend planner verifies bearer Auth alongside existing cookies, derives ownership from the verified account, and claims native generation before contacting AI. Native chat plans load the full owned saved transcript from an ID/version pointer and check exact timestamp precision before/after generation and on replay. Context includes existing habits, recorded misses/possible causes, screen time, sleep, memory, and faith preference, treating unavailable records as unknown. Plan normalization and schema/native create limits reject unusable output before it becomes applicable. Legacy cookie single-item and bounded chat planning contracts remain supported. Problem planning adds no SQL migration; saved-chat plans use the existing conversation-version migration.

Validation on 2026-10-10: all 241 Android tests across 36 classes passed with no failures or skips; debug assembly and lint passed with five existing warnings and no errors. The 34 new native tests cover rules/mappings, persistence/retry recovery, partial saves, frozen dates/IDs, account changes, UI state, and actual API/SDK requests. All 403 backend tests across 31 files, TypeScript checking, and changed-file ESLint passed; 29 new cases cover native ownership/version/claim checks, provider failures, normalization, and cookie compatibility. Exported Room v13 preserves all 21 v12 entities and adds only an encrypted recovery cache. No production migration, deployment, or phone installation was performed. Authenticated live generation/RLS, Keystore recovery, and visual/device acceptance remain pending.

The next local slice is native Quick Add navigation into the already implemented workflows. Production planning acceptance requires backend deployment and the existing conversation-version migration; future-self replies additionally retain their separate reply RPC prerequisite, as documented in `web-legacy/NATIVE_API.md`.

### 2026-10-10 - Production deployment and Android timestamp acceptance

The reviewed six-migration SQL transaction was applied to Supabase project `ckaigykceldybgknytwr`. Vercel cloud deployment `dpl_EVSMhXdtxg3iHMPBgRFEipDWzX5m` completed and was aliased to `https://nafs-one.vercel.app`. Ten production HTTP checks passed; the previously missing native routes now reject unauthenticated requests with HTTP 401.

The remaining Coach history failure was traced to Android timestamp validation after a successful database read. A shared `ServerTimestamp` parser uses `OffsetDateTime` for database timezone offsets, retains the original version strings and preserves microsecond comparisons. It is used by Coach, check-ins, evening verdict comparisons, saved-chat planning, letters and reports. Temporary diagnostic logging was removed; Room remains v14.

Validation: 271 unit tests across 42 classes and all 12 Android 16 phone tests passed. Debug/test assembly, lint (0 errors, 12 existing warnings) and unsigned R8 release APK/AAB assembly passed. Current artifacts are copied to `android/app/build/outputs`, and the corrected debug APK is installed. Read-only live checks passed for Coach conversations/reload, weekly and monthly reports with period selection confirmed, the 30-day history chart, badges/levels and the empty report archive. No AI was generated and no account record was changed. The phone was left on Home and its prior stay-awake setting was restored to `0`.

Remaining separate acceptance: authenticated AI/writes, managed Storage/RLS HTTP behavior, two-device conflicts, saved report bodies, manual accessibility/delivery/share/print checks, release signing/versionCode and Google OAuth provider configuration. Details and the recorded rollback target are in `output/deployment/PLAN.md`. This entry supersedes earlier production-pending and live-load-failure notes.

## Gemini backend integration (2026-10-10)

The shared backend now supports Gemini for all AI features and accurately saves provider/model metadata. Production deployment `dpl_GmJ4CkEF97HpEdLcEhXXXQcwnr47` is Ready; all ten production HTTP/authentication checks passed. Android accepts Gemini recovery responses and saved previews. Backend validation passed 478 tests, production build and lint; Android passed 272 unit tests, debug/test assembly and lint. The updated debug APK is installed. Google generation remains pending because the server key is not configured. The phone is securely locked, so a new UI acceptance run could not complete; earlier device evidence remains documented above. Existing unsigned release files predate this provider change and need rebuilding before publishing. Setup and remaining acceptance are in [PLAN.md](output/deployment/PLAN.md) and [AI.md](docs/AI.md).

Gemini activation is now configured: the sensitive Production key is present, `AI_PROVIDER=gemini` is selected, and deployment `dpl_HMbc8dtkxLZCgBBppKQgetRB86MV` is Ready after receiving those variables. Ten production HTTP checks passed. No key value was downloaded and no account AI generation was performed; authenticated Gemini acceptance remains pending.

The unavailable Coach report led to a model correction: Gemini 3.8 Flash timed out in cloud generation probes, while Gemini 3.5 Flash-Lite responded. Production now explicitly selects Flash-Lite, and deployment `dpl_F8ZdqGVLWWxzrDhiUngVJL7zEtWv` is Ready. Live synthetic calls through the real chat code and full recovery schema pass, as do all ten production HTTP checks and 478 backend tests. No account record was written by these probes; the user should retry the retained Coach request for account persistence acceptance.
