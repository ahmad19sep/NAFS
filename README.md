# NAFS — نَفْس

A personal accountability app: habits, tasks, goals, health, Islamic practice, and an AI coach.

**Platforms:** Android & iOS (native, via Expo)  
**Stack:** Expo SDK 52 · expo-router · NativeWind · Supabase · Gemini AI

---

## Architecture

```
NAFS/
├── app/                   # expo-router screens (file-based routing)
│   ├── _layout.tsx        # Root layout — auth guard, session init
│   ├── index.tsx          # Entry redirect
│   ├── auth.tsx           # Login / Sign-up / Forgot password
│   ├── onboarding.tsx     # Habit starter-pack selection
│   └── (app)/             # Protected tab group
│       ├── _layout.tsx    # Tab bar (Home, History, +, Coach, Profile)
│       ├── dashboard.tsx  # Home — prayers, habits, tasks, goals
│       ├── tasks.tsx      # Daily / Weekly / Monthly task CRUD
│       ├── habits.tsx     # Habit tracking with streak counters
│       ├── coach.tsx      # AI chat + AI reports
│       ├── profile.tsx    # User settings, sign out, delete account
│       ├── health.tsx     # Health logging (BMI, exercise, sleep)
│       ├── goals.tsx      # Goals with milestones + progress
│       ├── deen.tsx       # Prayer tracking + Quran log
│       ├── challenges.tsx # 21/30/90-day challenges
│       └── history.tsx    # Charts / trend analysis
├── components/            # Shared React Native components
├── lib/
│   ├── supabase.ts        # Native Supabase client (AsyncStorage sessions)
│   ├── api.ts             # HTTP client → Vercel backend (AI routes)
│   └── utils.ts           # Date helpers, formatting utilities
├── hooks/
│   └── useUser.ts         # Auth + profile hook
├── types/
│   └── database.ts        # Full Supabase TypeScript types
├── web-legacy/            # Archived Next.js web app (keep for AI backend)
│   ├── src/               # Next.js pages + API routes
│   └── public/            # PWA assets
├── app.json               # Expo configuration
├── eas.json               # EAS Build (APK preview + AAB production)
├── tailwind.config.js     # NativeWind colour palette (navy/teal/gold)
└── global.css             # NativeWind Tailwind directives
```

### Two-piece architecture

| Layer | Where it runs | What it does |
|---|---|---|
| **Native app** (this repo root) | Android / iOS via Expo | UI, local auth, direct Supabase queries |
| **Web backend** (`web-legacy/`) | Vercel | AI routes (Gemini/Groq), email reports, push cron |

The app calls Supabase directly for all data reads/writes, and calls `EXPO_PUBLIC_API_BASE_URL/api/...` for AI features that need server-side API keys.

---

## Quick start — test on your phone today

### 1. Prerequisites

```bash
node --version   # needs 20+
npm --version    # needs 9+
```

Install Expo CLI and EAS CLI:

```bash
npm install -g expo-cli eas-cli
```

Download **Expo Go** on your Android phone from the Play Store.

### 2. Clone & install

```bash
git clone https://github.com/ahmad19sep/NAFS.git
cd NAFS
npm install
```

### 3. Environment variables

```bash
cp .env.local.example .env.local
```

Edit `.env.local` — set at minimum:

```
EXPO_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
EXPO_PUBLIC_SUPABASE_ANON_KEY=YOUR_ANON_KEY
EXPO_PUBLIC_API_BASE_URL=https://your-nafs-app.vercel.app
```

Get Supabase keys from: **supabase.com → Project Settings → API**

### 4. Run the database schema

In Supabase SQL editor, run (in order):

```
supabase/schema.sql
supabase/tasks.sql
supabase/health.sql        (if exists)
supabase/schema_additions.sql
```

### 5. Add placeholder icons (required for Metro)

```bash
# Create the assets/ directory with placeholder images
# (replace with your real 1024×1024 PNG icons before building)
```

You need three PNG files:
- `assets/icon.png` — 1024×1024 app icon
- `assets/splash.png` — 1284×2778 splash screen  
- `assets/adaptive-icon.png` — 1024×1024 Android adaptive icon foreground

For quick testing, any PNG will work. You can copy an existing image and rename it.

### 6. Start the dev server

```bash
npx expo start
```

Metro bundler starts. You'll see a QR code in the terminal.

**Scan the QR code in Expo Go** (Android) or the Camera app (iOS) — the app loads on your phone instantly.

---

## Build an installable APK (Android)

This builds a real `.apk` you can install directly — no Play Store required.

### First time only

```bash
# Login to Expo / EAS (free account at expo.dev)
eas login

# Link this project to EAS
eas init
# This updates the projectId in app.json — commit the change
```

### Build the APK

```bash
npm run build:preview
# or explicitly:
eas build --platform android --profile preview
```

EAS builds in the cloud (no Android Studio needed). Takes ~5–10 minutes.  
When done, you'll get a download link for the `.apk` file. Install it on your phone.

### Production AAB (Play Store)

```bash
npm run build:prod-android
```

This produces an `.aab` (Android App Bundle) for uploading to Play Store.

---

## iOS builds

iOS requires:
- An Apple Developer account ($99/year at developer.apple.com)
- A Mac (or EAS cloud build, which works on any OS)

```bash
eas build --platform ios --profile production
```

You'll be prompted for your Apple credentials. EAS handles code signing automatically.

---

## Connecting the AI backend

The AI chat and analysis features call your Vercel-deployed backend.

1. Deploy the `web-legacy/` folder to Vercel as a separate project
2. Set all backend env vars in Vercel (see `web-legacy/package.json.bak` for the list)
3. Set `EXPO_PUBLIC_API_BASE_URL=https://your-project.vercel.app` in your `.env.local`

For local testing, run the Next.js backend locally:

```bash
cd web-legacy
npm install
npm run dev  # runs on http://localhost:3000
```

Then in the app's `.env.local`:
```
EXPO_PUBLIC_API_BASE_URL=http://192.168.x.x:3000
# Use your machine's local network IP (not localhost — phone can't reach localhost)
```

---

## Key decisions you may want to revisit

| Decision | Current choice | Alternative |
|---|---|---|
| AI provider | Google Gemini + Groq fallback (server-side) | Direct Gemini SDK in app (exposes key) |
| Backend | Next.js on Vercel (web-legacy/) | Supabase Edge Functions |
| Auth | Email + password | Add Google OAuth via expo-auth-session |
| Push notifications | Web Push (web-legacy backend) | expo-notifications (native, better UX) |
| Charts | Simple bar chart (custom) | victory-native for full charts |

---

## File structure of web-legacy/

The `web-legacy/` folder is the original Next.js app. It still serves as the **API backend** for AI features. Keep it deployed on Vercel. Do not delete it.

```
web-legacy/
├── src/app/api/         # 27 API routes (AI, notifications, cron, email)
├── src/lib/             # AI providers (Gemini, Groq), email (Resend)
└── package.json.bak     # Original web dependencies
```
