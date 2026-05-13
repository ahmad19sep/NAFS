import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiText } from '@/lib/ai'
import { sendEmail, weeklyReportHTML, hasEmail } from '@/lib/email'

// Vercel Cron: `{ "path": "/api/cron/weekly-report", "schedule": "0 21 * * 0" }` (Sunday 9 PM)

interface User {
  id: string; name: string; email: string; timezone: string
  notify_email_weekly: boolean
  height_cm: number | null
}

function fmtDate(d: Date) { return d.toISOString().split('T')[0] }

export async function GET(req: NextRequest) {
  const authHeader = req.headers.get('authorization')
  const isCron = authHeader === `Bearer ${process.env.CRON_SECRET}`
  const isTest = req.nextUrl.searchParams.get('test') === '1'

  if (!isCron && !isTest) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  if (!hasEmail()) return NextResponse.json({ error: 'RESEND_API_KEY not set' }, { status: 500 })

  const supabase = createClient()

  const COLUMNS = 'id, name, email, timezone, notify_email_weekly, height_cm'
  let users: User[] | null = null
  if (isTest) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Test mode: sign in first' }, { status: 401 })
    const { data } = await supabase.from('users').select(COLUMNS).eq('id', user.id)
    users = data as any
  } else {
    const { data } = await supabase
      .from('users').select(COLUMNS)
      .eq('notify_email_weekly', true).not('email', 'is', null)
    users = data as any
  }
  if (!users?.length) return NextResponse.json({ ok: true, sent: 0 })

  let sent = 0
  for (const u of users) {
    try {
      const r = await buildAndSend(u, supabase, isTest)
      if (r) sent++
    } catch (err: any) {
      console.error('[weekly-report] failed for', u.id, err?.message)
    }
  }
  return NextResponse.json({ ok: true, sent, total: users.length })
}

const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

async function buildAndSend(u: User, supabase: any, isTest: boolean) {
  if (!u.email) return false
  const recipient = (isTest && process.env.RESEND_DEV_RECIPIENT) || u.email

  const now = new Date()
  const start = new Date(now); start.setDate(now.getDate() - 6)
  const startStr = fmtDate(start)
  const todayStr = fmtDate(now)

  // Pull 7 days of activity
  const [
    { data: prayerLogs },
    { data: habits },
    { data: habitLogs },
    { data: tasks },
    { data: challenges },
    { data: challengeCheckins },
    { data: healthLogs },
    { data: goals },
  ] = await Promise.all([
    supabase.from('prayer_logs').select('*').eq('user_id', u.id).gte('date', startStr),
    supabase.from('habits').select('id, name, current_streak, type, target_value, time_target_mins, is_paused')
      .eq('user_id', u.id).eq('is_active', true),
    supabase.from('habit_logs').select('habit_id, date, completed, value, duration_mins')
      .eq('user_id', u.id).gte('date', startStr),
    supabase.from('tasks').select('title, status, period_date, type')
      .eq('user_id', u.id).eq('type', 'daily').gte('period_date', startStr),
    supabase.from('challenges').select('id, title, current_streak, status').eq('user_id', u.id).eq('status', 'active'),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', startStr),
    supabase.from('health_logs').select('*').eq('user_id', u.id).gte('date', startStr),
    supabase.from('goals').select('title, ai_alignment, progress_pct').eq('user_id', u.id),
  ])

  // ----- Day-by-day score (matches dashboard logic) -----
  function dayScore(date: string): number {
    const dPrayer = (prayerLogs ?? []).find((p: any) => p.date === date)
    const prayerSum = dPrayer
      ? ['fajr','dhuhr','asr','maghrib','isha'].reduce((s, k) => s + Number(dPrayer[k] ?? 0), 0)
      : 0
    const dTasks = (tasks ?? []).filter((t: any) => t.period_date === date)
    const dTasksDone = dTasks.filter((t: any) => t.status === 'completed').length

    const dHabitLogs = (habitLogs ?? []).filter((l: any) => l.date === date)
    const scheduledHabits = (habits ?? []).filter((h: any) => !h.is_paused)
    const dHabitsDone = scheduledHabits.filter((h: any) => {
      const log = dHabitLogs.find((l: any) => l.habit_id === h.id)
      if (!log?.completed) return false
      if (h.type === 'counter')  return log.value >= h.target_value
      if (h.type === 'duration') return log.duration_mins >= h.time_target_mins
      return true
    }).length

    const dChCheckins = (challengeCheckins ?? []).filter((c: any) => c.date === date && c.completed)

    const dHealth = (healthLogs ?? []).find((h: any) => h.date === date)
    const healthFlags = dHealth ? [
      (dHealth.water_glasses ?? 0) > 0,
      !!dHealth.exercise_done,
      (dHealth.sleep_hours ?? 0) > 0,
      (dHealth.steps ?? 0) > 0,
    ] : []
    const dHealthDone = healthFlags.filter(Boolean).length

    const sections = [
      { earned: prayerSum, max: 10, w: dPrayer ? 1 : 0 },
      { earned: dTasksDone, max: Math.max(dTasks.length, 1), w: dTasks.length > 0 ? 1 : 0 },
      { earned: dHabitsDone, max: Math.max(scheduledHabits.length, 1), w: scheduledHabits.length > 0 ? 1 : 0 },
      { earned: dChCheckins.length, max: Math.max((challenges ?? []).length, 1), w: (challenges ?? []).length > 0 ? 1 : 0 },
      { earned: dHealthDone, max: 4, w: u.height_cm || dHealth ? 1 : 0 },
    ]
    const tw = sections.reduce((s, x) => s + x.w, 0)
    return tw > 0
      ? Math.round(sections.reduce((s, x) => s + (x.w * x.earned / x.max), 0) / tw * 100)
      : 0
  }

  const weekDays: { date: string; day: string; score: number }[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now); d.setDate(now.getDate() - i)
    const ds = fmtDate(d)
    weekDays.push({ date: ds, day: DAY_NAMES[d.getDay()], score: dayScore(ds) })
  }

  const validDays = weekDays.filter((d) => d.score > 0)
  const avg = validDays.length > 0
    ? Math.round(validDays.reduce((s, d) => s + d.score, 0) / validDays.length)
    : 0
  const best = weekDays.reduce((a, b) => a.score >= b.score ? a : b)
  const worst = weekDays.reduce((a, b) => a.score <= b.score ? a : b)

  // ----- Goal alignment summary -----
  const goalAlignments = ((goals ?? []) as any[])
    .filter((g) => g.ai_alignment?.score != null)
    .slice(0, 6)
    .map((g) => ({ title: g.title, score: g.ai_alignment.score }))

  // ----- AI tribunal verdict + recommendations -----
  const habitSummary = ((habits ?? []) as any[]).slice(0, 8).map((h: any) => {
    const done = (habitLogs ?? []).filter((l: any) => l.habit_id === h.id && l.completed).length
    return `${h.name}: ${done}/7 days, streak ${h.current_streak}`
  }).join('; ')

  const prayerTotal = (prayerLogs ?? []).reduce((s: number, p: any) =>
    s + ['fajr','dhuhr','asr','maghrib','isha'].reduce((a, k) => a + Number(p[k] ?? 0), 0), 0)

  const system = `You are NAFS — an honest, caring Muslim accountability coach.
Write a brutally honest weekly tribunal in 4-6 sentences. Cite real numbers.
Then on a new line, list 2-3 concrete recommendations for the coming week.

Format:
VERDICT: <4-6 sentence paragraph with real numbers>
NEXT WEEK:
- <action 1>
- <action 2>
- <action 3>`

  const prompt = `User: ${u.name}
Week: ${startStr} to ${todayStr}
Avg daily score: ${avg}%
Daily scores: ${weekDays.map((d) => `${d.day} ${d.score}%`).join(', ')}
Best day: ${best.day} (${best.score}%)
Worst day: ${worst.day} (${worst.score}%)
Prayers prayed this week: ${prayerTotal}/35
Habits: ${habitSummary || 'none'}
Active challenges: ${(challenges ?? []).length}
Active goals: ${(goals ?? []).length}`

  let verdictText = ''
  let recs: string[] = []
  try {
    const reply = await aiText('verdict', prompt, system)
    const vMatch = reply.match(/VERDICT:\s*([\s\S]*?)(?:NEXT WEEK:|$)/i)
    const rMatch = reply.match(/NEXT WEEK:?\s*([\s\S]+)$/i)
    verdictText = vMatch?.[1]?.trim() || reply.split('NEXT WEEK')[0].trim()
    if (rMatch) {
      recs = rMatch[1]
        .split('\n')
        .map((l) => l.replace(/^[-*•\d.)\s]+/, '').trim())
        .filter((l) => l.length > 0)
        .slice(0, 3)
    }
  } catch (e) {
    verdictText = `Your weekly average is ${avg}%. ${best.day} was your peak (${best.score}%), ${worst.day} was your low (${worst.score}%). Patterns reveal more than peaks.`
    recs = ['Identify what made your best day great — replicate it.', 'Pick one habit to harden.', 'Cut one source of friction.']
  }

  const weekLabel = `${start.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} – ${now.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`

  const html = weeklyReportHTML({
    name: u.name || 'friend',
    week_label: weekLabel,
    avg_score: avg,
    best_day: { day: best.day, score: best.score },
    worst_day: { day: worst.day, score: worst.score },
    verdict: verdictText,
    goal_alignments: goalAlignments,
    next_week: recs.length > 0 ? recs : ['Pick one focus and commit.'],
  })

  await sendEmail({
    to: recipient,
    subject: `${isTest ? '[TEST] ' : ''}Your NAFS week — ${avg}% avg`,
    html,
  })
  return true
}
