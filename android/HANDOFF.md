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

## What's left (not done or not finished)
All of the items below were started in `cf90849` and stopped partway unless marked "not started". Check each one in code before trusting it.

**Habits**
- The reminder time field in the habit editor (time picker, bell on the card). `Habit.reminderTime` exists.
  - Check that the Supabase select/insert/update include `reminder_time` and that the Room entity and migration are complete.
- HabitViewModel must call `HabitReminderScheduler.reconcile(user, habits, zone)` after online loads and saves.
- Archive and restore (`is_active = false`) with Undo.
- Heatmap history with backfill by tapping a past day.
- Focus timer for duration habits.
- Quick +1 for counters.
- "x of N this week" for per-week habits.
- Allow parallel saves of different habits.
- Write `current_streak` / `longest_streak` back after logging.

**Tasks and Home**
- "Move to today / tomorrow" for missed tasks, with a distinct missed style.
- Plan ahead (date picker) and an Upcoming section.
- Sort completed tasks to the bottom.
- Search.
- Complete tasks and simple habits from Home.
- Real Challenges tile on Home.
- 12-hour time in "Next up".
- Celebration when everything is done (`NafsCelebration`).
- Pull-to-refresh.
- Ignore duplicate loads.
- Put `NextPrayerCard` and the Hijri date on Home.

**Deen**
- Hijri date (adjustable ±1 day), the next-prayer card, and a countdown to tomorrow's Fajr after Isha. Started in `core/deen/HijriDate.kt`, `NextPrayer.kt` and `ui/deen/NextPrayerCard.kt`.
- Prayer streak, 30-day heatmap and per-prayer stats (`DeenRepository.history`).
- Logging yesterday's prayers.
- Qibla screen. `ui/deen/QiblaScreen.kt` exists, but the `qibla` route in `ui/NafsApp.kt` and its tile in `ui/LibraryScreen.kt` are missing.
- Tasbih screen and its `tasbih` route — not started.
- Default calculation method by region.
- Counter-type extras (web parity).

**Account**
- Change password and change email.
- Resend the confirmation email.
- Sign-up minimum of 8 characters.
- Searchable timezone picker, and saving the device timezone at onboarding.
- Remove profile photo.
- Handle the sign-out result after deleting an account.
- "Download my data" JSON export, which needs an `exports/` path in `res/xml/share_paths.xml`.
- AuthCallbackController on an app scope with try/finally.
- Account group on the Profile screen.

**Challenges**
- A missed final period must not complete the challenge.
- Auto-resolve challenges past their end date.
- Compute the streak from check-ins.
- Period-based restart window.
- Check-in history with proof photos.
- Check-in for yesterday.

**Health, Check-in, Coach, Letters**
- Water, steps and sleep goals.
- Saving a day's weight should update `users.weight_kg`.
- 7- and 30-day trends.
- Editing past days.
- Logging without a height/weight baseline.
- Check-in draft autosave and voice dictation.
- Coach copy, share and voice input.
- Letter date picker.
- The unit tests passed partway through; the screenshot tests were being updated.

**Not started**
- App lock (biometric) and hiding the app in Recents. Needs `androidx.biometric` and MainActivity extending FragmentActivity.
- Crash reporting (Crashlytics or Sentry; needs the owner's project).
- Private storage buckets with signed URLs for journal and challenge photos (backend).
- An offline write queue for tasks, habit logs, prayers and check-ins. A `pending_mutations` table exists but is unused.
- Translations (Urdu).
- Opening cached data when a session refresh fails offline.
- Commit the generated Room schema `app/schemas/.../15.json` once CI builds.

**Notification work in `68df1ad` that needs a device test**
- Exact alarms after a reboot.
- Notification buttons while offline: they should retry and show "Not saved yet" if they still fail.
- The widget's tap actions, which open via Glance `actionStartActivity` with the destination extra.
- Habit reminders for per-week habits.

Tip: if a half-done feature blocks a green build, revert that file to `68df1ad` (`git checkout 68df1ad -- <file>`) and do it again properly.
