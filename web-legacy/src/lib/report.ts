// Weekly / monthly progress report.
//
// Pure computation: the page fetches raw rows, this turns them into a
// ReportData that both the on-screen view and the printable HTML render from.
// Day scoring mirrors the History page (combineDayScores): every feature that
// has something to measure on a day contributes equally to that day's score.
//
// The period is computed twice — current and previous — so every area can be
// diffed and the weakest ones ranked into an actionable focus list.

import { isHabitScheduledOn, isHabitLogComplete } from '@/lib/history'
import { isMetricDone, type CustomMetric } from '@/lib/health'
import type { Habit, HabitLog } from '@/types'
import type { Task, TaskType, TaskPriority } from '@/lib/tasks'

export type ReportPeriod = 'weekly' | 'monthly'

/**
 * How many times a weekday must have been recorded before the report will
 * describe it as a recurring tendency rather than a dated observation.
 *
 * A weekly report contains each weekday exactly once, so one low Sunday says
 * nothing about Sundays in general. Set to 4 per the Improvement Blueprint's
 * sample gate, which in practice means only monthly reports can make the
 * claim. Lowering this re-introduces the over-claiming it exists to prevent.
 */
const MIN_WEEKDAY_OBSERVATIONS = 4

const PRAYERS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'] as const
export type PrayerKey = typeof PRAYERS[number]

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const WEEKDAY_FULL: Record<string, string> = {
  Sun: 'Sunday', Mon: 'Monday', Tue: 'Tuesday', Wed: 'Wednesday',
  Thu: 'Thursday', Fri: 'Friday', Sat: 'Saturday',
}

// ─────────────────────────────────────────────────────────────────────────────
// Date helpers — parse at local noon (app convention, DST-safe), format from
// local Y/M/D so no timezone can shift the day.
// ─────────────────────────────────────────────────────────────────────────────

function parseLocal(dateStr: string): Date {
  return new Date(dateStr + 'T12:00:00')
}

function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function shift(dateStr: string, n: number): string {
  const d = parseLocal(dateStr)
  d.setDate(d.getDate() + n)
  return fmt(d)
}

export function prettyDate(dateStr: string, opts?: Intl.DateTimeFormatOptions): string {
  return parseLocal(dateStr).toLocaleDateString('en-GB', opts ?? { day: 'numeric', month: 'short' })
}

/** Range for a period, `offset` periods back from the current one (0 = current). */
export function periodRange(period: ReportPeriod, offset: number, today: string) {
  if (period === 'weekly') {
    const t = parseLocal(today)
    const dow = t.getDay()                     // 0 = Sun
    const start = shift(today, (dow === 0 ? -6 : 1 - dow) - offset * 7)
    return { start, end: shift(start, 6) }
  }
  const t = parseLocal(today)
  const first = new Date(t.getFullYear(), t.getMonth() - offset, 1, 12)
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0, 12)
  return { start: fmt(first), end: fmt(last) }
}

export function periodLabel(period: ReportPeriod, start: string, end: string) {
  const s = parseLocal(start)
  const e = parseLocal(end)
  if (period === 'monthly') {
    const l = s.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
    return { label: l, shortLabel: l }
  }
  const sameMonth = s.getMonth() === e.getMonth()
  return {
    label: `${s.toLocaleDateString('en-GB', sameMonth ? { day: 'numeric' } : { day: 'numeric', month: 'short' })} – ${e.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}`,
    shortLabel: `Week of ${s.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })}`,
  }
}

function eachDay(start: string, end: string): string[] {
  const out: string[] = []
  let cur = start
  for (let i = 0; i < 400 && cur <= end; i++) { out.push(cur); cur = shift(cur, 1) }
  return out
}

function avg(nums: number[]): number | null {
  return nums.length === 0 ? null : nums.reduce((s, n) => s + n, 0) / nums.length
}

function round(n: number | null, dp = 1): number | null {
  if (n == null) return null
  const f = Math.pow(10, dp)
  return Math.round(n * f) / f
}

function pct(earned: number, possible: number): number | null {
  return possible > 0 ? Math.round((earned / possible) * 100) : null
}

function longestRun(flags: boolean[]): number {
  let best = 0, cur = 0
  for (const f of flags) { cur = f ? cur + 1 : 0; if (cur > best) best = cur }
  return best
}

// ─────────────────────────────────────────────────────────────────────────────
// Shapes
// ─────────────────────────────────────────────────────────────────────────────

export interface DayRow {
  date: string
  weekday: string
  logged: boolean
  score: number
  prayer_points: number
  prayers_prayed: number
  prayers_jamat: number
  extras_done: number
  extras_total: number
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
  verdict: string | null
}

export interface HabitStat {
  id: string; name: string; emoji: string; type: string; unit: string
  done_days: number; scheduled_days: number; pct: number
  total_value: number; total_minutes: number
  current_streak: number; longest_streak: number; best_run: number
}

export interface PrayerStat {
  key: PrayerKey; label: string
  prayed: number; jamat: number; missed: number; pct: number
}

export interface ExtraPrayerStat { name: string; done: number; days: number; pct: number }

export interface TaskStat { type: TaskType; total: number; completed: number; pct: number }

export interface ChallengeStat {
  id: string; title: string; emoji: string; status: string
  checkins: number; possible_days: number; pct: number
  current_streak: number; longest_streak: number
  day_of: number; duration_days: number
}

export interface GoalStat {
  id: string; title: string; emoji: string
  progress_pct: number; deadline: string | null; days_left: number | null
  milestones_done: number; milestones_total: number
  alignment: number | null
}

export interface MetricStat { id: string; name: string; emoji: string; done: number; days: number; pct: number }

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
  metrics: MetricStat[]
}

export type AreaKey =
  | 'overall' | 'consistency' | 'salah' | 'quran'
  | 'habits' | 'tasks' | 'challenges' | 'health'

/** One row of the this-period vs last-period comparison. */
export interface AreaComparison {
  key: AreaKey
  label: string
  emoji: string
  /** percentage 0–100, or a raw count when unit is 'pages' */
  current: number | null
  previous: number | null
  delta: number | null
  unit: '%' | 'pages'
  detail: string
  previousDetail: string
}

export type Severity = 'critical' | 'serious' | 'warning'

/** One ranked "work on this next" item. */
export interface FocusItem {
  key: string
  area: string
  emoji: string
  title: string
  severity: Severity
  severityLabel: string
  stat: string
  delta: number | null
  why: string
  action: string
}

export interface ReportData {
  period: ReportPeriod
  offset: number
  deenEnabled: boolean
  label: string
  shortLabel: string
  previousLabel: string
  start: string
  end: string
  generated_at: string
  user: { name: string; email: string }

  days: DayRow[]
  days_in_period: number
  days_elapsed: number
  days_logged: number

  avg_score: number
  prev_avg_score: number | null
  score_delta: number | null
  best_day: DayRow | null
  worst_day: DayRow | null
  strong_days: number
  weak_days: number
  perfect_days: number
  best_streak: number

  /** this period vs last, area by area */
  comparison: AreaComparison[]
  /** ranked and actionable — the weakest and fastest-declining areas */
  focus: FocusItem[]
  /** biggest gains, so the report is not only bad news */
  wins: AreaComparison[]

  prayer_points: number
  prayer_points_max: number
  prayers_prayed: number
  prayers_possible: number
  prayers_jamat: number
  prayer_breakdown: PrayerStat[]
  extra_prayers: ExtraPrayerStat[]

  quran_pages: number
  quran_days: number
  quran_surahs: string[]

  habits: HabitStat[]
  habit_completion_pct: number

  tasks: TaskStat[]
  tasks_total: number
  tasks_completed: number
  tasks_pct: number
  missed_tasks: { title: string; date: string; priority: TaskPriority; type: TaskType }[]

  challenges: ChallengeStat[]
  goals: GoalStat[]
  health: HealthSummary

  weekday_avgs: { weekday: string; avg: number; days: number }[]
  reflections: { date: string; text: string; verdict: string | null }[]
  insights: string[]
}

// ─────────────────────────────────────────────────────────────────────────────
// Input — raw rows covering the current AND previous period
// ─────────────────────────────────────────────────────────────────────────────

export interface ReportInput {
  period: ReportPeriod
  offset: number
  today: string
  deenEnabled: boolean
  user: { name: string; email: string }
  habits: any[]
  habitLogs: any[]
  prayerLogs: any[]
  tasks: Task[]
  healthLogs: any[]
  quranLogs: any[]
  checkins: any[]
  challenges: any[]
  challengeCheckins: any[]
  goals: any[]
  healthMetrics: CustomMetric[]
}

function extraDone(x: any): boolean {
  if (x?.type === 'count') return (x.value ?? 0) >= (x.target ?? 1)
  if (x?.type === 'boolean') return !!x.done
  return Number(x?.status ?? 0) >= 1
}

// Per-feature day percentages, mirroring the History page's combine logic.
function dayFeatureScores(date: string, i: ReportInput) {
  const out: { pct: number; max: number }[] = []

  const scheduled = (i.habits as Habit[]).filter(
    (h) => h.is_active && (h.created_at ?? '').slice(0, 10) <= date && isHabitScheduledOn(h, date))
  const habitsDone = scheduled.filter((h) =>
    isHabitLogComplete(h, i.habitLogs.find((l: any) => l.habit_id === h.id && l.date === date) as HabitLog))
  out.push({ pct: scheduled.length ? (habitsDone.length / scheduled.length) * 100 : 0, max: scheduled.length })

  const prayer = i.prayerLogs.find((p: any) => p.date === date)
  if (i.deenEnabled) {
    if (prayer) {
      const base = PRAYERS.reduce((s, k) => s + Number(prayer[k] ?? 0), 0)
      const extras: any[] = Array.isArray(prayer.extra_prayers) ? prayer.extra_prayers : []
      const xEarned = extras.reduce((s, x) => s + (extraDone(x) ? (x.score_weight ?? 1) : 0), 0)
      const xMax = extras.reduce((s, x) => s + (x.score_weight ?? 1), 0)
      out.push({ pct: ((base + xEarned) / (10 + xMax)) * 100, max: 10 + xMax })
    } else {
      out.push({ pct: 0, max: 0 })
    }
  }

  const activeCh = i.challenges.filter((c: any) => {
    const end = shift(c.start_date, c.duration_days - 1)
    return c.start_date <= date && date <= end
  })
  const chDone = activeCh.filter((c: any) =>
    i.challengeCheckins.some((k: any) => k.challenge_id === c.id && k.date === date && k.completed))
  out.push({ pct: activeCh.length ? (chDone.length / activeCh.length) * 100 : 0, max: activeCh.length })

  const health = i.healthLogs.find((h: any) => h.date === date)
  const healthFlags = health
    ? [
        (health.water_glasses ?? 0) > 0,
        (health.steps ?? 0) > 0,
        (health.sleep_hours ?? 0) > 0,
        !!health.exercise_done,
      ].filter(Boolean).length
    : 0
  out.push(health ? { pct: (healthFlags / 4) * 100, max: 4 } : { pct: 0, max: 0 })

  const dayTasks = i.tasks.filter((t) => t.type === 'daily' && t.period_date === date)
  const tasksDone = dayTasks.filter((t) => t.status === 'completed')
  out.push({ pct: dayTasks.length ? (tasksDone.length / dayTasks.length) * 100 : 0, max: dayTasks.length })

  const counted = out.filter((f) => f.max > 0)
  const score = counted.length ? Math.round(counted.reduce((s, f) => s + f.pct, 0) / counted.length) : 0

  const quran = i.quranLogs.find((q: any) => q.date === date)
  const checkin = i.checkins.find((c: any) => c.date === date)
  const logged = !!prayer || !!health || !!quran || !!checkin
    || dayTasks.length > 0
    || i.habitLogs.some((l: any) => l.date === date)
    || i.challengeCheckins.some((k: any) => k.date === date && k.completed)

  return {
    score, logged, scheduled, habitsDone: habitsDone.length,
    dayTasks, tasksDone: tasksDone.length,
    prayer, health, quran, checkin,
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// One period, fully computed. Run for both the current and previous period.
// ─────────────────────────────────────────────────────────────────────────────

interface PeriodStats {
  start: string
  end: string
  days: DayRow[]
  daysInPeriod: number
  daysElapsed: number
  daysLogged: number
  avgScore: number
  strongDays: number
  weakDays: number
  perfectDays: number
  bestStreak: number
  bestDay: DayRow | null
  worstDay: DayRow | null

  salahPct: number | null
  prayerPoints: number
  prayerPointsMax: number
  prayersPrayed: number
  prayersPossible: number
  prayersJamat: number
  prayerBreakdown: PrayerStat[]
  extraPrayers: ExtraPrayerStat[]

  quranPages: number
  quranDays: number
  quranSurahs: string[]

  habitPct: number | null
  habitDoneDays: number
  habitScheduledDays: number
  habits: HabitStat[]

  tasksPct: number | null
  tasksTotal: number
  tasksCompleted: number
  tasks: TaskStat[]
  missedTasks: { title: string; date: string; priority: TaskPriority; type: TaskType }[]

  challengePct: number | null
  challengeCheckins: number
  challengePossible: number
  challenges: ChallengeStat[]

  healthPct: number | null
  health: HealthSummary

  consistencyPct: number | null
  weekdayAvgs: { weekday: string; avg: number; days: number }[]
  reflections: { date: string; text: string; verdict: string | null }[]
}

function computePeriod(input: ReportInput, start: string, end: string): PeriodStats {
  const { today } = input
  const allDays = eachDay(start, end)
  const elapsed = allDays.filter((d) => d <= today)

  const days: DayRow[] = elapsed.map((date) => {
    const f = dayFeatureScores(date, input)
    const prayerVals = PRAYERS.map((k) => Number(f.prayer?.[k] ?? 0))
    const extras: any[] = Array.isArray(f.prayer?.extra_prayers) ? f.prayer.extra_prayers : []

    return {
      date,
      weekday: WEEKDAYS[parseLocal(date).getDay()],
      logged: f.logged,
      score: f.score,
      prayer_points: prayerVals.reduce((s, v) => s + v, 0),
      prayers_prayed: prayerVals.filter((v) => v >= 1).length,
      prayers_jamat: prayerVals.filter((v) => v === 2).length,
      extras_done: extras.filter(extraDone).length,
      extras_total: extras.length,
      habits_done: f.habitsDone,
      habits_total: f.scheduled.length,
      tasks_done: f.tasksDone,
      tasks_total: f.dayTasks.length,
      quran_pages: input.quranLogs
        .filter((q: any) => q.date === date)
        .reduce((s: number, q: any) => s + Number(q.pages_read ?? 0), 0),
      sleep_hours: f.health?.sleep_hours ?? null,
      steps: f.health?.steps ?? null,
      water_glasses: f.health?.water_glasses ?? 0,
      exercise_done: !!f.health?.exercise_done,
      exercise_minutes: Number(f.health?.exercise_minutes ?? 0),
      mood: f.health?.mood ?? null,
      weight_kg: f.health?.weight_kg ?? null,
      reflection: f.checkin?.evening_text?.trim() || null,
      verdict: f.checkin?.ai_verdict?.trim() || null,
    }
  })

  const scored = days.filter((d) => d.logged)
  const avgScore = Math.round(avg(scored.map((d) => d.score)) ?? 0)

  const inRange = <T extends { date: string }>(rows: T[]) =>
    rows.filter((r) => r.date >= start && r.date <= end && r.date <= today)

  // ── Salah ──────────────────────────────────────────────────────────────────
  const prayerRows = inRange(input.prayerLogs as { date: string }[]) as any[]
  const prayerBreakdown: PrayerStat[] = PRAYERS.map((key) => {
    const vals = elapsed.map((date) => Number(prayerRows.find((p) => p.date === date)?.[key] ?? 0))
    const prayed = vals.filter((v) => v >= 1).length
    return {
      key,
      label: key.charAt(0).toUpperCase() + key.slice(1),
      prayed,
      jamat: vals.filter((v) => v === 2).length,
      missed: elapsed.length - prayed,
      pct: pct(prayed, elapsed.length) ?? 0,
    }
  })

  const extraNames = new Map<string, { done: number; days: number }>()
  for (const row of prayerRows) {
    const extras: any[] = Array.isArray(row.extra_prayers) ? row.extra_prayers : []
    for (const x of extras) {
      const name = String(x?.name ?? '').trim()
      if (!name) continue
      const cur = extraNames.get(name) ?? { done: 0, days: 0 }
      cur.days += 1
      if (extraDone(x)) cur.done += 1
      extraNames.set(name, cur)
    }
  }
  const extraPrayers: ExtraPrayerStat[] = Array.from(extraNames.entries())
    .map(([name, v]) => ({ name, done: v.done, days: v.days, pct: pct(v.done, v.days) ?? 0 }))
    .sort((a, b) => b.done - a.done)

  const prayersPrayed = days.reduce((s, d) => s + d.prayers_prayed, 0)
  const prayersPossible = elapsed.length * 5

  // ── Quran ──────────────────────────────────────────────────────────────────
  const quranRows = inRange(input.quranLogs as { date: string }[]) as any[]

  // ── Habits ─────────────────────────────────────────────────────────────────
  const habits: HabitStat[] = (input.habits as Habit[])
    .filter((h) => h.is_active)
    .map((h) => {
      const created = (h.created_at ?? '').slice(0, 10)
      const scheduledDays = elapsed.filter((d) => created <= d && isHabitScheduledOn(h, d))
      const flags = scheduledDays.map((d) =>
        isHabitLogComplete(h, input.habitLogs.find((l: any) => l.habit_id === h.id && l.date === d) as HabitLog))
      const doneDays = flags.filter(Boolean).length
      const logs = input.habitLogs.filter((l: any) => l.habit_id === h.id && l.date >= start && l.date <= end)
      return {
        id: h.id, name: h.name, emoji: h.emoji, type: h.type, unit: h.unit,
        done_days: doneDays,
        scheduled_days: scheduledDays.length,
        pct: pct(doneDays, scheduledDays.length) ?? 0,
        total_value: logs.reduce((s: number, l: any) => s + Number(l.value ?? 0), 0),
        total_minutes: logs.reduce((s: number, l: any) => s + Number(l.duration_mins ?? 0), 0),
        current_streak: h.current_streak,
        longest_streak: h.longest_streak,
        best_run: longestRun(flags),
      }
    })
    // A paused habit has no scheduled days, so it would render as a misleading
    // 0/0 at 0% — leave it out rather than score it.
    .filter((h) => h.scheduled_days > 0)
    .sort((a, b) => b.pct - a.pct)

  const habitDoneDays = habits.reduce((s, h) => s + h.done_days, 0)
  const habitScheduledDays = habits.reduce((s, h) => s + h.scheduled_days, 0)

  // ── Tasks ──────────────────────────────────────────────────────────────────
  const periodTasks = input.tasks.filter((t) => t.period_date >= start && t.period_date <= end)
  const tasks: TaskStat[] = (['daily', 'weekly', 'monthly'] as TaskType[]).map((type) => {
    const rows = periodTasks.filter((t) => t.type === type)
    const completed = rows.filter((t) => t.status === 'completed').length
    return { type, total: rows.length, completed, pct: pct(completed, rows.length) ?? 0 }
  }).filter((t) => t.total > 0)
  const tasksCompleted = periodTasks.filter((t) => t.status === 'completed').length

  // ── Challenges ─────────────────────────────────────────────────────────────
  const challenges: ChallengeStat[] = input.challenges
    .filter((c: any) => c.start_date <= end && shift(c.start_date, c.duration_days - 1) >= start)
    .map((c: any) => {
      const chEnd = shift(c.start_date, c.duration_days - 1)
      const wStart = c.start_date > start ? c.start_date : start
      const capped = chEnd < end ? chEnd : end
      const wEnd = capped < today ? capped : today
      const possible = wStart <= wEnd ? eachDay(wStart, wEnd).length : 0
      const done = input.challengeCheckins.filter((k: any) =>
        k.challenge_id === c.id && k.completed && k.date >= wStart && k.date <= wEnd).length
      return {
        id: c.id, title: c.title, emoji: c.emoji ?? '🎯', status: c.status,
        checkins: done,
        possible_days: possible,
        pct: pct(done, possible) ?? 0,
        current_streak: c.current_streak ?? 0,
        longest_streak: c.longest_streak ?? 0,
        day_of: c.start_date > today ? 0
          : Math.min(c.duration_days, eachDay(c.start_date, today < end ? today : end).length),
        duration_days: c.duration_days,
      }
    })
  const challengeCheckins = challenges.reduce((s, c) => s + c.checkins, 0)
  const challengePossible = challenges.reduce((s, c) => s + c.possible_days, 0)

  // ── Health ─────────────────────────────────────────────────────────────────
  const healthRows = inRange(input.healthLogs as { date: string }[]) as any[]
  const weights = healthRows.filter((h) => h.weight_kg != null).sort((a, b) => (a.date < b.date ? -1 : 1))
  const healthFlagTotal = healthRows.reduce((s, h) => s + [
    (h.water_glasses ?? 0) > 0, (h.steps ?? 0) > 0, (h.sleep_hours ?? 0) > 0, !!h.exercise_done,
  ].filter(Boolean).length, 0)

  const health: HealthSummary = {
    days_logged: healthRows.length,
    avg_sleep: round(avg(healthRows.filter((h) => h.sleep_hours != null).map((h) => Number(h.sleep_hours)))),
    avg_steps: round(avg(healthRows.filter((h) => h.steps != null).map((h) => Number(h.steps))), 0),
    total_steps: healthRows.reduce((s, h) => s + Number(h.steps ?? 0), 0),
    avg_water: round(avg(healthRows.map((h) => Number(h.water_glasses ?? 0)))),
    exercise_days: healthRows.filter((h) => h.exercise_done).length,
    exercise_minutes: healthRows.reduce((s, h) => s + Number(h.exercise_minutes ?? 0), 0),
    avg_mood: round(avg(healthRows.filter((h) => h.mood != null).map((h) => Number(h.mood)))),
    weight_start: weights.length ? Number(weights[0].weight_kg) : null,
    weight_end: weights.length ? Number(weights[weights.length - 1].weight_kg) : null,
    weight_change: weights.length > 1
      ? round(Number(weights[weights.length - 1].weight_kg) - Number(weights[0].weight_kg))
      : null,
    metrics: (input.healthMetrics ?? []).map((m) => {
      const withValue = healthRows.filter((h) => (h.extras ?? {})[m.id] !== undefined && (h.extras ?? {})[m.id] !== null)
      const done = withValue.filter((h) => isMetricDone(m, (h.extras ?? {})[m.id])).length
      return {
        id: m.id, name: m.name, emoji: m.emoji,
        done, days: withValue.length, pct: pct(done, withValue.length) ?? 0,
      }
    }).filter((m) => m.days > 0),
  }

  const weekdayAvgs = WEEKDAYS.map((w) => {
    const rows = scored.filter((d) => d.weekday === w)
    return { weekday: w, avg: Math.round(avg(rows.map((r) => r.score)) ?? 0), days: rows.length }
  }).filter((w) => w.days > 0)

  return {
    start, end, days,
    daysInPeriod: allDays.length,
    daysElapsed: elapsed.length,
    daysLogged: scored.length,
    avgScore,
    strongDays: scored.filter((d) => d.score >= 75).length,
    weakDays: scored.filter((d) => d.score < 50).length,
    perfectDays: scored.filter((d) => d.score === 100).length,
    bestStreak: longestRun(days.map((d) => d.logged && d.score >= 60)),
    bestDay: scored.length ? scored.reduce((a, b) => (b.score > a.score ? b : a)) : null,
    worstDay: scored.length ? scored.reduce((a, b) => (b.score < a.score ? b : a)) : null,

    salahPct: input.deenEnabled ? pct(prayersPrayed, prayersPossible) : null,
    prayerPoints: days.reduce((s, d) => s + d.prayer_points, 0),
    prayerPointsMax: elapsed.length * 10,
    prayersPrayed,
    prayersPossible,
    prayersJamat: days.reduce((s, d) => s + d.prayers_jamat, 0),
    prayerBreakdown,
    extraPrayers,

    quranPages: quranRows.reduce((s, q) => s + Number(q.pages_read ?? 0), 0),
    quranDays: new Set(quranRows.filter((q) => Number(q.pages_read ?? 0) > 0).map((q) => q.date)).size,
    quranSurahs: Array.from(new Set(quranRows.map((q) => q.surah).filter(Boolean))) as string[],

    habitPct: pct(habitDoneDays, habitScheduledDays),
    habitDoneDays,
    habitScheduledDays,
    habits,

    tasksPct: pct(tasksCompleted, periodTasks.length),
    tasksTotal: periodTasks.length,
    tasksCompleted,
    tasks,
    missedTasks: periodTasks
      .filter((t) => t.status !== 'completed' && t.period_date < today)
      .sort((a, b) => (a.period_date < b.period_date ? -1 : 1))
      .map((t) => ({ title: t.title, date: t.period_date, priority: t.priority, type: t.type })),

    challengePct: pct(challengeCheckins, challengePossible),
    challengeCheckins,
    challengePossible,
    challenges,

    healthPct: healthRows.length ? pct(healthFlagTotal, 4 * elapsed.length) : null,
    health,

    consistencyPct: pct(scored.length, elapsed.length),
    weekdayAvgs,
    reflections: days
      .filter((d) => d.reflection || d.verdict)
      .map((d) => ({ date: d.date, text: d.reflection ?? '', verdict: d.verdict })),
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build
// ─────────────────────────────────────────────────────────────────────────────

export function buildReport(input: ReportInput): ReportData {
  const { period, offset, today, deenEnabled } = input
  const cur = periodRange(period, offset, today)
  const prv = periodRange(period, offset + 1, today)
  const { label, shortLabel } = periodLabel(period, cur.start, cur.end)
  const previousLabel = periodLabel(period, prv.start, prv.end).label

  const now = computePeriod(input, cur.start, cur.end)
  const was = computePeriod(input, prv.start, prv.end)
  const hadPrevious = was.daysLogged > 0

  const comparison = buildComparison(now, was, hadPrevious, deenEnabled)
  const focus = buildFocus(now, comparison, period)
  const wins = comparison
    .filter((c) => c.key !== 'overall' && c.delta != null && c.delta > 0)
    .sort((a, b) => (b.delta ?? 0) - (a.delta ?? 0))
    .slice(0, 3)

  // Goals are standing objectives, not period-scoped.
  const goals: GoalStat[] = input.goals.map((g: any) => {
    const ms: any[] = g.goal_milestones ?? []
    return {
      id: g.id, title: g.title, emoji: g.emoji ?? '⭐',
      progress_pct: g.progress_pct ?? 0,
      deadline: g.deadline ?? null,
      days_left: g.deadline
        ? Math.round((parseLocal(g.deadline).getTime() - parseLocal(today).getTime()) / 86400000)
        : null,
      milestones_done: ms.filter((m) => m.done).length,
      milestones_total: ms.length,
      alignment: g.ai_alignment?.score ?? null,
    }
  }).sort((a, b) => b.progress_pct - a.progress_pct)

  const data: ReportData = {
    period, offset, deenEnabled,
    label, shortLabel, previousLabel,
    start: cur.start,
    end: cur.end,
    generated_at: new Date().toLocaleString('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    }),
    user: input.user,

    days: now.days,
    days_in_period: now.daysInPeriod,
    days_elapsed: now.daysElapsed,
    days_logged: now.daysLogged,

    avg_score: now.avgScore,
    prev_avg_score: hadPrevious ? was.avgScore : null,
    score_delta: hadPrevious ? now.avgScore - was.avgScore : null,
    best_day: now.bestDay,
    worst_day: now.worstDay,
    strong_days: now.strongDays,
    weak_days: now.weakDays,
    perfect_days: now.perfectDays,
    best_streak: now.bestStreak,

    comparison,
    focus,
    wins,

    prayer_points: now.prayerPoints,
    prayer_points_max: now.prayerPointsMax,
    prayers_prayed: now.prayersPrayed,
    prayers_possible: now.prayersPossible,
    prayers_jamat: now.prayersJamat,
    prayer_breakdown: now.prayerBreakdown,
    extra_prayers: now.extraPrayers,

    quran_pages: now.quranPages,
    quran_days: now.quranDays,
    quran_surahs: now.quranSurahs,

    habits: now.habits,
    habit_completion_pct: now.habitPct ?? 0,

    tasks: now.tasks,
    tasks_total: now.tasksTotal,
    tasks_completed: now.tasksCompleted,
    tasks_pct: now.tasksPct ?? 0,
    missed_tasks: now.missedTasks,

    challenges: now.challenges,
    goals,
    health: now.health,

    weekday_avgs: now.weekdayAvgs,
    reflections: now.reflections,
    insights: [],
  }

  data.insights = buildInsights(data, hadPrevious)
  return data
}

// ─────────────────────────────────────────────────────────────────────────────
// This period vs last, area by area
// ─────────────────────────────────────────────────────────────────────────────

function buildComparison(
  now: PeriodStats, was: PeriodStats, hadPrevious: boolean, deenEnabled: boolean
): AreaComparison[] {
  const prev = <T,>(v: T | null): T | null => (hadPrevious ? v : null)

  const rows: AreaComparison[] = [
    {
      key: 'overall', label: 'Overall score', emoji: '✨', unit: '%',
      current: now.daysLogged ? now.avgScore : null,
      previous: prev(was.daysLogged ? was.avgScore : null),
      delta: null,
      detail: `${now.avgScore}% average`,
      previousDetail: hadPrevious ? `${was.avgScore}% average` : 'no data',
    },
    {
      key: 'consistency', label: 'Days logged', emoji: '📆', unit: '%',
      current: now.consistencyPct,
      previous: prev(was.consistencyPct),
      delta: null,
      detail: `${now.daysLogged} of ${now.daysElapsed} days`,
      previousDetail: hadPrevious ? `${was.daysLogged} of ${was.daysElapsed} days` : 'no data',
    },
  ]

  if (deenEnabled) {
    rows.push({
      key: 'salah', label: 'Salah', emoji: '🕌', unit: '%',
      current: now.salahPct, previous: prev(was.salahPct), delta: null,
      detail: `${now.prayersPrayed}/${now.prayersPossible} prayers · ${now.prayersJamat} jamaat`,
      previousDetail: hadPrevious ? `${was.prayersPrayed}/${was.prayersPossible} · ${was.prayersJamat} jamaat` : 'no data',
    })
    rows.push({
      key: 'quran', label: 'Quran', emoji: '📖', unit: 'pages',
      current: now.quranPages, previous: prev(was.quranPages), delta: null,
      detail: `${now.quranPages} pages over ${now.quranDays} days`,
      previousDetail: hadPrevious ? `${was.quranPages} pages over ${was.quranDays} days` : 'no data',
    })
  }

  rows.push({
    key: 'habits', label: 'Habits', emoji: '🔄', unit: '%',
    current: now.habitPct, previous: prev(was.habitPct), delta: null,
    detail: `${now.habitDoneDays}/${now.habitScheduledDays} scheduled days`,
    previousDetail: hadPrevious ? `${was.habitDoneDays}/${was.habitScheduledDays} days` : 'no data',
  })
  rows.push({
    key: 'tasks', label: 'Tasks', emoji: '✅', unit: '%',
    current: now.tasksPct, previous: prev(was.tasksPct), delta: null,
    detail: `${now.tasksCompleted}/${now.tasksTotal} completed`,
    previousDetail: hadPrevious ? `${was.tasksCompleted}/${was.tasksTotal} completed` : 'no data',
  })
  rows.push({
    key: 'challenges', label: 'Challenges', emoji: '🎯', unit: '%',
    current: now.challengePct, previous: prev(was.challengePct), delta: null,
    detail: now.challenges.length
      ? `${now.challengeCheckins}/${now.challengePossible} check-ins`
      : 'none active',
    previousDetail: hadPrevious && was.challenges.length
      ? `${was.challengeCheckins}/${was.challengePossible} check-ins`
      : 'no data',
  })
  rows.push({
    key: 'health', label: 'Health', emoji: '❤️', unit: '%',
    current: now.healthPct, previous: prev(was.healthPct), delta: null,
    detail: now.health.days_logged
      ? `${now.health.days_logged} days logged · ${now.health.exercise_days} exercised`
      : 'nothing logged',
    previousDetail: hadPrevious && was.health.days_logged
      ? `${was.health.days_logged} days · ${was.health.exercise_days} exercised`
      : 'no data',
  })

  for (const r of rows) {
    r.delta = r.current == null || r.previous == null ? null : r.current - r.previous
  }
  return rows.filter((r) => r.current != null || r.previous != null)
}

// ─────────────────────────────────────────────────────────────────────────────
// Where to improve — ranked, specific, each with an action attached
// ─────────────────────────────────────────────────────────────────────────────

function severityFor(value: number): Severity {
  if (value < 40) return 'critical'
  if (value < 60) return 'serious'
  return 'warning'
}

const SEVERITY_LABEL: Record<Severity, string> = {
  critical: 'Critical',
  serious: 'Needs work',
  warning: 'Watch',
}

const PRAYER_TIP: Record<string, string> = {
  Fajr: 'Move bedtime 30 minutes earlier and put the alarm across the room.',
  Dhuhr: 'Block 15 minutes in your calendar — this one usually collides with work.',
  Asr: 'Set a reminder 20 minutes after the adhan; Asr gets lost in the afternoon.',
  Maghrib: 'It has the shortest window — pray it before you sit down to eat.',
  Isha: 'Fold it into your Maghrib routine rather than leaving it until you are tired.',
}

function buildFocus(now: PeriodStats, comparison: AreaComparison[], period: ReportPeriod): FocusItem[] {
  const unit = period === 'weekly' ? 'week' : 'month'
  if (now.daysLogged === 0) return []

  const byKey = new Map(comparison.map((c) => [c.key, c]))
  const items: (FocusItem & { rank: number })[] = []

  // Lower rank = more urgent. A decline pushes an area up the list.
  const push = (
    key: string, area: string, emoji: string, title: string,
    value: number, delta: number | null, stat: string, why: string, action: string
  ) => {
    const severity = severityFor(value)
    items.push({
      rank: value - (delta != null && delta < 0 ? Math.min(20, Math.abs(delta)) : 0),
      key, area, emoji, title,
      severity, severityLabel: SEVERITY_LABEL[severity],
      stat, delta, why, action,
    })
  }

  // Consistency first — nothing else is measurable without it.
  const cons = byKey.get('consistency')
  if (cons?.current != null && cons.current < 80) {
    const missed = now.daysElapsed - now.daysLogged
    push('consistency', 'Consistency', '📆', 'Logging every day',
      cons.current, cons.delta,
      `${now.daysLogged}/${now.daysElapsed} days`,
      `You missed ${missed} day${missed === 1 ? '' : 's'} entirely, so your real numbers are probably better than this report can show.`,
      'Open the app once at night — even a 20-second check-in keeps the record honest.')
  }

  // The weakest prayer by name, not just "salah is low".
  if (byKey.get('salah')?.current != null && now.prayerBreakdown.length > 0) {
    const weakest = [...now.prayerBreakdown].sort((a, b) => a.pct - b.pct)[0]
    if (weakest.pct < 85) {
      push('prayer', 'Salah', '🕌', `${weakest.label} prayer`,
        weakest.pct, null,
        `${weakest.prayed}/${now.daysElapsed} days`,
        `Your weakest prayer this ${unit} — missed ${weakest.missed} time${weakest.missed === 1 ? '' : 's'}.`,
        PRAYER_TIP[weakest.label] ?? 'Set a reminder 10 minutes before the adhan.')
    }
  }

  // The weakest habit by name.
  const weakHabit = now.habits[now.habits.length - 1]
  if (weakHabit && weakHabit.pct < 70) {
    push('habit', 'Habits', '🔄', weakHabit.name,
      weakHabit.pct, null,
      `${weakHabit.done_days}/${weakHabit.scheduled_days} days`,
      `Your least-kept habit — the longest run this ${unit} was ${weakHabit.best_run} day${weakHabit.best_run === 1 ? '' : 's'}.`,
      weakHabit.pct < 30
        ? 'This one is not sticking. Either halve the target or drop it and put the effort into a habit that is working.'
        : 'Anchor it to something you already do daily, at the same time each day.')
  }

  // Areas that fell hardest.
  for (const c of comparison) {
    if (c.key === 'overall' || c.key === 'consistency') continue
    if (c.current == null || c.delta == null || c.unit !== '%' || c.delta > -8) continue
    push(`drop-${c.key}`, c.label, c.emoji, `${c.label} is slipping`,
      c.current, c.delta,
      `${c.current}% (was ${c.previous}%)`,
      `Down ${Math.abs(c.delta)} points versus last ${unit}.`,
      `Pick the single easiest win in ${c.label.toLowerCase()} and rebuild from there.`)
  }

  // Low areas not already covered by a decline entry.
  for (const c of comparison) {
    if (c.key === 'overall' || c.key === 'consistency') continue
    if (c.current == null || c.unit !== '%' || c.current >= 60) continue
    if (items.some((i) => i.key === `drop-${c.key}`)) continue
    push(`low-${c.key}`, c.label, c.emoji, `${c.label} overall`,
      c.current, c.delta, c.detail,
      `Sitting at ${c.current}%, among the weakest of your tracked areas.`,
      `Set one concrete target for ${c.label.toLowerCase()} next ${unit} rather than trying to fix everything.`)
  }

  // The weekday that reliably drags. Only claim a recurring tendency when the
  // two weekdays being compared have each been observed enough times — a
  // weekly report sees every weekday exactly once, which says nothing about a
  // pattern. Below the gate the report stays silent here; the dated low day is
  // already reported by buildInsights.
  if (now.weekdayAvgs.length >= 4) {
    const sorted = [...now.weekdayAvgs].sort((a, b) => a.avg - b.avg)
    const worst = sorted[0]
    const best = sorted[sorted.length - 1]
    const comparable =
      worst.days >= MIN_WEEKDAY_OBSERVATIONS && best.days >= MIN_WEEKDAY_OBSERVATIONS
    if (comparable && worst.avg < 60 && best.avg - worst.avg >= 15) {
      const worstName = WEEKDAY_FULL[worst.weekday] ?? worst.weekday
      const bestName = WEEKDAY_FULL[best.weekday] ?? best.weekday
      push('weekday', 'Pattern', '📉', `${worstName}s`,
        worst.avg, null,
        `${worst.avg}% avg vs ${best.avg}% on ${bestName}s, ${worst.days} ${worstName}s recorded`,
        `${worstName} is reliably your worst day — a ${best.avg - worst.avg} point gap on your best.`,
        `Plan ${worstName} the night before, or lower the bar for that day on purpose.`)
    }
  }

  // A backlog that never clears.
  if (now.missedTasks.length >= 3 && now.tasksPct != null) {
    const high = now.missedTasks.filter((t) => t.priority === 'high').length
    push('backlog', 'Tasks', '✅', 'Unfinished tasks',
      now.tasksPct, byKey.get('tasks')?.delta ?? null,
      `${now.missedTasks.length} left undone`,
      high > 0
        ? `${now.missedTasks.length} tasks went unfinished, ${high} of them high priority.`
        : `${now.missedTasks.length} tasks went unfinished this ${unit}.`,
      'Carry over only what still matters and delete the rest — a backlog you never clear stops meaning anything.')
  }

  return items
    .sort((a, b) => a.rank - b.rank)
    .slice(0, 5)
    .map((item) => ({
      key: item.key, area: item.area, emoji: item.emoji, title: item.title,
      severity: item.severity, severityLabel: item.severityLabel,
      stat: item.stat, delta: item.delta, why: item.why, action: item.action,
    }))
}

// ─────────────────────────────────────────────────────────────────────────────
// Insights — plain arithmetic, no AI call, so the report always renders
// ─────────────────────────────────────────────────────────────────────────────

function buildInsights(d: ReportData, hadPrevious: boolean): string[] {
  const unit = d.period === 'weekly' ? 'week' : 'month'
  if (d.days_logged === 0) return [`Nothing was logged this ${unit}. The report fills in as you check in.`]

  const out: string[] = []

  if (d.score_delta != null) {
    out.push(d.score_delta === 0
      ? `Your average score held steady at ${d.avg_score}% versus last ${unit}.`
      : `Your average score is ${d.score_delta > 0 ? 'up' : 'down'} ${Math.abs(d.score_delta)} points versus last ${unit} (${d.prev_avg_score}% → ${d.avg_score}%).`)
  } else {
    out.push(`Your average score this ${unit} is ${d.avg_score}%. There is no previous ${unit} to compare against yet.`)
  }

  if (hadPrevious) {
    const moved = d.comparison
      .filter((c) => c.key !== 'overall' && c.delta != null && Math.abs(c.delta) >= 5)
      .sort((a, b) => Math.abs(b.delta ?? 0) - Math.abs(a.delta ?? 0))
    const up = moved.filter((c) => (c.delta ?? 0) > 0)
    const down = moved.filter((c) => (c.delta ?? 0) < 0)
    if (up.length) out.push(`Gained: ${up.map((c) => `${c.label} +${c.delta}`).join(', ')}.`)
    if (down.length) out.push(`Lost ground: ${down.map((c) => `${c.label} ${c.delta}`).join(', ')}.`)
    if (!moved.length) out.push(`No area moved more than 5 points either way — a flat ${unit}.`)
  }

  out.push(`You logged ${d.days_logged} of ${d.days_elapsed} days (${Math.round((d.days_logged / Math.max(d.days_elapsed, 1)) * 100)}% consistency), with ${d.strong_days} strong day${d.strong_days === 1 ? '' : 's'} at 75%+${d.perfect_days ? ` (${d.perfect_days} perfect)` : ''} and ${d.weak_days} below 50%.`)

  if (d.best_day && d.worst_day && d.best_day.date !== d.worst_day.date) {
    out.push(`Peak: ${d.best_day.weekday} ${prettyDate(d.best_day.date)} at ${d.best_day.score}%. Low: ${d.worst_day.weekday} ${prettyDate(d.worst_day.date)} at ${d.worst_day.score}%.`)
  }

  if (d.deenEnabled && d.prayers_possible > 0) {
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
    out.push(`Habits ran at ${d.habit_completion_pct}% overall. Strongest: ${top.name} (${top.done_days}/${top.scheduled_days}). Weakest: ${bottom.name} (${bottom.done_days}/${bottom.scheduled_days}).`)
  }

  // Same gate as the focus list: a weekday ranking is only a tendency once each
  // compared weekday has been seen several times. Otherwise report the dated
  // low day, which is a fact about this period rather than a claimed habit.
  if (d.weekday_avgs.length >= 3) {
    const s = [...d.weekday_avgs].sort((a, b) => b.avg - a.avg)
    const best = s[0]
    const weakest = s[s.length - 1]
    if (best.days >= MIN_WEEKDAY_OBSERVATIONS && weakest.days >= MIN_WEEKDAY_OBSERVATIONS) {
      out.push(`${best.weekday} is your best weekday (${best.avg}% avg over ${best.days}); ${weakest.weekday} is your weakest (${weakest.avg}% over ${weakest.days}).`)
    } else if (d.worst_day && d.best_day && d.worst_day.date !== d.best_day.date) {
      out.push(`Your lowest recorded day was ${d.worst_day.weekday} ${prettyDate(d.worst_day.date)} at ${d.worst_day.score}%, the highest ${d.best_day.weekday} ${prettyDate(d.best_day.date)} at ${d.best_day.score}%. One of each is not yet a weekly pattern.`)
    }
  }

  if (d.health.avg_sleep != null) {
    out.push(`Sleep averaged ${d.health.avg_sleep}h/night${d.health.exercise_days ? `, and you exercised on ${d.health.exercise_days} day${d.health.exercise_days === 1 ? '' : 's'} (${d.health.exercise_minutes} min total)` : ''}.`)
  }
  if (d.health.weight_change != null && d.health.weight_change !== 0) {
    out.push(`Weight moved ${d.health.weight_change > 0 ? '+' : ''}${d.health.weight_change} kg (${d.health.weight_start} → ${d.health.weight_end} kg).`)
  }

  return out
}
