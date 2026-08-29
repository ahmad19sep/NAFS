import type { CapacitorConfig } from '@capacitor/cli'

// NAFS native shell (Android / iOS).
//
// The native app is a thin shell whose webview loads the deployed Vercel app —
// the same code, server features (API routes, AI, cron) and database as the
// web/PWA version. No static export needed; ship a new version by deploying
// to Vercel, no app rebuild required.
const config: CapacitorConfig = {
  appId: 'com.ahmad.nafs',
  appName: 'Ascend',
  webDir: 'www', // offline fallback shell only — real app comes from server.url
  server: {
    url: 'https://nafs-one.vercel.app',
    cleartext: false,
  },
  backgroundColor: '#0b1a2b',
  android: {
    allowMixedContent: false,
  },
  ios: {
    contentInset: 'automatic',
  },
}

export default config
