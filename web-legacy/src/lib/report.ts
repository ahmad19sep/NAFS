// Weekly / monthly progress report.
//
// Pure computation: the page fetches raw rows, this turns them into a
// ReportData that both the on-screen view and the printable HTML render from.
// Day scoring mirrors the History page (combineDayScores): every feature that
// has something to measure on a day contributes equally to that day's score.

import { isHabitScheduledOn, isHabitLogComplete } from '@/lib/history'
import { isMetricDone, type CustomMetric } from '@/lib/health'
import type { Habit, HabitLog } from '@/types'
import type { Task, TaskType, TaskPriority } from '@/lib/tasks'

export type ReportPeriod = 'weekly' | 'monthly'

const PRAYERS = ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha'] as const
export type PrayerKey = typeof PRAYERS[number]

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

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
  prayer_max: number
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
  is_paused: boolean
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

export interface ReportData {
  period: ReportPeriod
  offset: number
  deenEnabled: boolean
  label: string
  shortLabel: string
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

// Per-feature day percentages, mirroring the History page's combine logic.
function dayFeatureScores(date: string, i: ReportInput) {
  const out: { pct: number; max: number }[] = []

  // Habits
  const scheduled = (i.habits as Habit[]).filter(
    (h) => h.is_active && (h.created_at ?? '').slice(0, 10) <= date && isHabitScheduledOn(h, date))
  const habitsDone = scheduled.filter((h) =>
    isHabitLogComplete(h, i.habitLogs.find((l: any) => l.habit_id === h.id && l.date === date) as HabitLog))
  out.push({ pct: scheduled.length ? (habitsDone.length / scheduled.length) * 100 : 0, max: scheduled.length })

  // Deen
  const prayer = i.prayerLogs.find((p: any) => p.date === date)
  if (i.deenEnabled) {
    if (prayer) {
      const base = PRAYERS.reduce((s, k) => s + Number(prayer[k] ?? 0), 0)
      const extras: any[] = Array.isArray(prayer.extra_prayers) ? prayer.extra_prayers : []
      const xEarned = extras.reduce((s, x) => {
        if (x.type === 'count') return s + ((x.value ?? 0) >= (x.target ?? 1) ? (x.score_weight ?? 1) : 0)
        if (x.type === 'boolean') return s + (x.done ? (x.score_weight ?? 1) : 0)
        return s + (Number(x.status ?? 0) >= 1 ? (x.score_weight ?? 1) : 0)
      }, 0)
      const xMax = extras.reduce((s, x) => s + (x.score_weight ?? 1), 0)
      out.push({ pct: ((base + xEarned) / (10 + xMax)) * 100, max: 10 + xMax })
    } else {
      out.push({ pct: 0, max: 0 })
    }
  }

  // Challenges active on that day
  const activeCh = i.challenges.filter((c: any) => {
    const end = shift(c.start_date, c.duration_days - 1)
    return c.start_date <= date && date <= end
  })
  const chDone = activeCh.filter((c: any) =>
    i.challengeCheckins.some((k: any) => k.challenge_id === c.id && k.date === date && k.completed))
  out.push({ pct: activeCh.length ? (chDone.length / activeCh.length) * 100 : 0, max: activeCh.length })

  // Health
  const health = i.healthLogs.find((h: any) => h.date === date)
  if (health) {
    const flags = [
      (health.water_glasses ?? 0) > 0,
      (health.steps ?? 0) > 0,
      (health.sleep_hours ?? 0) > 0,
      !!health.exercise_done,
    ]
    out.push({ pct: (flags.filter(Boolean).length / 4) * 100, max: 4 })
  } else {
    out.push({ pct: 0, max: 0 })
  }

  // Daily tasks
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

  return { score, logged, scheduled, habitsDone: habitsDone.length, dayTasks, tasksDone: tasksDone.length, prayer, health, quran, checkin }
}

// ─────────────────────────────────────────────────────────────────────────────

export function buildReport(input: ReportInput): ReportData {
  const { period, offset, today, deenEnabled } = input
  const { start, end } = periodRange(period, offset, today)
  const { label, shortLabel } = periodLabel(period, start, end)
  const prev = periodRange(period, offset + 1, today)

  const allDays = eachDay(start, end)
  const elapsed = allDays.filter((d) => d <= today)

  // ── Day rows ───────────────────────────────────────────────────────────────
  const days: DayRow[] = elapsed.map((date) => {
    const f = dayFeatureScores(date, input)
    const prayerVals = PRAYERS.map((k) => Number(f.prayer?.[k] ?? 0))
    const extras: any[] = Array.isArray(f.prayer?.extra_prayers) ? f.prayer.extra_prayers : []
    const extrasDone = extras.filter((x: any) =>
      x.type === 'count' ? (x.value ?? 0) >= (x.target ?? 1)
      : x.type === 'boolean' ? !!x.done
      : Number(x.status ?? 0) >= 1).length

    return {
      date,
      weekday: WEEKDAYS[parseLocal(date).getDay()],
      logged: f.logged,
      score: f.score,
      prayer_points: prayerVals.reduce((s, v) => s + v, 0),
      prayer_max: 10,
      prayers_prayed: prayerVals.filter((v) => v >= 1).length,
      prayers_jamat: prayerVals.filter((v) => v === 2).length,
      extras_done: extrasDone,
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

  // Previous period average, from the same raw rows
  const prevScores = eachDay(prev.start, prev.end)
    .filter((d) => d <= today)
    .map((d) => dayFeatureScores(d, input))
    .filter((f) => f.logged)
    .map((f) => f.score)
  const prevAvg = prevScores.length ? Math.round(avg(prevScores)!) : null

  const best = scored.length ? scored.reduce((a, b) => (b.score > a.score ? b : a)) : null
  const worst = scored.length ? scored.reduce((a, b) => (b.score < a.score ? b : a)) : null

  // ── Prayers ────────────────────────────────────────────────────────────────
  const inPeriod = <T extends { date: string }>(rows: T[]) =>
    rows.filter((r) => r.date >= start && r.date <= end && r.date <= today)

  const prayerRows = inPeriod(input.prayerLogs as { date: string }[]) as any[]
  const prayerBreakdown: PrayerStat[] = PRAYERS.map((key) => {
    const vals = elapsed.map((date) => Number(prayerRows.find((p) => p.date === date)?.[key] ?? 0))
    const prayed = vals.filter((v) => v >= 1).length
    return {
      key,
      label: key.charAt(0).toUpperCase() + key.slice(1),
      prayed,
      jamat: vals.filter((v) => v === 2).length,
      missed: elapsed.length - prayed,
      pct: elapsed.length ? Math.round((prayed / elapsed.length) * 100) : 0,
    }
  })

  // Extra / nafl prayers, aggregated by name
  const extraNames = new Map<string, { done: number; days: number }>()
  for (const row of prayerRows) {
    const extras: any[] = Array.isArray(row.extra_prayers) ? row.extra_prayers : []
    for (const x of extras) {
      const name = String(x?.name ?? '').trim()
      if (!name) continue
      const cur = extraNames.get(name) ?? { done: 0, days: 0 }
      cur.days += 1
      const done = x.type === 'count' ? (x.value ?? 0) >= (x.target ?? 1)
                 : x.type === 'boolean' ? !!x.done
                 : Number(x.status ?? 0) >= 1
      if (done) cur.done += 1
      extraNames.set(name, cur)
    }
  }
  const extraPrayers: ExtraPrayerStat[] = Array.from(extraNames.entries())
    .map(([name, v]) => ({ name, done: v.done, days: v.days, pct: v.days ? Math.round((v.done / v.days) * 100) : 0 }))
    .sort((a, b) => b.done - a.done)

  // ── Quran ──────────────────────────────────────────────────────────────────
  const quranRows = inPeriod(input.quranLogs as { date: string }[]) as any[]
  const quranPages = quranRows.reduce((s, q) => s + Number(q.pages_read ?? 0), 0)

  // ── Habits ─────────────────────────────────────────────────────────────────
  const habits: HabitStat[] = (input.habits as Habit[])
    .filter((h) => h.is_active)
    .map((h) => {
      const created = (h.created_at ?? '').slice(0, 10)
      const scheduledDays = elapsed.filter((d) => created <= d && isHabitScheduledOn(h, d))
      const flags = scheduledDays.map((d) =>
        isHabitLogComplete(h, input.habitLogs.find((l: any) => l.habit_id === h.id && l.date === d) as HabitLog))
      const logs = input.habitLogs.filter((l: any) => l.habit_id === h.id && l.date >= start && l.date <= end)
      return {
        id: h.id,
        name: h.name,
        emoji: h.emoji,
        type: h.type,
        unit: h.unit,
        done_days: flags.filter(Boolean).length,
        scheduled_days: scheduledDays.length,
        pct: scheduledDays.length ? Math.round((flags.filter(Boolean).length / scheduledDays.length) * 100) : 0,
        total_value: logs.reduce((s: number, l: any) => s + Number(l.value ?? 0), 0),
        total_minutes: logs.reduce((s: number, l: any) => s + Number(l.duration_mins ?? 0), 0),
        current_streak: h.current_streak,
        longest_streak: h.longest_streak,
        best_run: longestRun(flags),
        is_paused: !!h.is_paused,
      }
    })
    // A paused habit has no scheduled days, so it would render as a misleading
    // 0/0 at 0% — leave it out rather than score it.
    .filter((h) => h.scheduled_days > 0)
    .sort((a, b) => b.pct - a.pct)

  const habitDone = habits.reduce((s, h) => s + h.done_days, 0)
  const habitSched = habits.reduce((s, h) => s + h.scheduled_days, 0)

  // ── Tasks ──────────────────────────────────────────────────────────────────
  const periodTasks = input.tasks.filter((t) => t.period_date >= start && t.period_date <= end)
  const tasks: TaskStat[] = (['daily', 'weekly', 'monthly'] as TaskType[])
    .map((type) => {
      const rows = periodTasks.filter((t) => t.type === type)
      const completed = rows.filter((t) => t.status === 'completed').length
      return { type, total: rows.length, completed, pct: rows.length ? Math.round((completed / rows.length) * 100) : 0 }
    })
    .filter((t) => t.total > 0)

  // ── Challenges overlapping the period ──────────────────────────────────────
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
        id: c.id,
        title: c.title,
        emoji: c.emoji ?? '🎯',
        status: c.status,
        checkins: done,
        possible_days: possible,
        pct: possible ? Math.round((done / possible) * 100) : 0,
        current_streak: c.current_streak ?? 0,
        longest_streak: c.longest_streak ?? 0,
        day_of: c.start_date > today ? 0
          : Math.min(c.duration_days, eachDay(c.start_date, today < end ? today : end).length),
        duration_days: c.duration_days,
      }
    })

  // ── Goals ──────────────────────────────────────────────────────────────────
  const goals: GoalStat[] = input.goals
    .map((g: any) => {
      const ms: any[] = g.goal_milestones ?? []
      return {
        id: g.id,
        title: g.title,
        emoji: g.emoji ?? '⭐',
        progress_pct: g.progress_pct ?? 0,
        deadline: g.deadline ?? null,
        days_left: g.deadline
          ? Math.round((parseLocal(g.deadline).getTime() - parseLocal(today).getTime()) / 86400000)
          : null,
        milestones_done: ms.filter((m) => m.done).length,
        milestones_total: ms.length,
        alignment: g.ai_alignment?.score ?? null,
      }
    })
    .sort((a, b) => b.progress_pct - a.progress_pct)

  // ── Health ─────────────────────────────────────────────────────────────────
  const healthRows = inPeriod(input.healthLogs as { date: string }[]) as any[]
  const weights = healthRows.filter((h) => h.weight_kg != null).sort((a, b) => (a.date < b.date ? -1 : 1))
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
        done, days: withValue.length,
        pct: withValue.length ? Math.round((done / withValue.length) * 100) : 0,
      }
    }).filter((m) => m.days > 0),
  }

  // ── Weekday pattern ────────────────────────────────────────────────────────
  const weekdayAvgs = WEEKDAYS.map((w) => {
    const rows = scored.filter((d) => d.weekday === w)
    return { weekday: w, avg: Math.round(avg(rows.map((r) => r.score)) ?? 0), days: rows.length }
  }).filter((w) => w.days > 0)

  const data: ReportData = {
    period, offset, deenEnabled, label, shortLabel, start, end,
    generated_at: new Date().toLocaleString('en-GB', {
      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    }),
    user: input.user,

    days,
    days_in_period: allDays.length,
    days_elapsed: elapsed.length,
    days_logged: scored.length,

    avg_score: avgScore,
    prev_avg_score: prevAvg,
    score_delta: prevAvg == null ? null : avgScore - prevAvg,
    best_day: best,
    worst_day: worst,
    strong_days: scored.filter((d) => d.score >= 75).length,
    weak_days: scored.filter((d) => d.score < 50).length,
    perfect_days: scored.filter((d) => d.score === 100).length,
    best_streak: longestRun(days.map((d) => d.logged && d.score >= 60)),

    prayer_points: days.reduce((s, d) => s + d.prayer_points, 0),
    prayer_points_max: elapsed.length * 10,
    prayers_prayed: days.reduce((s, d) => s + d.prayers_prayed, 0),
    prayers_possible: elapsed.length * 5,
    prayers_jamat: days.reduce((s, d) => s + d.prayers_jamat, 0),
    prayer_breakdown: prayerBreakdown,
    extra_prayers: extraPrayers,

    quran_pages: quranPages,
    quran_days: new Set(quranRows.filter((q) => Number(q.pages_read ?? 0) > 0).map((q) => q.date)).size,
    quran_surahs: Array.from(new Set(quranRows.map((q) => q.surah).filter(Boolean))) as string[],

    habits,
    habit_completion_pct: habitSched ? Math.round((habitDone / habitSched) * 100) : 0,

    tasks,
    tasks_total: periodTasks.length,
    tasks_completed: periodTasks.filter((t) => t.status === 'completed').length,
    tasks_pct: periodTasks.length
      ? Math.round((periodTasks.filter((t) => t.status === 'completed').length / periodTasks.length) * 100)
      : 0,
    missed_tasks: periodTasks
      .filter((t) => t.status !== 'completed' && t.period_date < today)
      .sort((a, b) => (a.period_date < b.period_date ? -1 : 1))
      .map((t) => ({ title: t.title, date: t.period_date, priority: t.priority, type: t.type })),

    challenges,
    goals,
    health,

    weekday_avgs: weekdayAvgs,
    reflections: days
      .filter((d) => d.reflection || d.verdict)
      .map((d) => ({ date: d.date, text: d.reflection ?? '', verdict: d.verdict })),
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
  if (d.days_logged === 0) return [`Nothing was logged this ${unit}. The report fills in as you check in.`]

  const out: string[] = []

  if (d.score_delta != null) {
    out.push(d.score_delta === 0
      ? `Your average score held steady at ${d.avg_score}% versus last ${unit}.`
      : `Your average score is ${d.score_delta > 0 ? 'up' : 'down'} ${Math.abs(d.score_delta)} points versus last ${unit} (${d.prev_avg_score}% → ${d.avg_score}%).`)
  }

  out.push(`You logged ${d.days_logged} of ${d.days_elapsed} days (${Math.round((d.days_logged / Math.max(d.days_elapsed, 1)) * 100)}% consistency), with ${d.strong_days} strong day${d.strong_days === 1 ? '' : 's'} at 75%+${d.perfect_days ? ` (${d.perfect_days} perfect)` : ''} and ${d.weak_days} below 50%.`)

  if (d.best_day && d.worst_day && d.best_day.date !== d.worst_day.date) {
    out.push(`Peak: ${d.best_day.weekday} ${prettyDate(d.best_day.date)} at ${d.best_day.score}%. Low: ${d.worst_day.weekday} ${prettyDate(d.worst_day.date)} at ${d.worst_day.score}%.`)
  }

  if (d.deenEnabled && d.prayers_possible > 0) {
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
    out.push(`Habits ran at ${d.habit_completion_pct}% overall. Strongest: ${top.name} (${top.done_days}/${top.scheduled_days}). Weakest: ${bottom.name} (${bottom.done_days}/${bottom.scheduled_days}).`)
  }

  if (d.tasks_total > 0) {
    out.push(`Tasks: ${d.tasks_completed}/${d.tasks_total} completed (${d.tasks_pct}%)${d.missed_tasks.length ? `, ${d.missed_tasks.length} left unfinished` : ''}.`)
  }

  if (d.weekday_avgs.length >= 3) {
    const s = [...d.weekday_avgs].sort((a, b) => b.avg - a.avg)
    out.push(`${s[0].weekday} is your best weekday (${s[0].avg}% avg); ${s[s.length - 1].weekday} is your weakest (${s[s.length - 1].avg}%).`)
  }

  if (d.health.avg_sleep != null) {
    out.push(`Sleep averaged ${d.health.avg_sleep}h/night${d.health.exercise_days ? `, and you exercised on ${d.health.exercise_days} day${d.health.exercise_days === 1 ? '' : 's'} (${d.health.exercise_minutes} min total)` : ''}.`)
  }
  if (d.health.weight_change != null && d.health.weight_change !== 0) {
    out.push(`Weight moved ${d.health.weight_change > 0 ? '+' : ''}${d.health.weight_change} kg (${d.health.weight_start} → ${d.health.weight_end} kg).`)
  }
  if (d.deenEnabled && d.quran_pages > 0) {
    out.push(`Quran: ${d.quran_pages} pages over ${d.quran_days} day${d.quran_days === 1 ? '' : 's'}.`)
  }

  return out
}
