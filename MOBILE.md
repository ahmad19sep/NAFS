# NAFS on Android & iOS

The native apps are thin Capacitor shells: the app you see is loaded live from
**https://nafs-one.vercel.app** — same code, same database, same AI as the web app.

**This means: to update the app, just `git push` (Vercel deploys it). You never
need to rebuild the APK unless you change the icon, app name, or Capacitor config.**

---

## Android (works on Windows)

### One-time setup
1. Install [Android Studio](https://developer.android.com/studio).
2. Open Android Studio → **Open** → select the `android/` folder in this repo.
3. Let Gradle sync finish (first time takes a few minutes).

### Run on your phone (USB)
1. On your phone: Settings → About → tap **Build number** 7× → enable **Developer options** → turn on **USB debugging**.
2. Plug the phone in, accept the prompt.
3. In Android Studio press the green **▶ Run** button — NAFS installs and opens.

### Build an installable APK (share/sideload)
1. Android Studio → **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
2. The APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`.
3. Copy it to any Android phone and open it to install.

> For Play Store later: Build → Generate Signed App Bundle (needs a keystore + $25 Play account).

---

## iPhone (no Mac needed right now)

Two options:

1. **PWA (works today):** open https://nafs-one.vercel.app in Safari → Share →
   **Add to Home Screen**. Full-screen app icon, push notifications on iOS 16.4+.
2. **Real iOS app via cloud build:** the `ios/` project is ready. Sign up at
   [codemagic.io](https://codemagic.io) (free tier), connect the GitHub repo,
   pick the Capacitor iOS workflow. Note: installing on your own iPhone requires
   an Apple Developer account ($99/yr) — until then the PWA is the practical choice.

---

## Things to know

- **Login in the native app:** use **email + password**. Google OAuth inside a
  webview can be blocked by Google ("disallowed user agent").
- **Push notifications:** web push works in the browser/PWA. Inside the native
  Android shell, push needs Firebase Cloud Messaging (a future upgrade —
  `@capacitor/push-notifications`).
- **Offline:** the shell shows a simple "you're offline" page (`www/index.html`)
  if there's no internet at launch.

## Useful commands

```bash
npm run cap:sync       # re-sync config/icons into android/ and ios/
npm run cap:android    # open the Android project in Android Studio
npx capacitor-assets generate --android --ios   # regenerate icons from assets/
```

App identity: `com.ahmad.nafs` — configured in `capacitor.config.ts`.
