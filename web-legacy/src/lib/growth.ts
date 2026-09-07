/**
 * The growth review's input.
 *
 * Three things, none of which the model has to estimate for itself: the
 * shared coach context (a month of records, what keeps not happening, what
 * was different on those days, what the user said), the deterministic
 * this-period-against-last comparisons the Reports page already computes —
 * this week against last, this month against last — and the previous
 * review, so it can say what changed since.
 *
 * The comparisons are calendar periods, and early in a week or month the
 * current side is thin. The days elapsed and days logged are passed so the
 * model can say so instead of calling two days a trend.
 */

import type { ReportData } from './report'

export interface PeriodSummary {
  period: string
  previous: string
  days_in_period: number
  days_elapsed: number
  days_logged: number
  avg_score: number
  prev_avg_score: number | null
  score_delta: number | null
  /** Area by area. `now`/`before` are percentages unless unit says otherwise. */
  areas: {
    area: string
    now: number | null
    before: number | null
    delta: number | null
    unit: string
    now_detail: string
    before_detail: string
  }[]
  /** Biggest gains, labelled. */
  wins: string[]
  /** Ranked weakest / fastest-declining areas, with the report's own reasoning. */
  focus: { title: string; severity: string; stat: string; why: string; action: string }[]
  /** Average score by weekday over the period, where there were days. */
  weekdays: { weekday: string; avg: number; days: number }[]
}

export function summarizeReport(r: ReportData): PeriodSummary {
  return {
    period: r.label,
    previous: r.previousLabel,
    days_in_period: r.days_in_period,
    days_elapsed: r.days_elapsed,
    days_logged: r.days_logged,
    avg_score: r.avg_score,
    prev_avg_score: r.prev_avg_score,
    score_delta: r.score_delta,
    areas: r.comparison.map((c) => ({
      area: c.label,
      now: c.current,
      before: c.previous,
      delta: c.delta,
      unit: c.unit,
      now_detail: c.detail,
      before_detail: c.previousDetail,
    })),
    wins: r.wins.map((w) => `${w.label}: ${w.detail} (was ${w.previousDetail})`),
    focus: r.focus.slice(0, 5).map((f) => ({
      title: f.title, severity: f.severityLabel, stat: f.stat, why: f.why, action: f.action,
    })),
    weekdays: r.weekday_avgs.filter((w) => w.days > 0),
  }
}

/** Enough of the last review to say what changed; not the whole thing again. */
export const PREVIOUS_REVIEW_MAX_CHARS = 1500

export interface GrowthPromptInput {
  context: Record<string, unknown>
  week: PeriodSummary
  month: PeriodSummary
  previousReview?: { generated_at: string; content_md: string } | null
}

export function buildGrowthPrompt(input: GrowthPromptInput): string {
  const parts = [
    'USER DATA (real, last 30 days — every count carries its denominator; absent means unknown, not zero):',
    JSON.stringify(input.context, null, 1),
    '',
    'THIS WEEK AGAINST LAST (deterministic, from the records):',
    JSON.stringify(input.week, null, 1),
    '',
    'THIS MONTH AGAINST LAST:',
    JSON.stringify(input.month, null, 1),
  ]

  if (input.previousReview?.content_md) {
    const on = input.previousReview.generated_at.slice(0, 10)
    const text = input.previousReview.content_md.length > PREVIOUS_REVIEW_MAX_CHARS
      ? input.previousReview.content_md.slice(0, PREVIOUS_REVIEW_MAX_CHARS) + ' …'
      : input.previousReview.content_md
    parts.push('', `PREVIOUS REVIEW (written ${on}) — say what changed since, briefly, only where the numbers show it:`, text)
  }

  parts.push('', 'Write the growth review.')
  return parts.join('\n')
}

export interface ReportReviewPromptInput {
  /** The period being reported on, already compared against the one before. */
  summary: PeriodSummary
  /** Habits, misses, causes and the user's own words. Trimmed by the route. */
  context: Record<string, unknown>
  period: 'weekly' | 'monthly'
}

/**
 * The prompt behind the written read on a printed report. It leads with the
 * period summary, because that is what the reader is holding, and carries the
 * coach context underneath so a cause can be named.
 */
export function buildReportReviewPrompt(input: ReportReviewPromptInput): string {
  const word = input.period === 'weekly' ? 'week' : 'month'
  return [
    `THIS ${word.toUpperCase()} AGAINST LAST (deterministic, from the records; every count carries its denominator):`,
    JSON.stringify(input.summary, null, 1),
    '',
    'WIDER CONTEXT (last 30 days; absent means unknown, not zero):',
    JSON.stringify(input.context, null, 1),
    '',
    `Write the coach's read for this ${word}'s printed report.`,
  ].join('\n')
}
