import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Privacy policy · NAFS',
  description: 'What NAFS collects, why, where it is kept, and how to delete it.',
}

// Public page (see middleware publicRoutes): the Play Store listing links here.
const UPDATED = '10 October 2026'

const sections: { title: string; body: string[] }[] = [
  {
    title: 'What NAFS is',
    body: [
      'NAFS is a personal accountability app for habits, tasks, goals, health and, if you choose, Islamic practice, with an AI coach. It is available as an Android app and on the web.',
    ],
  },
  {
    title: 'What we collect',
    body: [
      'Account: your name, email address, chosen gender, timezone and an optional profile photo.',
      'What you record: tasks, habits and their logs, goals and milestones, challenges and check-ins, daily check-ins, journals (dreams, daily log, Qur\'an journal, screen time, bugs and ideas), future-self letters and conversations with the coach.',
      'Health and wellbeing, only what you enter: sleep, water, steps, exercise, weight, height, meals, mood and notes.',
      'Faith, only if you turn on faith mode: prayer logs and related activities.',
      'Photos you choose to upload, such as challenge proof or log photos.',
      'Approximate location, only when you ask NAFS to find prayer times for your area. It is rounded to about 1 km, used to look up prayer times, and never collected in the background.',
    ],
  },
  {
    title: 'What we do not collect',
    body: [
      'No advertising identifiers, contacts, call logs or precise location. NAFS shows no ads and uses no third-party analytics or tracking.',
    ],
  },
  {
    title: 'How it is used',
    body: [
      'To store your records, calculate streaks, scores and reports, send the reminders you switch on, and, when you ask, generate coach replies, plans and reviews.',
      'Your data is not sold and is not used to train AI models by NAFS.',
    ],
  },
  {
    title: 'Where it is kept and who processes it',
    body: [
      'Supabase stores your account, records and uploaded photos. Vercel hosts the NAFS server.',
      'When you use AI features, the relevant context is sent to the AI provider configured for NAFS (Google Gemini, Cloudflare Workers AI or Anthropic Claude) to produce the reply. Requests go through the NAFS server; the app holds no AI keys. Each provider handles requests under its own terms.',
      'Prayer times are looked up from the public Aladhan service using the city or rounded area you choose.',
      'All traffic uses HTTPS. On Android, your session and sensitive cached data (health notes, prayers, journals) are encrypted with Android Keystore.',
    ],
  },
  {
    title: 'Your choices',
    body: [
      'Turn faith mode, reminders and notifications on or off at any time in Profile.',
      'Edit or delete individual records in the app.',
      'Delete your account in Profile → Delete account. This removes your sign-in, your records and your uploaded files from our systems.',
    ],
  },
  {
    title: 'Children',
    body: ['NAFS is not directed at children under 13.'],
  },
  {
    title: 'Changes and contact',
    body: [
      'If this policy changes, the date below changes with it. For questions or a data request, contact the developer through the email address on the NAFS store listing.',
    ],
  },
]

export default function PrivacyPage() {
  return (
    <main className="mx-auto max-w-2xl px-6 py-14 space-y-10">
      <header className="space-y-3">
        <p className="section-header">NAFS</p>
        <h1 className="text-3xl font-semibold text-foreground">Privacy policy</h1>
        <p className="text-sm text-muted-foreground">Last updated {UPDATED}</p>
      </header>
      {sections.map((section) => (
        <section key={section.title} className="space-y-3">
          <h2 className="text-lg font-semibold text-foreground">{section.title}</h2>
          {section.body.map((paragraph) => (
            <p key={paragraph} className="text-sm leading-relaxed text-muted-foreground">{paragraph}</p>
          ))}
        </section>
      ))}
    </main>
  )
}
