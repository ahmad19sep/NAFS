import { describe, it, expect } from 'vitest'
import { buildReport, type ReportInput, type ReportData } from './report'

/**
 * Regression fixtures D01–D04 and D13 from the NAFS Improvement Blueprint.
 *
 * These record what the CURRENT implementation does. They are an audit, not
 * permission to change the score formula — DATA-01 says understand it first.
 * Where today's behaviour is arguably wrong the assertion still encodes it,
 * with a comment marking what the blueprint wants instead, so the day someone
 * changes the formula the diff shows exactly which promises moved.
 *
 * Scores are driven purely through daily tasks: with deen disabled and no
 * habits, health, challenges or prayer rows, every other feature contributes
 * max: 0 and is dropped by `counted`, so a day's score is exactly
 * round(completed / total * 100). That makes each day's number exact and the
 * fixture readable.
 */

const MONDAY = '2026-08-31'   // period start used throughout
const SUNDAY = '2026-09-06'   // period end — the report covers Mon–Sun

/** Inclusive list of YYYY-MM-DD dates. */
function eachDate(from: string, to: string): string[] {
  const out: string[] = []
  const cur = new Date(`${from}T12:00:00`)
  const end = new Date(`${to}T12:00:00`)
  while (cur <= end) {
    out.push(cur.toISOString().slice(0, 10))
    cur.setDate(cur.getDate() + 1)
  }
  return out
}

/** A day's worth of daily tasks: `done` of `total` completed. */
function tasksFor(date: string, done: number, total: number) {
  return Array.from({ length: total }, (_, i) => ({
    id: `${date}-t${i}`,
    user_id: 'u1',
    title: `task ${i}`,
    note: null,
    type: 'daily' as const,
    priority: 'medium' as const,
    status: (i < done ? 'completed' : 'active') as 'completed' | 'active',
    period_date: date,
    due_time: null,
    alerts_sent: [],
    completed_at: null,
    created_at: `${date}T08:00:00.000Z`,
    updated_at: `${date}T08:00:00.000Z`,
  }))
}

function makeInput(tasks: ReturnType<typeof tasksFor>): ReportInput {
  return {
    period: 'weekly',
    offset: 0,
    // `today` is the last day of the period, so every day has elapsed and
    // nothing is still pending — isolating the missing-data question.
    today: SUNDAY,
    deenEnabled: false,
    user: { name: 'Test', email: 'test@example.com' },
    habits: [],
    habitLogs: [],
    prayerLogs: [],
    tasks,
    healthLogs: [],
    quranLogs: [],
    checkins: [],
    challenges: [],
    challengeCheckins: [],
    goals: [],
    healthMetrics: [],
  }
}

function report(tasks: ReturnType<typeof tasksFor>): ReportData {
  return buildReport(makeInput(tasks))
}

/** Everything the report says in prose, where an over-claim would surface. */
function claimText(r: ReportData): string {
  return [
    ...r.focus.map((f) => `${f.title} ${f.stat} ${f.why} ${f.action}`),
    ...r.insights,
  ].join(' ').toLowerCase()
}

/**
 * Wording that asserts a recurring tendency rather than a dated observation:
 * "reliably", "your worst day", or plural weekdays ("Sundays") which imply a
 * habit rather than one instance.
 */
const RECURRING_CLAIM =
  /reliably|worst day|\b(mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays)\b/

/** Ratios chosen so each day rounds to the blueprint's exact score. */
const D01_DAYS: { date: string; done: number; total: number; score: number }[] = [
  { date: '2026-08-31', done: 13, total: 15, score: 87 }, // 86.67
  { date: '2026-09-01', done: 11, total: 14, score: 79 }, // 78.57
  { date: '2026-09-02', done: 3,  total: 8,  score: 38 }, // 37.5
  { date: '2026-09-03', done: 3,  total: 10, score: 30 },
  { date: '2026-09-04', done: 11, total: 18, score: 61 }, // 61.1
  // 2026-09-05 deliberately absent — the unlogged day
  { date: '2026-09-06', done: 1,  total: 5,  score: 20 },
]

describe('D01 — the reported average uses logged days, not the whole period', () => {
  const tasks = D01_DAYS.flatMap((d) => tasksFor(d.date, d.done, d.total))
  const r = report(tasks)

  it('produces the blueprint daily scores', () => {
    for (const day of D01_DAYS) {
      const row = r.days.find((x) => x.date === day.date)
      expect(row, `missing ${day.date}`).toBeDefined()
      expect(row!.score, `score for ${day.date}`).toBe(day.score)
      expect(row!.logged).toBe(true)
    }
  })

  it('treats the day with no records as unlogged rather than zero', () => {
    const missing = r.days.find((x) => x.date === '2026-09-05')
    expect(missing).toBeDefined()
    expect(missing!.logged).toBe(false)
    // The blueprint's key distinction: absent is not the same as zero.
    expect(r.days_elapsed).toBe(7)
    expect(r.days_logged).toBe(6)
  })

  it('averages over the 6 logged days — 315/6 = 52.5 → 53%', () => {
    const sum = D01_DAYS.reduce((s, d) => s + d.score, 0)
    expect(sum).toBe(315)
    expect(r.avg_score).toBe(53)

    // The alternative policy, zero-filling the unlogged day, would give 45%.
    // Documented so the difference is explicit if the policy ever changes.
    expect(Math.round(sum / 7)).toBe(45)
    expect(r.avg_score).not.toBe(45)
  })

  it('carries the coverage needed to label that average honestly', () => {
    // days_logged/days_elapsed is already in the data, so a denominator can be
    // shown beside the headline number without any new computation.
    expect(r.days_logged / r.days_elapsed).toBeCloseTo(6 / 7)
  })
})

describe('D03 — nothing scheduled produces no rate at all', () => {
  const r = report([])

  it('reports zero logged days', () => {
    expect(r.days_logged).toBe(0)
    expect(r.days_elapsed).toBe(7)
  })

  it('never yields NaN', () => {
    expect(Number.isNaN(r.avg_score)).toBe(false)
  })

  it('currently reports 0% where the blueprint wants "Nothing scheduled"', () => {
    // CURRENT behaviour, deliberately pinned. D03 asks for an explicit
    // "no rate" state instead of a 0 that reads as total failure. Changing
    // that means changing this assertion on purpose.
    expect(r.avg_score).toBe(0)
  })
})

describe('D04 — eligible days with no outcomes recorded', () => {
  // Tasks exist for the period but none were ever acted on. Every day is
  // logged (tasks are present) yet every score is 0 — a real zero, unlike D03.
  const tasks = ['2026-08-31', '2026-09-01', '2026-09-02'].flatMap((d) => tasksFor(d, 0, 4))
  const r = report(tasks)

  it('separates "recorded but incomplete" from "not recorded"', () => {
    expect(r.days_logged).toBe(3)
    expect(r.days_elapsed).toBe(7)
    for (const d of ['2026-08-31', '2026-09-01', '2026-09-02']) {
      expect(r.days.find((x) => x.date === d)!.score).toBe(0)
    }
  })

  it('does not count the four unrecorded days as failures', () => {
    const unlogged = r.days.filter((x) => !x.logged)
    expect(unlogged).toHaveLength(4)
    expect(r.avg_score).toBe(0) // 0 across the 3 logged days
  })
})

describe('D13 — a single observation is not a weekday pattern', () => {
  // One Sunday and one Monday, far apart in score. Today's gate checks how
  // many distinct weekdays appear, not how many times each was observed.
  const tasks = [
    ...tasksFor('2026-08-31', 9, 10),  // Monday, 90
    ...tasksFor('2026-09-06', 1, 10),  // Sunday, 10
  ]
  const r = report(tasks)

  it('records exactly one observation per weekday', () => {
    expect(r.weekday_avgs).toHaveLength(2)
    for (const w of r.weekday_avgs) expect(w.days).toBe(1)
  })

  it('makes no recurring-pattern claim from one observation each', () => {
    expect(claimText(r)).not.toMatch(RECURRING_CLAIM)
  })

  it('the full week — six weekdays, one observation each — also claims nothing', () => {
    // This is the case the supplied report actually hit. Six logged days give
    // six DISTINCT weekdays, so a gate counting weekdays rather than
    // observations per weekday passes, and a single Sunday becomes "reliably
    // your worst day". Every entry below still has days === 1.
    const week = report(D01_DAYS.flatMap((d) => tasksFor(d.date, d.done, d.total)))

    expect(week.weekday_avgs).toHaveLength(6)
    for (const w of week.weekday_avgs) expect(w.days).toBe(1)

    expect(claimText(week)).not.toMatch(RECURRING_CLAIM)
  })

  it('still reports a real pattern once each weekday has enough observations', () => {
    // The gate must suppress over-claiming without disabling the insight.
    // A month of daily logs where every Sunday is genuinely poor gives four
    // observations per weekday — the case the claim was written for.
    const september = eachDate('2026-09-01', '2026-09-30')
    const tasks = september.flatMap((date) => {
      const isSunday = new Date(`${date}T12:00:00`).getDay() === 0
      return isSunday ? tasksFor(date, 2, 10) : tasksFor(date, 9, 10)
    })

    const monthly = buildReport({
      ...makeInput(tasks),
      period: 'monthly',
      today: '2026-09-30',
    })

    const sunday = monthly.weekday_avgs.find((w) => w.weekday === 'Sun')
    expect(sunday?.days).toBeGreaterThanOrEqual(4)
    expect(claimText(monthly)).toMatch(/reliably|sundays/)
  })
})
