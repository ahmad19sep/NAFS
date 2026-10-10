# Publishing NAFS on Google Play

What is ready in the repository, and the steps only the account owner can take in Play Console.

## Ready

- A **signed release App Bundle** for `com.ahmad.nafs`:
  - Build it with `cd android && .\gradlew.bat bundleRelease -PNAFS_SIGNING_PROPERTIES_FILE=<path>`.
  - The output is `app/build/outputs/bundle/release/app-release.aab`.
- Listing text, graphics and release notes, in [LISTING.md](LISTING.md).
- A privacy policy at https://nafs-one.vercel.app/privacy.
- In-app and web account deletion (Profile → Delete account).

## The upload key: back it up

The release is signed with an **upload key**. It is stored outside the repository, next to its passwords file; the path is in `NAFS_SIGNING_PROPERTIES_FILE`.

- Never commit or share the key or its passwords.
- Keep two copies somewhere safe, for example an encrypted USB drive and a password manager.
- With **Play App Signing**, which is required for new apps, Google holds the key that users' devices check, so a lost upload key can be reset through Play support. Still, every update must be signed with the registered upload key.

## One-time Play Console setup (owner)

1. **Developer account.** Create one at https://play.google.com/console. There is a one-time US$25 fee and identity verification. Choose a personal or organization account.
2. **Create app.**
   - Name: `NAFS: Habits, Tasks & Prayer`
   - Default language: English (United States)
   - App, Free
   - Accept the declarations.
3. **Play App Signing.** Accept "Google manages and protects your app signing key". The first uploaded bundle registers the upload key.

## App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | `https://nafs-one.vercel.app/privacy` |
| Ads | No, the app contains no ads |
| App access | All or some functionality is restricted. Add **review login instructions**: a test account email and password you create for Google's reviewers. |
| Content rating | Fill in the questionnaire, category *Utility, Productivity, Communication or Other*: no violence, sexual content, profanity, drugs or gambling. Users can share content with the AI coach, but not with other users. |
| Target audience | 18 and over (or 13+). Not designed for children. |
| News app | No |
| Health apps | Declare health and fitness tracking: sleep, water, steps, weight, meals and mood. No medical diagnosis or treatment. |
| Government app / financial features | No |
| Data safety | See below |

### Data safety answers

- **Collected and shared?** Data is collected. It is *shared* only with processors acting for the app, which Play does not count as sharing:
  - Supabase stores account data.
  - The AI provider generates coach replies.
  - Aladhan calculates prayer times from the approximate location.
- **Encrypted in transit:** Yes, HTTPS only.
- **Account deletion:** Users can request deletion in the app and at https://nafs-one.vercel.app/privacy. To delete on the web: sign in at https://nafs-one.vercel.app, then go to Profile → Delete account.

Data types, all *collected*, *linked to the user*, *not used for ads*, and with the purpose **App functionality** (and **Account management** where it applies):

| Data type | Required or optional |
|---|---|
| Personal info: name, email address, user IDs | Required |
| Health and fitness: health info, fitness info | Optional |
| Photos | Optional: avatar, challenge proof, log photos |
| Approximate location | Optional, only for prayer times |
| App activity: other user-generated content (tasks, habits, goals, journals, coach chats) | Required for core features |
| Other: religious practice, prayer logs in faith mode | Optional. Play has no exact category; describe it under "Other user-generated content" |

Not collected: precise location, contacts, messages, audio, files, calendar, financial info, web history, device IDs, crash logs or diagnostics.

## Release: test first, then production

1. **Internal testing.** Go to Testing → Internal testing → Create release, upload `app-release.aab` and paste the release notes. Add your own email as a tester and install from the opt-in link.
2. **Closed testing (required for new personal accounts).** Google requires a closed test with a minimum number of testers (currently 12) who stay opted in for at least 14 days before you can apply for production. Check the current numbers in Play Console. Invite friends or family by email list or Google Group.
3. **Production.** After the closed test, apply for production access in the Dashboard, then promote the release. Review usually takes from a few hours to several days.

## Before each update

Raise `versionCode` in `app/build.gradle.kts`, rebuild with `bundleRelease`, and follow [RELEASE.md](../RELEASE.md).
