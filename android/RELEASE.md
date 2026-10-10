# Releasing NAFS for Android

A checklist for turning the code on `main` into an update people can install, and eventually a Play Store
listing. Signing and versioning details are in [README.md](README.md#release-signing).

## Every release

1. **Server first.** The app calls the `web-legacy` routes on `https://nafs-one.vercel.app`. Run the
   **Backend check** workflow (Actions tab) — every route must read `deployed`.
2. **Database migrations.** In the Supabase SQL editor, run any of these that haven't been applied yet, in
   this order (each is additive and safe to re-run):
   1. `supabase/app_notes.sql`
   2. `supabase/coach_conversation_versions.sql` — Coach chat history and versions
   3. `supabase/future_self_replies.sql` — replies to future-self letters
   4. `supabase/native_storage_policies.sql` — profile, challenge, screen-time and log photos
   5. `supabase/native_records.sql` — dreams, daily log, Qur'an journal, screen time, bugs & ideas
   6. `supabase/native_challenge_restart.sql` — restarting a challenge while keeping history
3. **Version.** Raise `versionCode` (and `versionName` when users would notice) in `app/build.gradle.kts`.
   Android refuses an update whose `versionCode` is not higher than the installed one.
4. **CI green.** The **Android** workflow must pass on the commit: unit tests, screenshots, debug APK,
   lint and the R8 release build. Review the `screenshots` artifact for both themes.
5. **Signed build.** On the machine that holds the keystore:
   `.\gradlew.bat bundleRelease` (Play Store) or `.\gradlew.bat assembleRelease` (direct install), with
   `NAFS_SIGNING_PROPERTIES_FILE` pointing at the private signing properties. Always sign with the same key,
   or existing installs can't update.
6. **Smoke test on a phone** (release build): sign in, sign out and sign in again; complete a task; log a
   habit; save a health entry; send a Coach message; turn on reminders and use **Send a test notification**;
   rotate the phone and switch light/dark.

## Supabase Auth settings

- Add `com.ahmad.nafs://auth/**` (and `com.ahmad.nafs.debug://auth/**` for test builds) to **Authentication →
  URL Configuration → Redirect URLs**. Password-reset emails link to `com.ahmad.nafs://auth/reset`, which opens
  the app's set-new-password dialog.
- Google sign-in is built but off. To turn it on, configure the Google provider in Supabase, then build with
  `NAFS_GOOGLE_AUTH_ENABLED=true`.

## Play Store listing

- **Package:** `com.ahmad.nafs`. Target SDK 36 meets the current Play requirement.
- **Privacy policy URL:** required because the app handles health, faith and account data. Use
  `https://nafs-one.vercel.app/privacy` (`web-legacy/src/app/privacy/page.tsx`, also linked from Profile → Privacy
  policy). Keep it in step with the data-safety answers below, and add a contact email before publishing.
- **Data safety** — what the app actually does:
  - Collected and linked to the account: name, email, user content (tasks, habits, goals, journals, coach
    chats), health and fitness data (sleep, water, steps, weight, meals, mood), religious practice (prayer
    logs, optional), photos the user chooses (avatar, challenge proof, logs).
  - Approximate location: only when the user asks for prayer times by location; rounded to about 1 km, not
    stored as a location history, never in the background.
  - Not collected: precise location, contacts, call logs, advertising ID. No ads, no third-party analytics.
  - Encrypted in transit (HTTPS only). Session and sensitive caches are encrypted on the device.
  - Users can delete their account and data in the app (Profile → Delete account).
- **Exact alarms:** the app asks the user to allow *Alarms & reminders* for on-time prayer and task alerts,
  and works (a little later) without it. Mention prayer-time alerts in the listing description.
- **Content:** screenshots from the `screenshots` CI artifact make good store images.

## Not included yet

- **Crash reporting.** There is none. Before a public launch, add Firebase Crashlytics or Sentry (needs a
  project and its config file/DSN from the owner).
- **Translations.** English only. Urdu would need string resources moved out of code and right-to-left
  checks.
