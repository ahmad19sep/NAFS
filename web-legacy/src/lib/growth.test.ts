import { describe, it, expect } from 'vitest'
import { buildGrowthPrompt, buildReportReviewPrompt, PREVIOUS_REVIEW_MAX_CHARS, type PeriodSummary } from './growth'

const period = (over: Partial<PeriodSummary> = {}): PeriodSummary => ({
  period: 'This week', previous: 'Last week',
  days_in_period: 7, days_elapsed: 2, days_logged: 1,
  avg_score: 40, prev_avg_score: 55, score_delta: -15,
  areas: [], wins: [], focus: [], weekdays: [],
  ...over,
})

describe('buildGrowthPrompt', () => {
  it('carries the context, both comparisons and the instruction', () => {
    const p = buildGrowthPrompt({
      context: { today: '2026-09-07' },
      week: period(),
      month: period({ period: 'September so far', previous: 'August' }),
    })
    expect(p).toContain('"today": "2026-09-07"')
    expect(p).toContain('THIS WEEK AGAINST LAST')
    expect(p).toContain('"period": "September so far"')
    expect(p).toContain('absent means unknown, not zero')
    expect(p.trim().endsWith('Write the growth review.')).toBe(true)
    expect(p).not.toContain('PREVIOUS REVIEW')
  })

  it('includes the previous review, dated and truncated', () => {
    const long = 'x'.repeat(PREVIOUS_REVIEW_MAX_CHARS + 500)
    const p = buildGrowthPrompt({
      context: {},
      week: period(),
      month: period(),
      previousReview: { generated_at: '2026-08-31T05:00:00.000Z', content_md: long },
    })
    expect(p).toContain('PREVIOUS REVIEW (written 2026-08-31)')
    expect(p).toContain('x'.repeat(PREVIOUS_REVIEW_MAX_CHARS) + ' …')
    expect(p).not.toContain('x'.repeat(PREVIOUS_REVIEW_MAX_CHARS + 1))
  })

  it('passes days elapsed and logged so thin data can be called thin', () => {
    const p = buildGrowthPrompt({ context: {}, week: period({ days_elapsed: 2, days_logged: 1 }), month: period() })
    expect(p).toContain('"days_elapsed": 2')
    expect(p).toContain('"days_logged": 1')
  })
})

describe('buildReportReviewPrompt', () => {
  it('leads with the period the reader is holding, and names it', () => {
    const p = buildReportReviewPrompt({
      summary: period({ period: 'Mon 1 – Sun 7 Sep' }),
      context: { today: '2026-09-07' },
      period: 'weekly',
    })
    expect(p.indexOf('THIS WEEK AGAINST LAST')).toBeLessThan(p.indexOf('WIDER CONTEXT'))
    expect(p).toContain('"period": "Mon 1 – Sun 7 Sep"')
    expect(p).toContain('absent means unknown, not zero')
    expect(p.trim().endsWith("Write the coach's read for this week's printed report.")).toBe(true)
  })

  it('says month when the period is monthly', () => {
    const p = buildReportReviewPrompt({ summary: period(), context: {}, period: 'monthly' })
    expect(p).toContain('THIS MONTH AGAINST LAST')
    expect(p.trim().endsWith("Write the coach's read for this month's printed report.")).toBe(true)
  })
})
