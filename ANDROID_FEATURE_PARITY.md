# NAFS Android Feature Parity

Last updated: 2026-10-10
Target: pure Kotlin + Jetpack Compose Android application  
Current milestone: 3/4 - native product slices and backend integration

## Status definitions

- `Not started`: no native Kotlin/Compose implementation exists.
- `In progress`: native implementation exists but is incomplete or not validated.
- `Implemented`: real implementation is present, connected to the intended data path, and has the required user states.
- `Blocked`: work cannot safely continue without a decision, schema, credential, tool, or external change.

Validation labels:

- `Not run`
- `Unit`
- `Integration`
- `Instrumented`
- `Manual`

The current Expo, Next.js, and Capacitor implementations are reference sources. They do not count as native Android implementation.

## Foundation parity

| Capability | Status | Validation | Notes |
|---|---|---|---|
| Pure Kotlin Android Studio project | Implemented | Unit | Capacitor shell preserved at `android-capacitor-legacy/`; native project builds at `android/` |
| Gradle Kotlin DSL and version catalog | Implemented | Unit | Compile API 37, target API 36, min API 24 |
| Jetpack Compose + Material 3 | Implemented | Unit | Root Compose activity and Material 3 shell compile |
| MVVM / UI-domain-data separation | In progress | Unit/build | Feature repositories, domain rules, ViewModels and Compose screens connected. |
| Hilt dependency injection | Implemented | Unit | Application, activity, ViewModel, and storage module compile through Hilt |
| Navigation Compose | In progress | Unit/build | Authenticated graph, six quick-add actions, feature library, account-scoped back stack and reminder destinations. |
| NAFS light theme | Implemented | Unit | Native light palette and system theme support compile |
| NAFS dark theme | Implemented | Unit | Native dark palette preserves the NAFS visual identity |
| Edge-to-edge and system insets | Implemented | Unit | Activity edge-to-edge and scaffold insets are active |
| Accessibility baseline | In progress | Unit/build/Instrumented | Heading and control semantics, readable chart alternatives, large-font/theme device tests passed; full TalkBack audit pending. |
| Supabase Auth session manager | In progress | Unit | Real SDK state flow and Keystore-encrypted persistence compile; live/device validation remains |
| Authenticated server API client | In progress | Unit/mock integration/build/lint | HTTPS origin restriction, account isolation, proactive token refresh, one 401 retry, timeouts, and sanitized errors implemented; deployed/device validation remains |
| Room cache | In progress | Unit/build | Room v14 includes encrypted generic records, drafts, retained AI requests and sync receipts, alongside prior feature caches. |
| DataStore preferences | Implemented | Unit | System/light/dark preference is persisted; no secrets stored |
| Durable outbox + WorkManager sync | In progress | Unit/build | Encrypted exact record intents, account-scoped network worker, CAS conflicts and confirmed receipts. Other feature writes use their documented explicit retry paths. |
| Network/offline indicator | In progress | Unit/build | Validated connectivity callback and app-wide offline message; cached data remains explicitly labeled. |
| Structured logging/redaction | In progress | Unit/build | Debug-only enum events; no tokens, URLs, payloads, health/faith text or raw exceptions. |
| Unit-test infrastructure | Implemented | Unit | JVM tests cover account dates, habits, task periods, and goal progress |
| Instrumented/UI-test infrastructure | Implemented | Instrumented | Nine tests passed on the connected Android 16 phone, including navigation, report rendering and real Keystore encryption. |
| Release build/AAB path | In progress | Unit/build | R8/resource shrinking and unsigned APK/AAB builds passed. Existing-keystore configuration is prepared; production signing/versioning remains. |

## User-facing feature parity

| Feature / workflow | Reference source | Data/backend | Status | Validation | Native gaps / acceptance notes |
|---|---|---|---|---|---|
| App launch and splash session resolution | `app/_layout.tsx` | Supabase Auth, `users` | In progress | Unit | Auth plus profile/onboarding routing implemented with retry/cache states; device validation remains |
| Email/password sign in | `app/auth.tsx`, web auth | Supabase Auth | In progress | Unit | Real SDK call, validation, loading, mapped errors, and encrypted session persistence compile; live validation remains |
| Sign up and public profile creation | root/web auth | Auth trigger, `users` | In progress | Unit | Auth metadata is sent; public profile reconciliation and live validation remain |
| Password reset email | `app/auth.tsx` | Supabase Auth | In progress | Unit | Enumeration-safe request UI implemented; reset completion/deep-link strategy remains |
| Google OAuth | web setup/docs | Supabase Auth | Blocked | Unit/build | External-browser PKCE and native deep link implemented behind NAFS_GOOGLE_AUTH_ENABLED. Provider console/redirect configuration and live validation remain. |
| Onboarding starter packs | `app/onboarding.tsx`, web onboarding | `habits`, `users` | In progress | Unit | Two-step native flow, skip path, deterministic habit IDs, desired-set cleanup, and profile completion implemented; live validation remains |
| Bottom navigation | `app/(app)/_layout.tsx`, `BottomNav.tsx` | Navigation | Implemented | Unit/build/lint; read-only device | Home, Tasks, Habits, Goals and Profile rendered on the phone. Explore opens the feature library, including History and reports. |
| Quick Add sheet | root layout, `QuickAddSheet.tsx` | Navigation | Implemented | Instrumented | Native task/habit/goal/challenge editors, check-in and Coach; new-goal routing passed on Android 16. |
| Dashboard greeting/date | root/web dashboard | `users`, timezone | Implemented | Unit/build/lint | Authenticated greeting, account-zone date, live clock, midnight/resume refresh, and cache/sync state |
| Dashboard identity/progress score | root/web dashboard, scoring/report libs | many | In progress | Unit/build; read-only device | Uses canonical recorded web report/history metrics and observed task/habit/goal values. New progress API deployment remains. |
| Dashboard next-up/recovery/coaching cards | web dashboard/libs | many, coach notes | In progress | Unit/build/lint | Earliest due unfinished daily task is live; recovery and coaching cards remain |
| Dashboard prayers | root/web dashboard | `prayer_logs` | In progress | Unit/build/lint | Faith-aware native Deen entry point implemented; dashboard prayer summary and live/device validation remain |
| Dashboard habits | root/web dashboard | `habits`, `habit_logs` | In progress | Unit/build/lint | Live scheduled count uses pause, weekday/per-week, counter, duration, and subject completion semantics; device validation pending |
| Dashboard tasks | root/web dashboard | `tasks` | In progress | Unit/build/lint | Live current-account-day daily completion and earliest due unfinished task; device validation pending |
| Dashboard goals/challenges | root/web dashboard | goals/challenges tables | In progress | Unit/build/lint | Live goal averages and nearest unfinished goals plus a native Challenges entry point; challenge summary metrics remain |
| Task list by daily/weekly/monthly period | root/web tasks | `tasks` | Implemented | Unit/build | Current plus one-period grace, completed/missed derivation, account-timezone keys, cached fallback |
| Create/edit task | web task APIs | `tasks`, idempotency | In progress | Unit/build | Create is implemented with validation, due time, priority, and stable UUID intent; editing remains |
| Complete/reopen task | task API, `task-status.ts` | `tasks` | Implemented | Unit/build | Desired boolean state is replay-safe and double taps are suppressed |
| Soft delete and restore task | web task API | `tasks` | Implemented | Build | `deleted_at` plus Snackbar Undo; fails honestly when the soft-delete migration is absent |
| Task reminders | web cron/profile | `tasks.due_time`, Android notifications | In progress | Unit/build/lint | Persistent unique work, app-start reconciliation, stale suppression, permission/settings UI, and Tasks deep link; device delivery validation pending |
| Habit list and daily status | root/web habits | `habits`, `habit_logs` | In progress | Unit/build/lint | Scheduled, rest-day, and paused sections; cached fallback and honest loading/empty/error/offline states; device validation pending |
| Create/edit simple habit | root/web habits | habit APIs | In progress | Unit/build/lint | Stable UUID create replay, native validation, direct RLS insert/update, and full editor; live RLS/device validation pending |
| Counter habit | web habits | habit logs | In progress | Unit/build/lint | Native creation/editing and absolute daily value logging with target-based completion; live validation pending |
| Duration habit | root/web habits | habit logs | In progress | Unit/build/lint | Native creation/editing and daily minute logging with target-based completion; live validation pending |
| Subject/book habit | web habits | RPC `log_subject_habit` | In progress | Unit/build/lint | Native definition editor plus atomic absolute daily delta and server-returned clamped position; live RPC validation pending |
| Habit weekday/per-week schedules | web habits/domain | schedule fields | In progress | Unit/build/lint | Editor validation, rest-day presentation, weekly targets, and cadence-aware live streaks; live mutation validation pending |
| Habit pause/resume | web habits | `habits` | In progress | Unit/build/lint | RLS-scoped pause/resume, separate paused section, and streak-safe semantics; device validation pending |
| Habit notes | web habits | habit/log note fields | In progress | Build/lint | Daily log notes can be saved; note history/edit presentation remains |
| Habit streaks | `streak.ts` and tests | logs | In progress | Unit | Derived live from up to 400 days; today/current week remain pending |
| AI habit starter | web API/client | `/api/ai/habit-starter` | In progress | Unit/build; live pending | Verified native advice gateway, encrypted exact generation retry and explicit validated suggestion creation. |
| Goal list/types/categories | web goals | `goals` | In progress | Unit/build/lint | Weekly through long-term filters, seven categories, deadlines, honest empty/error/offline states, and Room v5 cache; live RLS validation pending |
| Create/edit/delete goal | root/web goals | goals API/table | In progress | Unit/build/lint | Stable UUID create retries, default horizons, validated definition editor, confirmed cascade delete; live device validation pending |
| Goal milestones and true progress | root/web goals, `goals.ts` | `goal_milestones` | In progress | Unit/build/lint | Retry-safe milestone creation, desired-state completion, and milestone ratio overriding numeric/stored progress; live validation pending |
| Goal linked habits/tasks | web goals | APIs, linked IDs | In progress | Unit/build | Owned habit links persist through edits. Selected goal-starter task/habit/challenge suggestions use frozen IDs/dates and explicit creation. Live RLS acceptance pending. |
| Goal AI plan/alignment/starter | web APIs | AI routes, `goals` | In progress | Unit/build; live pending | Native selection, real linked habit context, server-only generation, retained requests and saved-answer recovery. |
| Challenge list/status | root/web challenges | `challenges` | In progress | Unit/build/lint | Native active/completed/all filters, honest loading/empty/error/offline states, deadline completion, and Room v6 cache; failed/restart workflow and live RLS validation remain |
| Challenge cadence | web challenges/domain | frequency fields | In progress | Unit/build/lint | Daily/weekly/monthly/yearly period boundaries, elapsed units, progress, and cadence-aware validation are ported |
| Create/edit/delete challenge | root/web challenges | challenge APIs/table | In progress | Unit/build/lint | Stable UUID create retries, definition-preserving edit, and confirmed cascade delete; photo/sadqa configuration and live validation remain |
| Challenge check-in/streak | root/web challenges | `challenge_checkins` | In progress | Unit/build/lint | Desired completed/missed upsert per date, cadence-aware period status/streak, and best-streak persistence; live cross-device validation remains |
| Challenge photo evidence | web challenges | Supabase Storage | In progress | Unit/build/lint | System Photo Picker, 6 MB image validation, retry-safe user-scoped upload path, and proof URL persistence implemented without gallery/camera permission; live bucket-policy validation pending |
| Challenge sadqa tracking | root/web challenges | challenge/check-in fields | In progress | Unit/build/lint | Optional amount/currency pledge, missed-period paid state, and correction action implemented; live RLS validation pending |
| AI challenge starter | web API/client | `/api/ai/challenge-starter` | In progress | Unit/build; live pending | Verified server route and native advice workflow; suggestions require explicit creation. |
| Health opt-in/profile setup | web health/profile | `users` | In progress | Unit/build/lint | Required height/weight baseline, edit flow, validation, BMI, and dashboard entry point implemented; sleep defaults/configurable fields and live RLS validation remain |
| Daily body metrics/BMI | root/web health | `health_logs`, `users` | In progress | Unit/build/lint | Account-day weight, steps, derived sleep total, hydration, mood, and notes use validated RLS upsert with an AES-GCM/Keystore-encrypted Room v7 cache |
| Exercise logging | root/web health | `health_logs` | In progress | Unit/build/lint | Exercise toggle and required 1–1,440 minute consistency implemented; live validation pending |
| Multiple sleep sessions | root/web health | `health_logs` | In progress | Unit/build/lint | Multiple timed periods, cross-midnight totals, invalid/equal-time checks, and legacy total preservation implemented; live validation pending |
| Hydration, mood, notes | root/web health | `health_logs` | In progress | Unit/build/lint | Partial daily records, bounded inputs, and `updated_at` optimistic conflict protection implemented; live validation remains |
| Meals and custom health metrics | web health, `food.ts` | health JSON fields | In progress | Unit/build/lint | Core/extra meals, food entries, profile metric definitions, and daily yes/no/counter/number values implemented against existing JSON contracts; live validation pending |
| AI health recommendation | web API/client | `/api/ai/health-recommend` | In progress | Unit/build; live pending | Existing baseline prerequisite, saved server recommendation, faith preference and explicit habit creation. |
| Prayer logging | root/web deen | `prayer_logs` | In progress | Unit/build/lint | Account-day 0/1/2 editor, prayer points, web/legacy extras, encrypted Room v8 fallback, replay-safe saves, and compare-and-set conflict UI; live authenticated/device validation remains |
| Faith/deen feature toggle | web profile/deen | `users.deen_enabled` | In progress | Build/lint | RLS-scoped desired preference in You, cached profile flag, and hidden dashboard entry point; saved records are retained; live/device validation remains |
| Prayer times/countdown | web deen/hooks | external prayer API | In progress | Unit/build; live pending | Manual city/country, method and school selection; public Aladhan lookup, timezone/date validation and encrypted same-day fallback. No location permission. |
| Quran logging | root/web deen/log | `quran_log` | In progress | Unit/build; live pending | Validated pages/surah/ayah/notes, dated encrypted drafts and conflict-protected sync. Additive quran_log migration prepared; deployment pending. |
| Morning check-in | root/web check-in | `daily_checkins` | In progress | Unit/build/lint | Native priorities editor, desired completion state, seven-item web limit, account-day keys, stable create ID, encrypted Room v9 cache, and optimistic conflicts; live/device validation remains |
| Evening review | root/web check-in | `daily_checkins` | In progress | Unit/build/lint | Validated narrative and task review share one user/day row; manual saves retain server AI verdicts and expose prior reviews; live/device validation remains |
| AI evening verdict | root/web check-in | AI endpoint | In progress | Unit/mock integration/build/lint | Saved-note generation, verified bearer/cookie auth, durable retry claims, guarded server persistence, and encrypted cache implemented; backend deployment and live/device validation remain |
| Dream statement and target | root/web dreams | `dreams` | In progress | Unit/build; live pending | Statement, reason, target date and required hours through validated, guarded native records RPC. |
| Focused-hours mapping | web dreams/logs | `daily_logs` | In progress | Unit/build; live pending | Real saved dream drives required daily hours; work/study weighting and trajectory preserve the web contract. |
| Daily rich log | web log | `daily_logs`, storage | In progress | Unit/build; live pending | Work, study, screen time, mood, reflection, sleep and Photo Picker uploads; encrypted drafts and exact queued saves. |
| Past log browser | web logs | `daily_logs` | In progress | Unit/build; live pending | Dated records, detail editing and stable date-cursor pagination. |
| 30-day history overview | root/web history | many tables | In progress | Unit/build; live pending | Shared tested web domain, tabs, recorded/pending/unrecorded/unknown coverage and readable chart values. |
| Habits history | web history libs | habits/logs | In progress | Unit/build; live pending | Shared schedule-aware web calculations; missing/unavailable observations retain explicit coverage. |
| Deen history | web history libs | prayer/Quran | In progress | Unit/build; live pending | Faith-mode-aware prayer/Quran history using the shared domain. |
| Tasks/challenges/health history | web history libs | feature tables | In progress | Unit/build; live pending | Shared period/cadence calculations; unknown records never count as failures. |
| Weekly report | root/web reports | report domain/data | In progress | Unit/build; live pending | Current and earlier periods with shared buildReport calculations and truthful no-data/partial states. |
| Monthly report | root/web reports | report domain/data | In progress | Unit/build; live pending | Same canonical report contract as weekly, with account-zone boundaries. |
| AI report review | web reports/API | `report_reviews`, AI deep path | In progress | Unit/build; live pending | Verified gateway, frozen account date, provider disclosure, saved review and account-day server cache. |
| Print/export/share report PDF | root/web reports | Android print/share | In progress | Unit/build; live pending | Native PdfDocument, selectable print pages, document picker and scoped FileProvider sharing; coverage and nested health details retained. |
| AI coach chat | root/web coach | `/api/ai/chat`, conversations | In progress | Unit/mock integration/build/lint | Native chat, recent conversations, encrypted durable send intents, exact-version conflicts, restart/reply recovery, and bearer route implemented; additive conversation-version migration, backend deployment, and authenticated device acceptance remain |
| AI reports list/detail | root/web coach | `ai_reports` | In progress | Unit/mock integration/build/lint | All five current types including growth, unknown types in All, server-filtered metadata paging, native Markdown details, account-zone dates, encrypted Room v11 offline copies, and account cleanup; authenticated RLS/device acceptance pending |
| Recovery plan from coach | web coach/APIs | plan routes | In progress | Unit/mock integration/build/lint | Native problem/saved-chat previews, validated schedules/targets, explicit selected-step apply through normal RLS creates, encrypted retries, frozen dates and partial-save recovery; backend deployment and authenticated/device acceptance remain |
| Future-self letter/reply | web coach/API | `future_self_letters` | In progress | Unit/mock integration/build/lint | Native archive/editor, encrypted drafts and reply intents, due-date server replies, guarded original comparison, two-device generation claims, and restart/timeout recovery; reply RPC migration/backend deployment, automatic scheduling, and authenticated/device acceptance remain |
| Screen-time manual capture | web screen time | storage, `screentime_logs` | In progress | Unit/build; live pending | Manual totals/apps and optional system-picker screenshot; no UsageStats permission. |
| Screen-time AI analysis | web API | `/api/screentime/analyze` | In progress | Unit/build; live pending | Real manual data through the authenticated analyzer, frozen generation retry and CAS save of returned summary. |
| Bug/idea capture | web improve | `app_notes` | In progress | Unit/build; live pending | Validated app_notes editor and browser with encrypted drafts and guarded queued saves. |
| Profile name/avatar | root/web profile | `users`, avatar storage | In progress | Unit/build; live pending | Name editing, system photo picker, public bucket disclosure, rendered avatar and initials fallback. |
| Timezone preference/detection | root/web profile | `users.timezone` | In progress | Unit/build; live pending | Validated IANA zone editor and existing device alignment; saved zone controls daily boundaries. |
| Badges/levels | web profile/domain | `users.badges`, level rules | In progress | Unit/build; live pending | Read-only verified lifetime projection, stored award preservation, faith filtering and encrypted native fallback. |
| Notification settings/test | web profile/APIs | profile, VAPID today | In progress | Unit/build; live pending | Per-device task/habit/letter choices, permission-aware test notification, channels, deep links and best-effort WorkManager delivery. |
| Sign out | root/web profile | Supabase Auth | In progress | Unit | SDK sign-out clears encrypted session; per-user Room cache isolation remains |
| Delete account and all data | web server API | service-role endpoint | In progress | Unit/build; live pending | Explicit DELETE confirmation; verified service-role backend cleans owned storage then deletes Auth identity and cascaded rows. No live deletion tested. |

## Offline and state coverage

| Workflow | Cached read | Queued write | Duplicate protection | Conflict UI | Status |
|---|---|---|---|---|---|
| Session/profile | Yes | Limited | User-keyed cache | Retry/error state | In progress |
| Tasks | Yes | No | Stable create row ID + desired state | Retry/error banner | In progress |
| Habit logs | Yes | No | Unique day key + atomic subject RPC | Retry/error UI | In progress |
| Prayer/check-in daily rows | Encrypted | No | Stable create ID + unique user/day key | Compare-and-set / timestamp conflict UI | In progress |
| Quran daily rows | Encrypted | Yes | Unique day key + exact CAS intent | Retained draft/reload conflict | Migration/deployment pending |
| Health daily rows | Encrypted | No | Unique user/date upsert | Retry/error UI | In progress |
| Goals/challenges | Yes | No | Stable create row ID / unique period key | Retry/error UI | In progress |
| Photo evidence | Uploaded URL + local draft | Record save only | User-scoped object paths | Upload/save failures visible | Live bucket-policy acceptance pending |
| AI generation | Encrypted reviews/conversations | No automatic generation queue | Durable server claims + exact native send intent | Retained text, retry/reload, version conflicts | In progress |

## Critical test parity

| Rule | Existing evidence | Kotlin test | Integration/UI test | Status |
|---|---|---|---|---|
| Account-timezone day | `dates.test.ts` | Present | Timezone change/launch | In progress |
| Pure calendar shifting | `dates.test.ts` | Present | N/A | Implemented |
| Habit target completion | `streak.test.ts` | Present | Habit UI | In progress |
| Scheduled-day streak | `streak.test.ts` | Present | Midnight refresh | In progress |
| Subject progress retry | Web tests and atomic SQL RPC | Repository uses absolute desired progress | Live RPC replay contract pending | In progress |
| Desired task status retry | `task-status.test.ts` | Present | Live RLS contract required | In progress |
| Request idempotency | `idempotency.test.ts` | Present for task create replay/conflict | Live lost-response scenario required | In progress |
| Optimistic conflict classification | Web tests + native API error mapping | Retained CAS/version intents | Two-client live acceptance pending | In progress |
| Challenge cadence/restart | challenge domain | Cadence present; restart required | Challenge UI required | In progress |
| Goal milestone progress | goal domain | Present | Goal UI | In progress |
| Health progress/sleep | health domain/tests | BMI/range/session validation present | Native Health form present | In progress |
| Coverage/unknown denominator | Shared tested web history/report domain | Native export coverage tests present | Instrumented PDF rendering passed | In progress: deployed API acceptance pending |
| Report calculations | Shared buildReport and web golden tests | Native consumes shared server output | Native PDF page/range tests passed | In progress: backend deployment pending |
| AI schema/error mapping | AI/schema tests | Required | Mock server/API | In progress: evening review transport, saved response validation, and sanitized route failures covered |

## Known blockers and discrepancies

1. The deployed Supabase schema has not been exported, while repository SQL is a set of additive scripts without a single ordered migration history.
2. `supabase/native_records.sql` now creates `quran_log` and the guarded native record RPC; deployed-schema export and execution remain pending.
3. Native progress uses the tested web report/history domain. Dashboard exposes recorded progress; a separate identity-score redesign has not been invented.
4. Root TypeScript now excludes the archived Capacitor configuration. Expo 52-compatible local lint tooling now passes separately from the web backend (24 existing warnings).
5. The archived Capacitor shell references Capacitor Gradle modules, but the root package manifest/lock install does not provide `@capacitor/cli` or `@capacitor/android`.

## Milestone log

### 2026-10-09 - Milestone 1 audit

Implemented:

- complete repository architecture inventory;
- native migration plan;
- this feature parity baseline;
- initial screen, data, backend, business-rule, security, offline, and test mapping.

Files created:

- `ANDROID_MIGRATION_PLAN.md`
- `ANDROID_FEATURE_PARITY.md`

Checks run:

- source/file inventory and targeted static tracing;
- backend route and environment-variable inventory;
- SQL table/migration inventory;
- current Android Gradle/Capacitor inspection;
- official Google Play target API policy verification.
- `web-legacy` Vitest: 25 files, 289 tests passed;
- `web-legacy` TypeScript check: passed;
- root Expo lint: failed because `npx` fetched ESLint 10 but the project only has legacy `.eslintrc.json` configuration;
- root TypeScript check: failed because `@capacitor/cli` is imported but absent from the root dependencies;
- npm installation audits reported 75 root and 20 `web-legacy` dependency vulnerabilities; no breaking automatic fixes were applied.

Not run:

- Expo/Next production builds;
- Android build, unit tests, emulator, or device tests;
- live backend/RLS verification.

Next milestone:

- resolve preservation of the existing `android/` shell and application ID;
- scaffold the Kotlin/Compose foundation under `android/`;
- compile a debug build and run the first Kotlin unit tests.

### 2026-10-09 - Milestone 2 native foundation

Implemented:

- preserved the former Capacitor shell at `android-capacitor-legacy/`;
- created the Kotlin/Compose project with Kotlin DSL, a version catalog, API 36 target, and `com.ahmad.nafs` release identity;
- added Material 3 light/dark/system themes, edge-to-edge activity setup, Navigation Compose, Hilt, Room/outbox schema, DataStore theme preferences, and WorkManager readiness;
- ported initial pure Kotlin rules for account-timezone dates, habit completion/streaks, task periods, and goal milestone progress.

Validation:

- `testDebugUnitTest`: passed;
- `assembleDebug`: passed;
- Android Studio's bundled JBR and the installed `%LOCALAPPDATA%\Android\Sdk` were used without changing global environment variables.

Next milestone:

- implement the Supabase session/authentication state machine, onboarding, and dashboard shell;
- connect tasks and habits to real data with explicit loading, empty, error, and offline states.

### 2026-10-09 - Milestone 3 authentication slice started

Implemented:

- current Supabase Kotlin Auth/PostgREST dependencies and Android Ktor engine;
- build-time public Supabase configuration with an honest missing-configuration screen;
- session-state routing for initializing, signed out, authenticated, expired refresh, and configuration-missing states;
- email/password sign-in, sign-up metadata, enumeration-safe password-reset request, and sign-out;
- AES-GCM session persistence using a non-exportable Android Keystore key;
- auth input validation tests and mapped user-safe authentication errors.

Validation:

- `testDebugUnitTest`: 11 tests passed across 5 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- live auth and device UI validation were not run because no Supabase credentials were accessed and the connected Android device was not authorized for ADB.

Next slice:

- resolve the authenticated `users` profile and route new users through onboarding;
- validate sign-in, session restore, reset email, and sign-out against the real backend on an authorized device.

### 2026-10-09 - Authenticated profile and onboarding slice

Implemented:

- RLS-scoped `users` profile lookup with safe recreation when the auth trigger row is absent;
- user-isolated Room profile cache, offline fallback, and an explicit schema migration from version 1 to 2;
- root routing through profile loading, retry, onboarding, and authenticated app states;
- two-step name/starter-habit onboarding based on the richer web habit contract;
- deterministic UUIDv5 starter IDs plus desired-set cleanup, making retries converge without duplicate habits;
- profile completion is written only after starter habit synchronization succeeds;
- authenticated dashboard greeting and profile summary from real profile state.

Validation:

- `testDebugUnitTest`: 14 tests passed across 6 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schema version 2 was generated and inspected against the migration columns;
- live backend and device UI validation remain pending for the same credential/ADB authorization reasons.

Next slice:

- implement the first real dashboard data workflow, beginning with tasks or habits;
- add device/integration coverage when the backend configuration and ADB authorization are available.

### 2026-10-09 - Native tasks vertical slice

Implemented:

- RLS-scoped daily, weekly, and monthly task loading with account-timezone period anchors;
- current-period and one-period grace lists with derived missed/completed states and priority ordering;
- Room task cache plus sync metadata and the version 2 to 3 migration, including an explicit offline banner;
- validated task creation with a stable UUID used as both the creation intent and row ID, so a lost-response retry converges without a duplicate;
- desired-state complete/reopen writes that do not flip on retry;
- soft deletion with Snackbar Undo restoring the original row;
- explicit loading, empty, cached, refresh, form, and mutation failure states.

Validation:

- `testDebugUnitTest`: 21 tests passed across 9 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schema version 3 was generated and inspected against the migration;
- live RLS/backend and device UI validation remain pending because public Supabase configuration was not available and the attached device was not ADB-authorized.

Next slice:

- add task editing and decide the native reminder delivery path;
- begin the habits vertical slice, including schedule-aware status and atomic subject progress.

### 2026-10-09 - Task editing and local reminders

Implemented:

- task editing for title, note, priority, and daily due time, with RLS-scoped updates and Room cache refresh;
- account-timezone reminder planning at 60 and 30 minutes before a daily task's due time;
- WorkManager-backed, uniquely keyed local reminder work that is reconciled after load and mutations;
- cancellation after completion/deletion and rescheduling after edit/restore;
- Android 13+ notification permission flow, a dedicated notification channel, and launch intent.

Validation:

- `testDebugUnitTest`: 24 tests passed across 10 test classes, including reminder timezone, elapsed-window, and invalid-input cases;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- live backend/RLS, runtime permission, reboot persistence, and on-device notification delivery remain pending.

Next slice:

- begin the habits vertical slice, including schedule-aware status and atomic subject progress;
- add device/integration coverage when backend configuration and ADB authorization are available.

### 2026-10-09 - Native habits logging slice

Implemented:

- RLS-scoped active-habit loading plus 400 days of logs for live streak derivation;
- daily, weekday, and per-week scheduling with paused habits excluded and current periods treated as pending;
- simple completion toggles and absolute daily counter/duration logging with target-derived completion;
- subject progress through `log_subject_habit`, using absolute daily deltas and the server-returned clamped position;
- optional daily notes, typed progress validation, explicit loading/empty/error/offline states, and a Habits destination;
- user-isolated Room habit/log cache and the version 3 to 4 migration.

Validation:

- `testDebugUnitTest`: 26 tests passed across 10 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schema version 4 generated successfully;
- live RLS/RPC and device UI validation remain pending because public backend configuration and an authorized device were not available.

Next slice:

- add habit creation/editing, pause/resume, and schedule controls;
- add live Supabase integration coverage, especially atomic subject retries and cross-device updates.

### 2026-10-09 - Production shell, reminders, time, and visual system

Implemented:

- cohesive emerald/navy/gold Material 3 color system, typography, shapes, branded hero surfaces, and light/dark presentation;
- useful native dashboard with live account time, quick actions, sync state, and explicit privacy copy;
- five-destination bottom navigation plus polished task, habit, authentication, and profile surfaces;
- notification permission/status management with direct Android system-settings access;
- task notification deep links, private lock-screen visibility, current remaining-time copy, and expired reminder suppression;
- persistent local active-task registry so locally completed or deleted tasks cannot emit stale queued reminders;
- app-start task reconciliation and notification-channel creation;
- automatic task/habit refresh on resume, account midnight, and Android date/time/timezone changes;
- one-tap device timezone adoption through the authenticated profile, with reminder rescheduling after success;
- no location permission: current scheduling uses the explicit account timezone and does not justify continuous device tracking.

Validation:

- `testDebugUnitTest`: 27 tests passed across 10 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- packaged permissions inspected: Internet, notification, network/work scheduling, wake lock, and boot restore only; no location permission;
- visual/device notification validation remains pending because the attached device is ADB-unauthorized and no emulator is installed.

Next slice:

- run the complete sign-in, timezone, reminder, midnight, dark-theme, and accessibility matrix on an authorized device;
- add server push/FCM only if cross-device notification delivery becomes a confirmed product requirement.

### 2026-10-09 - Native goals and challenges slices

Implemented:

- native goal list/filtering, definition editing, numeric progress, milestones, confirmed deletion, and milestone-derived true progress;
- retry-safe goal creation plus a user-isolated Room v5 goal/milestone cache;
- native challenge list/filtering, cadence-aware creation/editing, confirmed deletion, and completed/missed period check-ins;
- daily, weekly, monthly, and yearly elapsed-unit, period-boundary, progress, and streak rules;
- deadline completion and best-streak persistence matching the existing web behavior;
- retry-safe challenge creation plus a user-isolated Room v6 challenge/check-in cache;
- dashboard and authenticated navigation entry points for both feature slices.

Validation:

- `testDebugUnitTest`: 52 tests passed across 15 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schemas through version 6 generated successfully;
- the debug APK installed and launched without a fatal exception on the attached Android device;
- interactive visual/auth/backend validation remains pending because the handset was locked during ADB validation.

Next slice:

- unlock the authorized device and run the sign-in, onboarding, task, habit, goal, challenge, timezone, reminder, dark-theme, and accessibility matrix;
- add challenge photo proof and sadqa only after validating the existing core challenge contract live.

### 2026-10-09 - Challenge evidence and sadqa completion

Implemented:

- Android system Photo Picker evidence selection with no camera or broad gallery permission;
- JPEG, PNG, WebP, HEIC, and HEIF validation with a 6 MB in-memory upload limit;
- stable per-user/per-challenge evidence paths and retry-safe Supabase Storage upserts;
- user-path-scoped insert, update, and delete storage policies for `challenge-photos`;
- editable photo-proof requirement and optional sadqa pledge with supported currency validation;
- missed-period sadqa paid state at check-in plus later mark-paid/undo correction.

Validation:

- `testDebugUnitTest`: 54 tests passed across 15 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- packaged permissions remain Internet, notifications, and WorkManager/runtime support only; no camera or media permission was added;
- live Storage/RLS and Photo Picker interaction remain pending until the SQL policy is deployed and a device install is approved.

Next slice:

- implement the Health foundation with profile setup and validated daily body/activity metrics;
- return to live device validation once Xiaomi `Install via USB` is enabled.

### 2026-10-09 - Native Health foundation

Implemented:

- authenticated Health route from the dashboard with account-timezone daily identity;
- required height/current-weight baseline, editable profile, and metric BMI calculation;
- partial daily logging for hydration, steps, sleep total, exercise/minutes, weight, mood, and notes;
- bounded input validation, explicit loading/offline/error/success states, and medical-safety copy;
- RLS-scoped profile updates and retry-safe `health_logs` upsert on `(user_id,date)`;
- user-isolated Room v7 fallback with all body metrics, mood, and notes encrypted as AES-GCM payloads by a dedicated non-exportable Android Keystore key.

Validation:

- `testDebugUnitTest`: 59 tests passed across 16 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- Room schema v7 contains only encrypted payload/IV columns for cached health values; body fields and notes are not stored as plaintext columns.

Next slice:

- add daily meal/custom-metric workflows;
- add live Health RLS/cache/device validation and optimistic conflict handling.

### 2026-10-09 - Native multiple sleep periods

Implemented:

- add, edit, and remove multiple daily sleep periods with 24-hour start/end inputs;
- cross-midnight duration calculation and a derived one-decimal `sleep_hours` total for existing reports;
- compatibility writes to `sleep_time`/`wake_time`, JSON `sleep_sessions`, and preservation of older total-only records until replaced;
- validation for malformed/equal times and combined totals above 24 hours;
- encrypted Room cache serialization of the timed periods without adding plaintext health columns.

Validation:

- `testDebugUnitTest`: 62 tests passed across 16 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- the Android DTO matches the existing `supabase/sleep_sessions.sql` and web data contract.

Next slice:

- add live Health RLS/cache/device validation and optimistic conflict handling.

### 2026-10-09 - Native meals and custom health metrics

Implemented:

- permanent breakfast, lunch, and dinner slots plus repeatable snack, brunch, tea, sehri, iftar, and late-night meals;
- add/remove food and drink entries while keeping empty meal slots distinct from meals actually eaten;
- user-profile custom metric definitions for yes/no, counter, and number trackers with optional numeric targets and units;
- daily primitive values in `health_logs.extras` and metric definitions in `users.health_extras_config`, matching the existing web JSON contract;
- guarded metric removal that retains older history, bounded meal/food/metric validation, and retry/error UI;
- AES-GCM/Keystore-encrypted cache payloads for meal entries, metric definitions, and daily values without a Room schema change.

Validation:

- `testDebugUnitTest`: 67 tests passed across 16 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- serialization tests verify the existing lowercase metric types and primitive `extras` object shape;
- Room schema v7 still exposes only encrypted health payload/IV columns.

Next slice:

- run live Health RLS/cache/device validation once Xiaomi `Install via USB` is enabled.

### 2026-10-09 - Health optimistic write protection

Implemented:

- new daily rows use insert semantics so a concurrent `(user_id,date)` creation becomes a conflict instead of an overwrite;
- existing rows update only when both the loaded row ID and exact `updated_at` version still match;
- every successful write advances `updated_at` and adopts the returned version for the next save;
- duplicate-key and zero-row update outcomes become a dedicated conflict state rather than a generic success/failure;
- a blocking conflict banner preserves the local form and requires a successful reload before saving again;
- reload failures remain visible and keep stale writes disabled.

Validation:

- `testDebugUnitTest`: 69 tests passed across 16 test classes;
- `assembleDebug`: passed;
- `lintDebug`: passed;
- duplicate-key classification and complete row-version validation have pure Kotlin coverage.

Next slice:

- begin the native Deen workflow with account-day prayer logging and status validation;
- return to live Health conflict/RLS validation once device installation is permitted.

### 2026-10-09 - Native Deen foundation

Implemented:

- account-timezone daily prayer logging with explicit missed/alone/jamaat choices and 0/1/2 values;
- prayed/jamaat counts and the existing 10-point base prayer score without choosing a global identity-score formula;
- suggested and custom boolean extra prayers, existing web count activities, and legacy status-based extra prayers;
- original extra-activity JSON fields retained during edits, including unknown web fields;
- saved `users.deen_enabled` preference in You, a hidden dashboard entry when disabled, and retained prayer history;
- AES-GCM prayer cache under a dedicated Android Keystore key with Room migration 7 to 8;
- stable row identity, exact-response-loss replay resolution, full loaded-content compare-and-set writes, and a conflict banner that preserves the local form until a successful online reload.

Validation:

- the Deen checkpoint passed 77 unit tests across 17 classes, debug assembly, and lint;
- anonymous read-only checks returned HTTP 200 for the deployed prayer columns, JSON equality filter, and faith preference column; authenticated RLS writes are not yet validated;
- migration 7 to 8 matches Room's exported tables and indexes;
- the connected Xiaomi device rejected the debug install with `INSTALL_FAILED_USER_RESTRICTED`; runtime and visual validation remain pending.

### 2026-10-09 - Native daily check-in foundation

Implemented:

- authenticated Daily check-in destination from Home with account-day morning and evening modes;
- a priorities list matching the existing `{text,done}` JSON contract, desired completion state, removal, and the web's seven-item upper limit with 3–5-item guidance;
- validated evening notes and an explicit save flow with loading, empty, cache, error, and conflict states;
- stable create intent and duplicate-key replay resolution, followed by exact `updated_at` version protection for existing rows;
- monotonically advanced write timestamps even after a device clock rollback;
- server-generated `ai_verdict` values excluded from all client write payloads; existing verdicts remain readable with their earlier-entry attribution;
- AES-GCM/Keystore-encrypted check-in cache under a separate key, Room migration 8 to 9, and per-user sign-out cleanup;
- dirty forms survive routine same-day refreshes, and failed offline conflict reloads keep unsaved entries and stale-write blocking.

Validation:

- `testDebugUnitTest`: 86 tests passed across 18 classes; `assembleDebug` and `lintDebug` passed;
- the deployed daily-check-in columns passed an anonymous read-only HTTP 200 schema check;
- the full Room migration chain 7 to 8 to 9 matches all exported table and index definitions; prayer and check-in tables contain only encrypted payload/IV columns alongside user/day identifiers;
- lint reports the same four existing toolchain/target warnings and no errors; authenticated RLS, two-device conflicts, cache recovery, visual interaction, and midnight/timezone behavior remain pending on a device that accepts installation.

Next slice:

- add a bearer-authenticated server API contract and native API client before implementing AI evening verdict generation; the current `/api/ai/evening-verdict` route authenticates only through browser cookies;
- resume phone validation once Xiaomi USB installation is permitted;
- Quran remains blocked by its missing repository table migration, and prayer times remain a separate location/offline-cache decision.

### 2026-10-10 - Native server API and AI evening review

Implemented:

- a reusable HTTPS API client with account isolation, proactive Supabase token refresh, one 401 refresh retry, redirect blocking, request timeouts, and sanitized errors;
- public API origin configuration through `NAFS_API_BASE_URL` or `EXPO_PUBLIC_API_BASE_URL`, defaulting to the existing Vercel host;
- verified bearer authentication alongside the existing cookie flow, using a public-key Supabase client and refusing cookie fallback when a bearer header is invalid;
- an evening review action that requires saved notes and priorities, retains the same request ID during retry, blocks duplicate taps, and stores the successful server record in the encrypted check-in cache;
- current-account-day and exact saved-version checks before generation, preserving PostgreSQL timestamp precision, followed by a version-guarded server write that changes only the AI verdict and timestamp;
- strict durable generation claims using the existing user-scoped mutations table, completed-response replay without another AI call, and explicit reload handling for unfinished or unconfirmed results;
- stale replay rejection after peer edits, recorded-data coaching context, faith-mode-aware prompts, correct provider/model metadata, and best-effort secondary report history;
- deployment and error/retry contracts in `web-legacy/NATIVE_API.md`.

Validation:

- `testDebugUnitTest`: 96 tests across 20 classes passed; `assembleDebug` and `lintDebug` passed on the final native sources;
- `web-legacy`: all 329 tests across 28 files passed, including 40 authentication, durable-claim, and route cases; TypeScript checking passed;
- the actual local Next.js route compiled and returned the expected 401 errors for no session, malformed authorization, and an invalid bearer token;
- lint retains four existing target/toolchain warnings and no errors; no production deployment, authenticated AI generation, or phone interaction was performed.

Next slice:

- native AI coach chat with a bearer-authenticated route and saved conversation handling;
- deploy the evening review backend before end-to-end testing against the configured API origin;
- complete authenticated RLS/conflict and visual/device checks once the phone accepts installation.

### 2026-10-10 - Native coach chat and saved conversations

Implemented:

- a real Coach destination from Home, recent conversation selection, new chats, selectable message text, and explicit loading/empty/offline/error/sending states;
- exact saved transcript and PostgreSQL version handling, retained failed messages, duplicate-tap blocking, and explicit retry/reload/draft recovery;
- AES-GCM/Android Keystore-encrypted recent history and durable send intents persisted before contacting AI, with Room migration 9 to 10 and per-user sign-out cleanup;
- process-restart recovery of the same send request, recovery of a matching saved reply, and concurrent refresh protection for pending sends and newly confirmed replies;
- bearer-authenticated chat using recorded account context and the saved faith preference, partial-context disclosure, durable server generation claims, guarded conversation writes, and stale replay rejection;
- ambiguous committed writes and ledger completion failures retain their generation claim and require reloading saved history;
- an additive SQL migration that backfills strictly advancing conversation versions while preserving existing rows and creation dates;
- legacy cookie clients retain their reply contract, shortened bubble transcripts retain earlier saved turns, web failures return to the composer, and the web selects conversations by update time;
- full conversations require a new chat; oversized pasted drafts remain intact for editing rather than being truncated.

Validation:

- final `testDebugUnitTest`: 120 tests across 22 classes passed; `assembleDebug` and `lintDebug` passed;
- lint reports five target/dependency warnings and no errors, including the new test dependency's version notice;
- all 357 backend tests across 29 files passed, including 26 chat route cases and 11 strict generation-claim cases; TypeScript checking and ESLint for the changed backend/web files passed;
- Room v10 keeps all v9 entities unchanged and adds only the user identifier plus encrypted coach ciphertext/IV columns;
- read-only deployed-schema checks requested no rows: `ai_conversations.id` returned HTTP 200, while `updated_at` returned HTTP 400 / SQLSTATE 42703, confirming that the conversation-version migration is still required;
- the final APK installation was attempted once on the connected Xiaomi phone and rejected with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; authenticated chat, RLS/conflict, and visual/device acceptance remain unvalidated;
- no production SQL mutation or backend deployment was performed.

Next slice:

- apply `supabase/coach_conversation_versions.sql` and deploy the updated `web-legacy` backend before live chat/evening-review acceptance;
- resume phone interaction when the device accepts USB installation;
- add native coach AI report list/detail browsing without changing the server-only generation boundary.

### 2026-10-10 - Native coach report archive

Implemented:

- Coach → Reports list and detail destinations, with separate navigation scopes that retain the chat draft and pending-send state;
- server-side filters for daily verdicts, weekly tribunals, gap analyses, letter replies, and growth reviews; All also preserves unknown future report types;
- user-scoped metadata-only queries with 20-item keyset pages, exact timestamp precision, and a UUID tie-breaker; full narrative content loads when a report is opened;
- native selectable Markdown text with headings, bold runs, lists, quotes, and literal code/HTML text; saved model metadata, account-zone creation dates, and unchanged calendar week dates;
- explicit loading, refresh, empty, offline, missing-detail, and older-page retry states, including cancellation/account-change protection;
- AES-GCM/Android Keystore-encrypted recent metadata pages per filter and the last 20 opened bodies, with Room migration 10 to 11 and per-user sign-out/account-switch cleanup;
- offline archives stop at their cached page, unopened details disclose their connection requirement, successful empty refreshes replace stale lists, and authoritative missing details remove cached copies;
- read-only browsing through the existing authenticated Supabase SDK, independent of the chat version migration and server AI generation endpoints.

Validation:

- final `testDebugUnitTest`: 148 tests across 26 classes passed with no skipped tests; `assembleDebug` and `lintDebug` passed;
- 28 new tests cover precise paging boundaries, unknown types, account-zone dates, Markdown, account isolation, offline recovery, empty/deleted reports, cache failures, concurrent UI requests, and actual SDK-generated PostgREST request filters;
- lint reports the same five target/dependency warnings and no errors;
- Room v11 preserves all 19 v10 entity definitions and adds only the user identifier plus encrypted report ciphertext/IV columns;
- a read-only deployed `ai_reports` check selected every required column with `limit=0` and returned HTTP 200 / an empty array; this verifies the column contract, not authenticated RLS;
- backend source and deployment were unchanged; authenticated RLS, visual/device interaction, and actual Keystore/cache recovery remain pending because the connected phone previously rejected installation.

Next slice:

- native future-self letter list/editor with saved delivery dates and replies, preserving server-only AI reply generation;
- apply `supabase/coach_conversation_versions.sql` and deploy the updated backend before live chat/evening-review acceptance;
- resume authenticated report/RLS and visual/cache validation when the device accepts USB installation.

### 2026-10-10 - Native future-self letters and draft recovery

Implemented:

- Coach → Future-self letters archive, new-letter editor, original-letter reader, and existing saved coach replies;
- account-calendar revisit targets, account-zone written timestamps, ready-to-revisit/reply status, and 20-item timestamp/UUID keyset pagination;
- encrypted local draft autosave with an editor-close flush, offline composition, explicit draft discard, and oversized pasted text retained for editing;
- stable create UUIDs and exact pending intents persisted before contacting Supabase, duplicate-tap blocking, committed-response-loss recovery, and process-restart retry;
- online checks before resuming a missing save, same-UUID editing after recovery, explicit acceptance of a different saved version, and retained state after failed reload/cleanup;
- four-field creation payloads preserve server-owned written times, delivery stamps, and AI reply text; saved letters retain their original words;
- dedicated AES-GCM/Android Keystore letter cache and Room migration 11 to 12, with sign-out/account-switch cleanup;
- honest revisit dates and saved delivery state; automatic notifications and native AI reply requests remain separate server work.

Validation:

- all 185 Android tests across 30 classes passed, with no skipped tests; `assembleDebug` and `lintDebug` passed;
- 37 new tests cover date/content rules, paging, draft recovery, exact retries, lost responses, server metadata preservation, cache failures, account changes, concurrent refresh/create, conflict acceptance, and actual PostgREST request bodies/filters;
- lint retains five existing target/dependency warnings and no errors;
- Room v12 preserves all 20 v11 entity definitions and adds only a user identifier plus encrypted letter ciphertext/IV columns;
- an anonymous deployed-column check requested no rows (`limit=0`) and returned HTTP 200 for every required `future_self_letters` column;
- no production data write, backend deployment, or phone installation was performed; authenticated RLS, actual Keystore recovery, and visual/device acceptance remain pending.

Next slice:

- bearer-authenticated, retry-safe server future-self replies and the native request action;
- deploy existing chat/evening-review backend changes and apply the conversation-version migration before their live acceptance;
- resume authenticated/device validation when USB installation is permitted.

### 2026-10-10 - Native future-self reply requests

Implemented:

- due-letter native reply action with encrypted stable intents, exact retry, offline/process-restart retention, and saved-answer recovery;
- fresh detail reads, missing/changed-original handling, truthful cache cleanup failures, and protection against stale refresh/account responses;
- verified bearer/cookie endpoint with a shared UTF-8 original-field fingerprint, account-calendar due-date checks, and ownership derived from Auth;
- one durable generation claim per letter version across devices, guarded atomic reply/delivery writes, and recovery after uncertain writes or ledger completion;
- additive authenticated/RLS reply RPC with read-only preflight and original content carried in POST bodies;
- recorded-context and faith-aware replies, partial-context disclosure, correct provider/model history, and best-effort report copies;
- cookie `{ letterId }` / `{ reply }` compatibility; automatic delivery/notifications remain separate work.

Validation:

- all 207 Android tests across 31 classes passed with no failures or skips; `assembleDebug` and `lintDebug` passed with five existing warnings and no errors;
- 22 new native cases cover Unicode fingerprint agreement, API payload/token validation, date/clock rules, persistence/restart/reload recovery, cache failures, stale refreshes, and account isolation;
- all 374 backend tests across 30 files passed, including 17 new future-self route cases; TypeScript checking and changed-file ESLint passed;
- Room remains v12 and previous encrypted snapshot JSON remains readable without a pending reply field;
- no production migration/deployment or phone installation was performed; SQL/RLS execution, authenticated live generation, Keystore recovery, and visual/device acceptance remain pending.

Next slice:

- native recovery-plan preview and explicit apply with schema validation and bearer-authenticated routes;
- apply `supabase/coach_conversation_versions.sql` and `supabase/future_self_replies.sql` before deploying the updated backend;
- complete authenticated/device acceptance when the phone permits USB installation.

### 2026-10-10 - Native recovery-plan preview and selected-step apply

Implemented:

- Coach entry points for describing a setback or planning from a saved conversation, with a separate composer and preview;
- problem, approach, per-step reason, target/schedule, due time, photo proof, partial-context, and provider-fallback disclosures;
- explicit selected-step apply through the existing native task, habit, and challenge create repositories; preview generation performs no feature writes;
- Keystore-encrypted Room v13 drafts, exact generation retries, deterministic step UUIDs, selection, frozen first-add dates, and per-item progress;
- required local persistence before generation and each item write; unreadable retry storage blocks writes instead of replacing IDs;
- owned-record inspection before create, matching-record recovery after uncertain responses, conflict preservation, and retries limited to remaining selected steps;
- offline drafts/previews, restart recovery, duplicate-tap suppression, explicit preview clearing, and account/sign-out cache isolation;
- bearer-authenticated problem and saved-chat routes with verified ownership, full saved transcripts, exact version checks before/after generation and replay, and durable pre-generation claims;
- schema/native-limit validation and normalization, sanitized errors, faith/unknown-data semantics, and existing cookie planning contracts.

Validation:

- all 241 Android tests across 36 classes passed with no failures or skips; debug assembly and lint passed with five existing warnings and no errors;
- 34 new native cases cover validation, mappings, required storage, partial save/date/UUID recovery, account changes, drafts and explicit apply, actual bearer request bodies, and actual SDK read filters;
- all 403 backend tests across 31 files passed, including 29 new planning cases; TypeScript checking and changed-file ESLint passed;
- rebuilt debug APK: `android/app/build/outputs/apk/debug/app-debug.apk` (27,626,326 bytes);
- Room v13 preserves all 21 v12 entities unchanged and adds only a user identifier plus encrypted recovery ciphertext/IV columns;
- no production SQL migration, deployment, or phone installation was performed; authenticated generation/RLS, Keystore recovery, and visual/device acceptance remain pending.

Next slice:

- native Quick Add navigation into implemented task, habit, goal, challenge, check-in, and Coach workflows;
- deploy the updated planning routes; saved-chat plans require the existing conversation-version migration;
- complete authenticated/device acceptance when USB installation is permitted.

### 2026-10-10 - Remaining native workflows and Android 16 device validation

The previously missing native slices now include Quick Add, the feature library, profile/avatar editing, generic dream/log/Quran/screen-time/note records, retained encrypted drafts and guarded background sync, history, reports, native PDF output, structured advice and explicit suggestion creation, linked goal habits, challenge restart/history, prayer times, badges and reminder preferences. Room schema v14 was generated. Account-cache writes verify the active user before and after persistence; logout cancels scoped work.

Validated locally:

- 268 Android unit tests passed across 41 classes.
- Debug app and test APK assembly passed; Android lint passed with 0 errors and 12 warnings.
- R8 release APK and AAB assembly passed unsigned, with optional existing-keystore wiring prepared.
- 430 backend tests passed across 37 files; TypeScript, changed-route ESLint and the Next.js production build passed.
- Root Expo TypeScript and compatible local lint passed; 24 pre-existing lint warnings remain.
- On the newly connected Android 16 phone (model 2404ARN45A), debug installation and launch passed. Nine instrumented tests passed: 2x-font history navigation, dark-theme faith filtering, Quick Add routing, Home returning to the dashboard after viewing a report or switching tabs, three-page A4 rendering, selected-page printing, Keystore draft encryption, account-switch isolation and retained corrupt-draft handling. The synthetic PDF first page was visually inspected. UI fixtures keep the screen awake only while visible; the complete suite passed after unlocking the phone.
- The real app's Tasks, Habits, Goals, Profile and Home screens rendered during read-only navigation. No account save, deletion, AI generation, email/reset request or live schema deployment was performed.
- The later requested live-account check opened Explore, Challenges, Health, Coach, the report archive, History, reports and badges. History/reports/badges displayed load failures, and Coach could not refresh its conversations. An unauthenticated availability check confirmed HTTP 404 for the production /api/progress, /api/recognition and /api/ai/native routes. Live feature acceptance therefore remains incomplete. The report fallback was corrected so a missing report cannot be labeled as an offline saved copy.
- The same origin's homepage returned 200 and its existing chat/planning APIs returned the expected unauthenticated 401. The configured origin is valid. The connection callback now uses supplied capabilities, and the Home tab opens the dashboard instead of restoring its saved report child. Debug/release builds passed after these corrections. The final read-only phone check returned from History to the live dashboard with one Home tap.

Remaining external acceptance:

- Review/export the deployed Supabase schema, then apply the additive conversation-version, future-self reply, native-record and challenge-restart migrations in their documented dependency order.
- Deploy the updated backend and verify authenticated generation, RLS, bucket policies and two-device response-loss/conflict behavior using an approved test account. Live account changes were explicitly skipped by the user.
- Supply the existing release keystore, correct production versionCode and provider-console redirect configuration before signing/publishing or enabling Google OAuth. No production replacement key was generated.
- Complete a manual TalkBack audit and real delivery/share/print-destination acceptance beyond the automated fixture checks.

Local test logs/artifacts are ignored under android/.gradle-local. The SDK-path workaround used for sandbox validation is restored to the normal Android Studio SDK path after the final build. Later entries supersede historical milestone counts and availability notes above.

### 2026-10-10 - Production deployment and Android timestamp acceptance

The reviewed six-migration SQL transaction was applied to Supabase project `ckaigykceldybgknytwr`. Vercel cloud deployment `dpl_EVSMhXdtxg3iHMPBgRFEipDWzX5m` completed and was aliased to `https://nafs-one.vercel.app`. Ten production HTTP checks passed; the previously missing native routes now reject unauthenticated requests with HTTP 401.

The remaining Coach history failure was traced to Android timestamp validation after a successful database read. A shared `ServerTimestamp` parser uses `OffsetDateTime` for database timezone offsets, retains the original version strings and preserves microsecond comparisons. It is used by Coach, check-ins, evening verdict comparisons, saved-chat planning, letters and reports. Temporary diagnostic logging was removed; Room remains v14.

Validation: 271 unit tests across 42 classes and all 12 Android 16 phone tests passed. Debug/test assembly, lint (0 errors, 12 existing warnings) and unsigned R8 release APK/AAB assembly passed. Current artifacts are copied to `android/app/build/outputs`, and the corrected debug APK is installed. Read-only live checks passed for Coach conversations/reload, weekly and monthly reports with period selection confirmed, the 30-day history chart, badges/levels and the empty report archive. No AI was generated and no account record was changed. The phone was left on Home and its prior stay-awake setting was restored to `0`.

Remaining separate acceptance: authenticated AI/writes, managed Storage/RLS HTTP behavior, two-device conflicts, saved report bodies, manual accessibility/delivery/share/print checks, release signing/versionCode and Google OAuth provider configuration. Details and the recorded rollback target are in `output/deployment/PLAN.md`. This entry supersedes earlier production-pending and live-load-failure notes.

## Gemini backend integration (2026-10-10)

The shared backend now supports Gemini for all AI features and accurately saves provider/model metadata. Production deployment `dpl_GmJ4CkEF97HpEdLcEhXXXQcwnr47` is Ready; all ten production HTTP/authentication checks passed. Android accepts Gemini recovery responses and saved previews. Backend validation passed 478 tests, production build and lint; Android passed 272 unit tests, debug/test assembly and lint. The updated debug APK is installed. Google generation remains pending because the server key is not configured. The phone is securely locked, so a new UI acceptance run could not complete; earlier device evidence remains documented above. Existing unsigned release files predate this provider change and need rebuilding before publishing. Setup and remaining acceptance are in [PLAN.md](output/deployment/PLAN.md) and [AI.md](docs/AI.md).

Gemini activation is now configured: the sensitive Production key is present, `AI_PROVIDER=gemini` is selected, and deployment `dpl_HMbc8dtkxLZCgBBppKQgetRB86MV` is Ready after receiving those variables. Ten production HTTP checks passed. No key value was downloaded and no account AI generation was performed; authenticated Gemini acceptance remains pending.

The unavailable Coach report led to a model correction: Gemini 3.8 Flash timed out in cloud generation probes, while Gemini 3.5 Flash-Lite responded. Production now explicitly selects Flash-Lite, and deployment `dpl_F8ZdqGVLWWxzrDhiUngVJL7zEtWv` is Ready. Live synthetic calls through the real chat code and full recovery schema pass, as do all ten production HTTP checks and 478 backend tests. No account record was written by these probes; the user should retry the retained Coach request for account persistence acceptance.

### 2026-10-10 - Coach retained-message recovery and first authenticated Gemini reply

While a message is retained, Coach now replaces the composer with Retry message and Check for reply; after an online check, Edit message returns the text to the composer under a new request. Sends are bounded at 70 seconds and reloads at 30 seconds, so the screen cannot wait indefinitely. 275 unit tests across 42 classes, debug assembly and lint (0 errors) passed, and the build was installed over the existing app data. The message retained from the 09:51 UTC failure (HTTP 503 from Gemini 3.8 Flash) was retried on the phone: `POST /api/ai/chat` returned 200 at 10:27:53 UTC on deployment `dpl_F8ZdqGVLWWxzrDhiUngVJL7zEtWv` (Gemini 3.5 Flash-Lite), and the saved reply appeared with the composer unlocked. Duplicate-request replay and the other AI routes remain unverified with an account.

### 2026-10-10 - Authenticated Gemini acceptance on the owner's account

With the owner's approval, on the Android 16 phone against production (Gemini 3.5 Flash-Lite):

- Coach chat: a double-tapped Send produced exactly one `POST /api/ai/chat` (200 at 10:38:37 UTC). The saved reply cited the account's own goals and habits.
- Recovery-plan preview: a described setback produced `POST /api/ai/plan` 200 at 10:40:17 UTC and a two-step preview (a task and a daily habit) based on that day's records. No step was applied.
- Evening verdict: not run. The day's check-in had no priorities or notes, and none were invented for the owner's account.
- Future-self reply: not run. The account has no letters.

Remaining: evening verdict and letter reply with real owner content, server-side replay of an identical request ID, report and growth reviews, and the starter/health advice routes.
