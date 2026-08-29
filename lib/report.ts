import { supabase } from '@/lib/supabase'
import type {
  HabitRow, HabitLogRow, PrayerLogRow, TaskRow,
  GoalRow, ChallengeRow, HealthLogRow, QuranLogRow,
} from '@/types/database'

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

export type ReportPeriod = 'weekly' | 'monthly'

const PRAYERS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'] as const
export type PrayerKey = typeof PRAYERS[number]

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

export interface DayRow {
  date: string
  weekday: string
  logged: boolean
  score: number
  prayer_points: number      // 0–10 (per prayer: 0 missed / 1 alone / 2 jamaat)
  prayers_prayed: number     // 0–5
  prayers_jamat: number      // 0–5
  extra_prayers: number
  habits_done: number
  habits_total: number
  tasks_done: number
  tasks_total: number
  quran_pages: number
  sleep_hours: number | null
  steps: number | null
  water_glasses: number
  exercise_done: boolean
  exercise_minutes: number
  mood: number | null
  weight_kg: number | null
  reflection: string | null
}

export interface HabitStat {
  id: string
  name: string
  emoji: string
  type: HabitRow['type']
  unit: string
  done_days: number
  eligible_days: number
  pct: number
  total_value: number
  total_minutes: number
  current_streak: number
  longest_streak: number
  best_run: number           // longest run of done days inside this period
}

export interface PrayerStat {
  key: PrayerKey
  label: string
  prayed: number
  jamat: number
  missed: number
  pct: number
}

export interface TaskStat {
  type: TaskRow['type']
  total: number
  completed: number
  pct: number
}

export interface ChallengeStat {
  id: string
  title: string
  emoji: string
  status: ChallengeRow['status']
  checkins: number
  possible_days: number
  pct: number
  current_streak: number
  longest_streak: number
  day_of: number             // day N of duration_days
  duration_days: number
}

export interface GoalStat {
  id: string
  title: string
  emoji: string
  progress_pct: number
  deadline: string | null
  days_left: number | null
  milestones_done: number
  milestones_total: number
}

export interface HealthSummary {
  days_logged: number
  avg_sleep: number | null
  avg_steps: number | null
  total_steps: number
  avg_water: number | null
  exercise_days: number
  exercise_minutes: number
  avg_mood: number | null
  weight_start: number | null
  weight_end: number | null
  weight_change: number | null
}

export interface ReportData {
  period: ReportPeriod
  label: string              // "24 – 30 Aug 2026" / "August 2026"
  short_label: string        // "Week of 24 Aug" / "August 2026"
  start: string
  end: string
  generated_at: string
  user: { name: string; email: string }

  days: DayRow[]             // only days up to today
  days_in_period: number
  days_elapsed: number
  days_logged: number

  avg_score: number
  prev_avg_score: number | null
  score_delta: number | null
  best_day: DayRow | null
  worst_day: DayRow | null
  strong_days: number        // score ≥ 75
  weak_days: number          // score < 50
  best_streak: number        // longest run of days scoring ≥ 60

  prayer_points: number
  prayer_points_max: number
  prayers_prayed: number
  prayers_possible: number
  prayers_jamat: number
  extra_prayers: number
  prayer_breakdown: PrayerStat[]

  quran_pages: number
  quran_days: number

  habits: HabitStat[]
  habit_completion_pct: number

  tasks: TaskStat[]
  tasks_total: number
  tasks_completed: number
  tasks_pct: number
  missed_tasks: { title: string; date: string; priority: TaskRow['priority']; type: TaskRow['type'] }[]

  challenges: ChallengeStat[]
  goals: GoalStat[]
  health: HealthSummary

  weekday_avgs: { weekday: string; avg: number; days: number }[]
  reflections: { date: string; text: string }[]
  insights: string[]
}

// ─────────────────────────────────────────────────────────────────────────────
// Date helpers (all local-time, so no UTC drift)
// ─────────────────────────────────────────────────────────────────────────────

function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function parseLocal(dateStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function shiftDays(dateStr: string, n: number): string {
  const d = parseLocal(dateStr)
  d.setDate(d.getDate() + n)
  return fmt(d)
}

export function prettyDate(dateStr: string, opts?: Intl.DateTimeFormatOptions): string {
  return parseLocal(dateStr).toLocaleDateString('en-GB', opts ?? { day: 'numeric', month: 'short' })
}

/** Range for a period, `offset` periods back from the current one (0 = current). */
export function periodRange(period: ReportPeriod, offset: number) {
  const today = fmt(new Date())
  if (period === 'weekly') {
    const t = parseLocal(today)
    const dow = t.getDay()                        // 0 = Sun
    const toMonday = dow === 0 ? -6 : 1 - dow
    const start = shiftDays(today, toMonday - offset * 7)
    return { start, end: shiftDays(start, 6), today }
  }
  const t = parseLocal(today)
  const first = new Date(t.getFullYear(), t.getMonth() - offset, 1)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)
  return { start: fmt(first), end: fmt(last), today }
}

export function periodLabel(period: ReportPeriod, start: string, end: string) {
  const s = parseLocal(start)
  const e = parseLocal(end)
  if (period === 'monthly') {
    const l = s.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
    return { label: l, short_label: l }
  }
  const sameMonth = s.getMonth() === e.getMonth()
  const left = s.toLocaleDateString('en-GB', sameMonth ? { day: 'numeric' } : { day: 'numeric', month: 'short' })
  const right = e.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
  return {
    label: `${left} – ${right}`,
    short_label: `Week of ${s.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}`,
  }
}

function eachDay(start: string, end: string): string[] {
  const out: string[] = []
  let cur = start
  for (let i = 0; i < 400 && cur <= end; i++) {   // guard against bad ranges
    out.push(cur)
    cur = shiftDays(cur, 1)
  }
  return out
}

function avg(nums: number[]): number | null {
  if (nums.length === 0) return null
  return nums.reduce((s, n) => s + n, 0) / nums.length
}

function round(n: number | null, dp = 1): number | null {
  if (n == null) return null
  const f = Math.pow(10, dp)
  return Math.round(n * f) / f
}

function habitDone(h: HabitRow, log: HabitLogRow | undefined): boolean {
  if (!log?.completed) return false
  if (h.type === 'count') return Number(log.value ?? 0) >= Number(h.target_value ?? 1)
  if (h.type === 'duration') return Number(log.duration_mins ?? 0) >= Number(h.time_target_mins ?? 0)
  return true
}

function longestRun(flags: boolean[]): number {
  let best = 0, cur = 0
  for (const f of flags) {
    cur = f ? cur + 1 : 0
    if (cur > best) best = cur
  }
  return best
}

// ─────────────────────────────────────────────────────────────────────────────
// Raw fetch
// ─────────────────────────────────────────────────────────────────────────────

interface RawData {
  habits: HabitRow[]
  habitLogs: HabitLogRow[]
  prayers: PrayerLogRow[]
  tasks: TaskRow[]
  health: HealthLogRow[]
  quran: QuranLogRow[]
  checkins: { date: string; evening_text: string | null }[]
  challenges: (ChallengeRow & { checkins: { date: string; completed: boolean }[] })[]
  goals: (GoalRow & { goal_milestones: { done: boolean }[] })[]
}

async function fetchRange(userId: string, start: string, end: string): Promise<RawData> {
  const [
    habitsRes, habitLogsRes, prayersRes, tasksRes,
    healthRes, quranRes, checkinsRes, challengesRes, goalsRes,
  ] = await Promise.all([
    supabase.from('habits').select('*').eq('user_id', userId).eq('is_active', true).order('sort_order'),
    supabase.from('habit_logs').select('*').eq('user_id', userId).gte('date', start).lte('date', end),
    supabase.from('prayer_logs').select('*').eq('user_id', userId).gte('date', start).lte('date', end),
    supabase.from('tasks').select('*').eq('user_id', userId).gte('period_date', start).lte('period_date', end),
    supabase.from('health_logs').select('*').eq('user_id', userId).gte('date', start).lte('date', end),
    supabase.from('quran_log').select('*').eq('user_id', userId).gte('date', start).lte('date', end),
    supabase.from('daily_checkins').select('date,evening_text').eq('user_id', userId).gte('date', start).lte('date', end),
    supabase.from('challenges').select('*').eq('user_id', userId),
    supabase.from('goals').select('*, goal_milestones(done)').eq('user_id', userId),
  ])

  const challenges = (challengesRes.data ?? []) as ChallengeRow[]
  let checkinRows: { challenge_id: string; date: string; completed: boolean }[] = []
  if (challenges.length > 0) {
    const { data } = await supabase
      .from('challenge_checkins')
      .select('challenge_id,date,completed')
      .in('challenge_id', challenges.map(c => c.id))
      .gte('date', start).lte('date', end)
    checkinRows = data ?? []
  }

  return {
    habits: (habitsRes.data ?? []) as HabitRow[],
    habitLogs: (habitLogsRes.data ?? []) as HabitLogRow[],
    prayers: (prayersRes.data ?? []) as PrayerLogRow[],
    tasks: (tasksRes.data ?? []) as TaskRow[],
    health: (healthRes.data ?? []) as HealthLogRow[],
    quran: (quranRes.data ?? []) as QuranLogRow[],
    checkins: (checkinsRes.data ?? []) as { date: string; evening_text: string | null }[],
    challenges: challenges.map(c => ({
      ...c,
      checkins: checkinRows.filter(r => r.challenge_id === c.id),
    })),
    goals: (goalsRes.data ?? []) as RawData['goals'],
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scoring — same shape the dashboard uses: every section that has something to
// measure on a given day contributes equally to that day's score.
// ─────────────────────────────────────────────────────────────────────────────

function scoreDay(date: string, raw: RawData, activeChallenges: number): { score: number; logged: boolean } {
  const prayer = raw.prayers.find(p => p.date === date)
  const prayerPoints = prayer
    ? PRAYERS.reduce((s, k) => s + Number((prayer as any)[k] ?? 0), 0)
    : 0

  const dayTasks = raw.tasks.filter(t => t.type === 'daily' && t.period_date === date)
  const tasksDone = dayTasks.filter(t => t.status === 'completed').length

  const dayLogs = raw.habitLogs.filter(l => l.date === date)
  const eligible = raw.habits.filter(h => (h.created_at ?? '').slice(0, 10) <= date)
  const habitsDone = eligible.filter(h => habitDone(h, dayLogs.find(l => l.habit_id === h.id))).length

  const chDone = raw.challenges.reduce(
    (s, c) => s + (c.checkins.some(k => k.date === date && k.completed) ? 1 : 0), 0)

  const health = raw.health.find(h => h.date === date)
  const healthDone = health
    ? [
        (health.water_glasses ?? 0) > 0,
        !!health.exercise_done,
        (health.sleep_hours ?? 0) > 0,
        (health.steps ?? 0) > 0,
      ].filter(Boolean).length
    : 0

  const quran = raw.quran.find(q => q.date === date)
  const checkin = raw.checkins.find(c => c.date === date)

  const sections = [
    { earned: prayerPoints, max: 10, on: !!prayer },
    { earned: tasksDone, max: Math.max(dayTasks.length, 1), on: dayTasks.length > 0 },
    { earned: habitsDone, max: Math.max(eligible.length, 1), on: eligible.length > 0 },
    { earned: chDone, max: Math.max(activeChallenges, 1), on: activeChallenges > 0 },
    { earned: healthDone, max: 4, on: !!health },
  ].filter(s => s.on)

  const score = sections.length > 0
    ? Math.round(sections.reduce((s, x) => s + Math.min(1, x.earned / x.max), 0) / sections.length * 100)
    : 0

  const logged = !!prayer || !!health || !!quran || !!checkin
    || dayLogs.length > 0 || dayTasks.length > 0 || chDone > 0

  return { score, logged }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build
// ─────────────────────────────────────────────────────────────────────────────

export async function buildReport(period: ReportPeriod, offset = 0): Promise<ReportData> {
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) throw new Error('Not signed in')

  const { start, end, today } = periodRange(period, offset)
  const { label, short_label } = periodLabel(period, start, end)
  const prev = periodRange(period, offset + 1)

  const [profileRes, raw, prevRaw] = await Promise.all([
    supabase.from('users').select('name,email').eq('id', user.id).single(),
    fetchRange(user.id, start, end),
    fetchRange(user.id, prev.start, prev.end),
  ])

  const allDays = eachDay(start, end)
  const elapsed = allDays.filter(d => d <= today)   // only days that happened
  const activeChallenges = raw.challenges.filter(c => c.status === 'active').length

  // ── Day rows ───────────────────────────────────────────────────────────────
  const days: DayRow[] = elapsed.map(date => {
    const { score, logged } = scoreDay(date, raw, activeChallenges)
    const prayer = raw.prayers.find(p => p.date === date)
    const prayerVals = PRAYERS.map(k => Number((prayer as any)?.[k] ?? 0))
    const dayLogs = raw.habitLogs.filter(l => l.date === date)
    const eligible = raw.habits.filter(h => (h.created_at ?? '').slice(0, 10) <= date)
    const dayTasks = raw.tasks.filter(t => t.type === 'daily' && t.period_date === date)
    const health = raw.health.find(h => h.date === date)
    const quran = raw.quran.filter(q => q.date === date)
    const checkin = raw.checkins.find(c => c.date === date)
    const extras = Array.isArray(prayer?.extra_prayers) ? (prayer!.extra_prayers as any[]) : []

    return {
      date,
      weekday: WEEKDAYS[parseLocal(date).getDay()],
      logged,
      score,
      prayer_points: prayerVals.reduce((s, v) => s + v, 0),
      prayers_prayed: prayerVals.filter(v => v >= 1).length,
      prayers_jamat: prayerVals.filter(v => v === 2).length,
      extra_prayers: extras.filter(e => Number(e?.status ?? 0) >= 1).length,
      habits_done: eligible.filter(h => habitDone(h, dayLogs.find(l => l.habit_id === h.id))).length,
      habits_total: eligible.length,
      tasks_done: dayTasks.filter(t => t.status === 'completed').length,
      tasks_total: dayTasks.length,
      quran_pages: quran.reduce((s, q) => s + Number(q.pages_read ?? 0), 0),
      sleep_hours: health?.sleep_hours ?? null,
      steps: health?.steps ?? null,
      water_glasses: health?.water_glasses ?? 0,
      exercise_done: !!health?.exercise_done,
      exercise_minutes: Number(health?.exercise_minutes ?? 0),
      mood: health?.mood ?? null,
      weight_kg: health?.weight_kg ?? null,
      reflection: checkin?.evening_text?.trim() || null,
    }
  })

  const scored = days.filter(d => d.logged)
  const avgScore = Math.round(avg(scored.map(d => d.score)) ?? 0)

  // Previous period average, for the trend arrow
  const prevScored = eachDay(prev.start, prev.end)
    .filter(d => d <= today)
    .map(d => scoreDay(d, prevRaw, prevRaw.challenges.filter(c => c.status === 'active').length))
    .filter(s => s.logged)
  const prevAvg = prevScored.length > 0 ? Math.round(avg(prevScored.map(s => s.score))!) : null

  const best = scored.length ? scored.reduce((a, b) => (b.score > a.score ? b : a)) : null
  const worst = scored.length ? scored.reduce((a, b) => (b.score < a.score ? b : a)) : null

  // ── Prayers ────────────────────────────────────────────────────────────────
  const prayerBreakdown: PrayerStat[] = PRAYERS.map(key => {
    const vals = elapsed.map(date => Number((raw.prayers.find(p => p.date === date) as any)?.[key] ?? 0))
    const prayed = vals.filter(v => v >= 1).length
    return {
      key,
      label: key.charAt(0).toUpperCase() + key.slice(1),
      prayed,
      jamat: vals.filter(v => v === 2).length,
      missed: elapsed.length - prayed,
      pct: elapsed.length ? Math.round((prayed / elapsed.length) * 100) : 0,
    }
  })

  // ── Habits ─────────────────────────────────────────────────────────────────
  const habits: HabitStat[] = raw.habits.map(h => {
    const created = (h.created_at ?? '').slice(0, 10)
    const eligibleDays = elapsed.filter(d => created <= d)
    const flags = eligibleDays.map(d =>
      habitDone(h, raw.habitLogs.find(l => l.habit_id === h.id && l.date === d)))
    const doneDays = flags.filter(Boolean).length
    const logs = raw.habitLogs.filter(l => l.habit_id === h.id)
    return {
      id: h.id,
      name: h.name,
      emoji: h.emoji,
      type: h.type,
      unit: h.unit,
      done_days: doneDays,
      eligible_days: eligibleDays.length,
      pct: eligibleDays.length ? Math.round((doneDays / eligibleDays.length) * 100) : 0,
      total_value: logs.reduce((s, l) => s + Number(l.value ?? 0), 0),
      total_minutes: logs.reduce((s, l) => s + Number(l.duration_mins ?? 0), 0),
      current_streak: h.current_streak,
      longest_streak: h.longest_streak,
      best_run: longestRun(flags),
    }
  }).sort((a, b) => b.pct - a.pct)

  const habitCompletionPct = (() => {
    const done = habits.reduce((s, h) => s + h.done_days, 0)
    const total = habits.reduce((s, h) => s + h.eligible_days, 0)
    return total ? Math.round((done / total) * 100) : 0
  })()

  // ── Tasks ──────────────────────────────────────────────────────────────────
  const tasks: TaskStat[] = (['daily', 'weekly', 'monthly'] as TaskRow['type'][]).map(type => {
    const rows = raw.tasks.filter(t => t.type === type)
    const completed = rows.filter(t => t.status === 'completed').length
    return { type, total: rows.length, completed, pct: rows.length ? Math.round((completed / rows.length) * 100) : 0 }
  }).filter(t => t.total > 0)

  const tasksTotal = raw.tasks.length
  const tasksCompleted = raw.tasks.filter(t => t.status === 'completed').length
  const missedTasks = raw.tasks
    .filter(t => t.status !== 'completed' && t.period_date < today)
    .sort((a, b) => (a.period_date < b.period_date ? -1 : 1))
    .map(t => ({ title: t.title, date: t.period_date, priority: t.priority, type: t.type }))

  // ── Challenges (only those overlapping the period) ─────────────────────────
  const challenges: ChallengeStat[] = raw.challenges
    .filter(c => c.start_date <= end && shiftDays(c.start_date, c.duration_days - 1) >= start)
    .map(c => {
      const challengeEnd = shiftDays(c.start_date, c.duration_days - 1)
      const windowStart = c.start_date > start ? c.start_date : start
      const cappedEnd = challengeEnd < end ? challengeEnd : end
      const windowEnd = cappedEnd < today ? cappedEnd : today
      const possible = windowStart <= windowEnd ? eachDay(windowStart, windowEnd).length : 0
      const done = c.checkins.filter(k => k.completed && k.date >= windowStart && k.date <= windowEnd).length
      const dayOf = c.start_date > today
        ? 0
        : Math.min(c.duration_days, eachDay(c.start_date, today < end ? today : end).length)
      return {
        id: c.id,
        title: c.title,
        emoji: c.emoji,
        status: c.status,
        checkins: done,
        possible_days: possible,
        pct: possible ? Math.round((done / possible) * 100) : 0,
        current_streak: c.current_streak,
        longest_streak: c.longest_streak,
        day_of: dayOf,
        duration_days: c.duration_days,
      }
    })

  // ── Goals ──────────────────────────────────────────────────────────────────
  const goals: GoalStat[] = raw.goals.map(g => {
    const ms = g.goal_milestones ?? []
    return {
      id: g.id,
      title: g.title,
      emoji: g.emoji,
      progress_pct: g.progress_pct,
      deadline: g.deadline,
      days_left: g.deadline
        ? Math.round((parseLocal(g.deadline).getTime() - parseLocal(today).getTime()) / 86400000)
        : null,
      milestones_done: ms.filter(m => m.done).length,
      milestones_total: ms.length,
    }
  }).sort((a, b) => b.progress_pct - a.progress_pct)

  // ── Health ─────────────────────────────────────────────────────────────────
  const weights = raw.health
    .filter(h => h.weight_kg != null)
    .sort((a, b) => (a.date < b.date ? -1 : 1))
  const health: HealthSummary = {
    days_logged: raw.health.length,
    avg_sleep: round(avg(raw.health.filter(h => h.sleep_hours != null).map(h => Number(h.sleep_hours)))),
    avg_steps: round(avg(raw.health.filter(h => h.steps != null).map(h => Number(h.steps))), 0),
    total_steps: raw.health.reduce((s, h) => s + Number(h.steps ?? 0), 0),
    avg_water: round(avg(raw.health.map(h => Number(h.water_glasses ?? 0)))),
    exercise_days: raw.health.filter(h => h.exercise_done).length,
    exercise_minutes: raw.health.reduce((s, h) => s + Number(h.exercise_minutes ?? 0), 0),
    avg_mood: round(avg(raw.health.filter(h => h.mood != null).map(h => Number(h.mood)))),
    weight_start: weights.length ? Number(weights[0].weight_kg) : null,
    weight_end: weights.length ? Number(weights[weights.length - 1].weight_kg) : null,
    weight_change: weights.length > 1
      ? round(Number(weights[weights.length - 1].weight_kg) - Number(weights[0].weight_kg))
      : null,
  }

  // ── Weekday pattern ────────────────────────────────────────────────────────
  const weekdayAvgs = WEEKDAYS.map(w => {
    const rows = scored.filter(d => d.weekday === w)
    return { weekday: w, avg: Math.round(avg(rows.map(r => r.score)) ?? 0), days: rows.length }
  }).filter(w => w.days > 0)

  const data: ReportData = {
    period,
    label,
    short_label,
    start,
    end,
    generated_at: new Date().toLocaleString('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    }),
    user: { name: profileRes.data?.name || 'Friend', email: profileRes.data?.email || '' },

    days,
    days_in_period: allDays.length,
    days_elapsed: elapsed.length,
    days_logged: scored.length,

    avg_score: avgScore,
    prev_avg_score: prevAvg,
    score_delta: prevAvg == null ? null : avgScore - prevAvg,
    best_day: best,
    worst_day: worst,
    strong_days: scored.filter(d => d.score >= 75).length,
    weak_days: scored.filter(d => d.score < 50).length,
    best_streak: longestRun(days.map(d => d.logged && d.score >= 60)),

    prayer_points: days.reduce((s, d) => s + d.prayer_points, 0),
    prayer_points_max: elapsed.length * 10,
    prayers_prayed: days.reduce((s, d) => s + d.prayers_prayed, 0),
    prayers_possible: elapsed.length * 5,
    prayers_jamat: days.reduce((s, d) => s + d.prayers_jamat, 0),
    extra_prayers: days.reduce((s, d) => s + d.extra_prayers, 0),
    prayer_breakdown: prayerBreakdown,

    quran_pages: days.reduce((s, d) => s + d.quran_pages, 0),
    quran_days: days.filter(d => d.quran_pages > 0).length,

    habits,
    habit_completion_pct: habitCompletionPct,

    tasks,
    tasks_total: tasksTotal,
    tasks_completed: tasksCompleted,
    tasks_pct: tasksTotal ? Math.round((tasksCompleted / tasksTotal) * 100) : 0,
    missed_tasks: missedTasks,

    challenges,
    goals,
    health,

    weekday_avgs: weekdayAvgs,
    reflections: days.filter(d => d.reflection).map(d => ({ date: d.date, text: d.reflection! })),
    insights: [],
  }

  data.insights = buildInsights(data)
  return data
}

// ─────────────────────────────────────────────────────────────────────────────
// Insights — plain arithmetic, no AI call, so the report always renders
// ─────────────────────────────────────────────────────────────────────────────

function buildInsights(d: ReportData): string[] {
  const unit = d.period === 'weekly' ? 'week' : 'month'
  if (d.days_logged === 0) {
    return [`Nothing was logged this ${unit}. The report fills in as you check in.`]
  }

  const out: string[] = []

  if (d.score_delta != null) {
    out.push(d.score_delta === 0
      ? `Your average score held steady at ${d.avg_score}% versus last ${unit}.`
      : `Your average score is ${d.score_delta > 0 ? 'up' : 'down'} ${Math.abs(d.score_delta)} points versus last ${unit} (${d.prev_avg_score}% → ${d.avg_score}%).`)
  }

  out.push(`You logged ${d.days_logged} of ${d.days_elapsed} days (${Math.round((d.days_logged / Math.max(d.days_elapsed, 1)) * 100)}% consistency), with ${d.strong_days} strong day${d.strong_days === 1 ? '' : 's'} at 75%+ and ${d.weak_days} below 50%.`)

  if (d.best_day && d.worst_day && d.best_day.date !== d.worst_day.date) {
    out.push(`Peak: ${d.best_day.weekday} ${prettyDate(d.best_day.date)} at ${d.best_day.score}%. Low: ${d.worst_day.weekday} ${prettyDate(d.worst_day.date)} at ${d.worst_day.score}%.`)
  }

  if (d.prayers_possible > 0) {
    out.push(`Salah: ${d.prayers_prayed}/${d.prayers_possible} prayers (${Math.round((d.prayers_prayed / d.prayers_possible) * 100)}%), ${d.prayers_jamat} in jamaat.`)
    const byPct = [...d.prayer_breakdown].sort((a, b) => a.pct - b.pct)
    const weakest = byPct[0]
    const strongest = byPct[byPct.length - 1]
    if (weakest && strongest && weakest.pct < strongest.pct) {
      out.push(`${strongest.label} is your most consistent prayer (${strongest.pct}%); ${weakest.label} needs the most work (${weakest.pct}%, ${weakest.missed} missed).`)
    }
  }

  if (d.habits.length > 0) {
    const top = d.habits[0]
    const bottom = d.habits[d.habits.length - 1]
    out.push(`Habits ran at ${d.habit_completion_pct}% overall. Strongest: ${top.name} (${top.done_days}/${top.eligible_days}). Weakest: ${bottom.name} (${bottom.done_days}/${bottom.eligible_days}).`)
  }

  if (d.tasks_total > 0) {
    out.push(`Tasks: ${d.tasks_completed}/${d.tasks_total} completed (${d.tasks_pct}%)${d.missed_tasks.length ? `, ${d.missed_tasks.length} left unfinished` : ''}.`)
  }

  if (d.weekday_avgs.length >= 3) {
    const sorted = [...d.weekday_avgs].sort((a, b) => b.avg - a.avg)
    out.push(`${sorted[0].weekday} is your best weekday (${sorted[0].avg}% avg); ${sorted[sorted.length - 1].weekday} is your weakest (${sorted[sorted.length - 1].avg}%).`)
  }

  if (d.health.avg_sleep != null) {
    out.push(`Sleep averaged ${d.health.avg_sleep}h/night${d.health.exercise_days ? `, and you exercised on ${d.health.exercise_days} day${d.health.exercise_days === 1 ? '' : 's'} (${d.health.exercise_minutes} min total)` : ''}.`)
  }
  if (d.health.weight_change != null && d.health.weight_change !== 0) {
    out.push(`Weight moved ${d.health.weight_change > 0 ? '+' : ''}${d.health.weight_change} kg (${d.health.weight_start} → ${d.health.weight_end} kg).`)
  }
  if (d.quran_pages > 0) {
    out.push(`Quran: ${d.quran_pages} pages over ${d.quran_days} day${d.quran_days === 1 ? '' : 's'}.`)
  }

  return out
}
