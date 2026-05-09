# NAFS Setup Guide

## 1. Get your API keys (takes ~5 minutes)

### Supabase (database + auth + storage)
1. Go to supabase.com → New project → Free tier
2. Copy from Project Settings > API:
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
3. Go to SQL Editor → paste contents of `supabase/schema.sql` → Run
4. Go to Storage → Create two buckets:
   - `dream-images` (public)
   - `log-photos` (public)
5. Go to Authentication → Providers → Enable Google OAuth
   (requires Google Cloud Console OAuth2 credentials)

### Gemini (AI engine — free)
1. Go to aistudio.google.com → Get API Key → Create API key
2. Copy the key → `GEMINI_API_KEY`

### Web Push VAPID keys
Run: `npx web-push generate-vapid-keys`
Copy the output into `NEXT_PUBLIC_VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY`

## 2. Create .env.local
Copy `.env.local.example` to `.env.local` and fill in your values:
```
cp .env.local.example .env.local
```

## 3. Run locally
```bash
npm install
npm run dev
```

Open http://localhost:3000

## 4. Add to Home Screen on iPhone
1. Open http://localhost:3000 in Safari
2. Tap Share → Add to Home Screen
3. Done — it works like a native app

## 5. Deploy to Vercel
```bash
npm i -g vercel
vercel
```
Then add all env vars in Vercel dashboard → Settings → Environment Variables.

## 6. Cron job (Sunday Tribunal)
The `vercel.json` already configures the cron.
Add `CRON_SECRET=<any-random-string>` to your Vercel env vars.

## Architecture
- Next.js 14 App Router + TypeScript
- Supabase (Postgres + Auth + Storage)
- Gemini 2.0 Flash (free tier)
- PWA (works on iOS 16.4+ as native app)
- Vercel (free hosting + cron)
- Total monthly cost: $0
