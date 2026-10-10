# Handoff note (for Codex or any next developer)

Branch: `claude/dreamy-thompson-7lahom` · Native Android app in `android/` (Kotlin, Compose, Hilt, Room, Supabase).

## State
- `68df1ad` is **green on CI**: unit tests passed and the debug APK built. The run was cancelled by the next push before lint and the R8 release build finished. It adds actionable notifications (Mark done, Snooze, Prayed, Habit done), per-habit reminders (`habits.reminder_time`), a morning plan and evening habit check, a Glance "NAFS Today" widget, launcher shortcuts, and photo EXIF/GPS stripping.
- `cf90849` (latest) is **unfinished work in progress and not yet compiled**: habits (archive, heatmap, focus timer), tasks/Home (reschedule, plan ahead, search), Deen (Hijri date, next prayer, Qibla), account (change password and email, timezone picker, data export), challenge fixes, and health/check-in/coach polish. The work was stopped early.

## First job
1. Push and open the latest **Android** workflow run (GitHub → Actions), read the "Unit tests and screenshots" log, and fix the compile errors.
   - `allWarningsAsErrors = true`, so deprecated APIs and unused variables fail the build.
2. Finish or trim the half-done features until CI is green. Screens use the Route + stateless `internal` screen pattern. Screenshot tests live in `app/src/test/.../screenshots/`, extending `ScreenshotTestBase`.
3. Room is at version 15 (`MIGRATION_14_15` adds `cached_habits.reminderTime`); check that it is registered in `core/di/StorageModule.kt`.

## Test on a phone
- Download the `nafs-debug-apk` artifact from a green CI run and install it. It installs as "NAFS Dev", next to any release build.
- Or build locally: `cd android && ./gradlew :app:installDebug`. This needs `NAFS_SUPABASE_URL` and `NAFS_SUPABASE_PUBLISHABLE_KEY` as Gradle properties.
- Smoke test:
  - sign in / sign out
  - add a task with a due time and set Profile → Reminders lead time to 10 min, then use **Mark done** / **Snooze** on the alert
  - set a reminder time on a habit → **Done** button
  - with faith mode on, the prayer alert → **Prayed**
  - add the "NAFS Today" widget
  - long-press the app icon for shortcuts
  - upload a photo, then check that it has no GPS data
  - "Send a test notification" in Profile
- On Xiaomi, Oppo, Vivo and Samsung, allow "Alarms & reminders" and turn off battery restrictions for on-time alerts.

## Owner setup (Supabase)
- Run the migrations listed in `android/RELEASE.md`.
- Add `com.ahmad.nafs://auth/**` and `com.ahmad.nafs.debug://auth/**` to the Auth redirect URLs.
