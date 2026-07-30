import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiText } from '@/lib/ai'
import { sendEmail, dailyReportHTML, hasEmail } from '@/lib/email'

// Vercel Cron: `{ "path": "/api/cron/daily-report", "schedule": "0 21 * * *" }` (9 PM daily)

interface User {
  id: string
  name: string
  email: string
  timezone: string
  notify_email_daily: boolean
  height_cm: number | null
  weight_kg: number | null
}

function todayString(): string { return new Date().toISOString().split('T')[0] }
function yesterdayString(): string {
  const d = new Date(); d.setDate(d.getDate() - 1); return d.toISOString().split('T')[0]
}

export async function GET(req: NextRequest) {
  // Allow either Vercel cron auth or manual trigger from settings (?test=1 with a logged-in user)
  const authHeader = req.headers.get('authorization')
  const isCron = authHeader === `Bearer ${process.env.CRON_SECRET}`
  const isTest = req.nextUrl.searchParams.get('test') === '1'

  if (!isCron && !isTest) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  if (!hasEmail()) return NextResponse.json({ error: 'RESEND_API_KEY not set' }, { status: 500 })

  const supabase = createClient()
  const today = todayString()
  const yest = yesterdayString()

  // In test mode, only send to the currently signed-in user
  const COLUMNS = 'id, name, email, timezone, notify_email_daily, height_cm, weight_kg'
  let users: User[] | null = null
  if (isTest) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Test mode: sign in first' }, { status: 401 })
    const { data } = await supabase.from('users').select(COLUMNS).eq('id', user.id)
    users = data as any
  } else {
    const { data } = await supabase
      .from('users').select(COLUMNS)
      .eq('notify_email_daily', true).not('email', 'is', null)
    users = data as any
  }
  if (!users?.length) return NextResponse.json({ ok: true, sent: 0 })

  let sent = 0
  for (const u of users) {
    try {
      const r = await buildAndSend(u, today, yest, supabase, isTest)
      if (r) sent++
    } catch (err: any) {
      console.error('[daily-report] failed for', u.id, err?.message)
    }
  }

  return NextResponse.json({ ok: true, sent, total: users.length })
}

async function buildAndSend(u: User, today: string, yest: string, supabase: any, isTest: boolean) {
  if (!u.email) return false
  // In test mode, allow overriding the recipient (Resend free tier only sends
  // to your own verified email until you verify a domain).
  const recipient = (isTest && process.env.RESEND_DEV_RECIPIENT) || u.email

  // Pull today's snapshot
  const [
    { data: prayer },
    { data: habits },
    { data: habitLogs },
    { data: tasks },
    { data: challengeCheckins },
    { data: health },
    { data: yesterdayPrayer },
    { data: yesterdayHabitLogs },
    { data: yesterdayTasks },
    { data: yesterdayHealth },
  ] = await Promise.all([
    supabase.from('prayer_logs').select('*').eq('user_id', u.id).eq('date', today).maybeSingle(),
    supabase.from('habits').select('id, name, current_streak, type, target_value, time_target_mins, is_paused, schedule_kind, schedule_days')
      .eq('user_id', u.id).eq('is_active', true),
    supabase.from('habit_logs').select('habit_id, completed, value, duration_mins').eq('user_id', u.id).eq('date', today),
    supabase.from('tasks').select('title, status').eq('user_id', u.id).eq('type', 'daily').eq('period_date', today),
    supabase.from('challenge_checkins').select('completed').eq('date', today),
    supabase.from('health_logs').select('*').eq('user_id', u.id).eq('date', today).maybeSingle(),
    supabase.from('prayer_logs').select('*').eq('user_id', u.id).eq('date', yest).maybeSingle(),
    supabase.from('habit_logs').select('habit_id, completed').eq('user_id', u.id).eq('date', yest),
    supabase.from('tasks').select('status').eq('user_id', u.id).eq('type', 'daily').eq('period_date', yest),
    supabase.from('health_logs').select('water_glasses, steps, exercise_done, sleep_hours').eq('user_id', u.id).eq('date', yest).maybeSingle(),
  ])

  // ----- Compute today's stats -----
  const prayerDone = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha']
    .reduce((s, k) => s + Number((prayer as any)?.[k] ?? 0), 0)
  const prayerMax = 10
  const prayerEngaged = !!prayer

  const tasksTotal = tasks?.length ?? 0
  const tasksDone  = tasks?.filter((t: any) => t.status === 'completed').length ?? 0
  const tasksEngaged = tasksTotal > 0

  const scheduledHabits = (habits ?? []).filter((h: any) => !h.is_paused)
  const habitsTotal = scheduledHabits.length
  const habitsDone = scheduledHabits.filter((h: any) => {
    const log = (habitLogs ?? []).find((l: any) => l.habit_id === h.id)
    if (!log?.completed) return false
    if (h.type === 'counter')  return log.value >= h.target_value
    if (h.type === 'duration') return log.duration_mins >= h.time_target_mins
    return true
  }).length
  const habitsEngaged = habitsTotal > 0

  const challengesTotal = challengeCheckins?.length ?? 0
  const challengesDone  = challengeCheckins?.filter((c: any) => c.completed).length ?? 0

  const healthFlags = [
    (health?.water_glasses ?? 0) > 0,
    !!health?.exercise_done,
    (health?.sleep_hours ?? 0) > 0,
    (health?.steps ?? 0) > 0,
  ]
  const healthDone = healthFlags.filter(Boolean).length
  const healthTotal = 4
  const healthEngaged = !!u.height_cm || !!health

  // ----- Score (engagement-aware, matches dashboard) -----
  const sections = [
    { earned: prayerDone, max: prayerMax, weight: prayerEngaged ? 1 : 0 },
    { earned: tasksDone, max: Math.max(tasksTotal, 1), weight: tasksEngaged ? 1 : 0 },
    { earned: habitsDone, max: Math.max(habitsTotal, 1), weight: habitsEngaged ? 1 : 0 },
    { earned: challengesDone, max: Math.max(challengesTotal, 1), weight: challengesTotal > 0 ? 1 : 0 },
    { earned: healthDone, max: healthTotal, weight: healthEngaged ? 1 : 0 },
  ]
  const totalWeight = sections.reduce((s, x) => s + x.weight, 0)
  const score = totalWeight > 0
    ? Math.round(sections.reduce((s, x) => s + (x.weight * x.earned / x.max), 0) / totalWeight * 100)
    : 0

  // Yesterday score (for delta)
  const yPrayer = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha']
    .reduce((s, k) => s + Number((yesterdayPrayer as any)?.[k] ?? 0), 0)
  const yTasksDone = yesterdayTasks?.filter((t: any) => t.status === 'completed').length ?? 0
  const yTasksTotal = yesterdayTasks?.length ?? 0
  const yHabitsDone = (yesterdayHabitLogs ?? []).filter((l: any) => l.completed).length
  const yHealthFlags = [
    (yesterdayHealth?.water_glasses ?? 0) > 0,
    !!yesterdayHealth?.exercise_done,
    (yesterdayHealth?.sleep_hours ?? 0) > 0,
    (yesterdayHealth?.steps ?? 0) > 0,
  ]
  const yHealthDone = yHealthFlags.filter(Boolean).length

  const ySections = [
    { earned: yPrayer, max: 10, weight: yesterdayPrayer ? 1 : 0 },
    { earned: yTasksDone, max: Math.max(yTasksTotal, 1), weight: yTasksTotal > 0 ? 1 : 0 },
    { earned: yHabitsDone, max: Math.max(habitsTotal, 1), weight: habitsTotal > 0 ? 1 : 0 },
    { earned: yHealthDone, max: 4, weight: u.height_cm || yesterdayHealth ? 1 : 0 },
  ]
  const yTotalWeight = ySections.reduce((s, x) => s + x.weight, 0)
  const yScore = yTotalWeight > 0
    ? Math.round(ySections.reduce((s, x) => s + (x.weight * x.earned / x.max), 0) / yTotalWeight * 100)
    : 0
  const delta = yTotalWeight > 0 ? score - yScore : null

  // ----- AI verdict + tomorrow tip -----
  const system = `You are NAFS — an honest, caring Muslim accountability coach.
Write a 2–3 sentence verdict on today, citing real numbers. Then in a new line write
a single concrete focus for tomorrow. Format:

VERDICT: <2–3 sentences>
TOMORROW: <one specific action>

Be direct. Don't sugarcoat. Don't preach.`

  const prompt = `User: ${u.name}
Today's score: ${score}% (yesterday: ${yScore}%)
Prayers: ${prayerDone}/${prayerMax}
Tasks: ${tasksDone}/${tasksTotal}
Habits: ${habitsDone}/${habitsTotal}
Challenges: ${challengesDone}/${challengesTotal}
Health metrics logged: ${healthDone}/${healthTotal}`

  let verdictText = ''
  let tomorrowText = ''
  try {
    const reply = await aiText('verdict', prompt, system)
    const vMatch = reply.match(/VERDICT:\s*([\s\S]*?)(?:\n|$)(?=TOMORROW:|$)/i)
    const tMatch = reply.match(/TOMORROW:\s*([\s\S]+)$/i)
    verdictText = vMatch?.[1]?.trim() || reply.split('TOMORROW:')[0].trim()
    tomorrowText = tMatch?.[1]?.trim() || 'Pick one small win and get it done before noon.'
  } catch (e) {
    verdictText = score >= 70 ? 'Solid day. Keep the rhythm.' : score >= 40 ? 'Mixed day — finish stronger.' : 'Tough day. Reset overnight, restart fresh.'
    tomorrowText = 'Pick one small win and get it done before noon.'
  }

  // Pick stat rows that have any data
  const stats = [
    prayerEngaged ? { emoji: '🕌', label: 'Prayers',    earned: prayerDone,    max: prayerMax } : null,
    tasksEngaged ? { emoji: '✅', label: 'Tasks',       earned: tasksDone,     max: tasksTotal } : null,
    habitsEngaged ? { emoji: '🔄', label: 'Habits',     earned: habitsDone,    max: habitsTotal } : null,
    challengesTotal > 0 ? { emoji: '🎯', label: 'Challenges', earned: challengesDone, max: challengesTotal } : null,
    healthEngaged ? { emoji: '❤️', label: 'Health',     earned: healthDone,    max: healthTotal } : null,
  ].filter(Boolean) as { emoji: string; label: string; earned: number; max: number }[]

  const html = dailyReportHTML({
    name: u.name || 'friend',
    date: today,
    score,
    delta,
    verdict: verdictText,
    stats,
    tomorrow: tomorrowText,
  })

  await sendEmail({
    to: recipient,
    subject: `${isTest ? '[TEST] ' : ''}Your NAFS daily verdict — ${score}%`,
    html,
  })
  return true
}
