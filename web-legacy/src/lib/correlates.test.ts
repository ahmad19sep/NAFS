import { describe, it, expect } from 'vitest'
import { findCorrelates, describeCorrelate, hm } from './correlates'
import type { RepeatedMiss } from './misses'

/**
 * Possible causes. What these protect: a comparison needs enough days on
 * both sides, a gap must be large enough to mean something, and a day with
 * no value is unknown — it never becomes a zero that drags an average down.
 */

const fajr: RepeatedMiss = {
  kind: 'prayer', id: 'fajr', label: 'Fajr', emoji: '🌅',
  missed: 4, of: 7, windowDays: 7,
  missedDates: ['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04'],
  doneDates: ['2026-09-05', '2026-09-06', '2026-09-07'],
}

describe('findCorrelates', () => {
  it('reports less sleep on missed days when the gap is real', () => {
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {
        '2026-09-01': 300, '2026-09-02': 320, '2026-09-03': 290, '2026-09-04': 310,
        '2026-09-05': 430, '2026-09-06': 450, '2026-09-07': 440,
      },
      screenMinutesByDate: {},
    })
    expect(r).toHaveLength(1)
    expect(r[0]).toMatchObject({
      subject: 'prayer:fajr', factor: 'sleep',
      missedAvgMinutes: 305, doneAvgMinutes: 440, missedDays: 4, doneDays: 3,
    })
  })

  it('reports more screen time on missed days', () => {
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {},
      screenMinutesByDate: {
        '2026-09-01': 240, '2026-09-02': 200, '2026-09-03': 260, '2026-09-04': 220,
        '2026-09-05': 90, '2026-09-06': 110, '2026-09-07': 100,
      },
    })
    expect(r).toHaveLength(1)
    expect(r[0]).toMatchObject({ factor: 'screen_time', missedAvgMinutes: 230, doneAvgMinutes: 100 })
  })

  it('stays quiet when the gap is small', () => {
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {
        '2026-09-01': 420, '2026-09-02': 430, '2026-09-03': 410, '2026-09-04': 440,
        '2026-09-05': 430, '2026-09-06': 450, '2026-09-07': 440,
      },
      screenMinutesByDate: {},
    })
    expect(r).toHaveLength(0)
  })

  it('needs enough days on both sides', () => {
    // Only one done day has a sleep value: not enough to compare against.
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {
        '2026-09-01': 300, '2026-09-02': 320, '2026-09-03': 290, '2026-09-04': 310,
        '2026-09-05': 450,
      },
      screenMinutesByDate: {},
    })
    expect(r).toHaveLength(0)
  })

  it('treats a day with no value as unknown, not zero', () => {
    // Two missed days have no sleep value. If they were read as zero the
    // missed average would collapse; instead they are simply left out.
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {
        '2026-09-01': 420, '2026-09-02': 430,
        '2026-09-05': 430, '2026-09-06': 450, '2026-09-07': 440,
      },
      screenMinutesByDate: {},
    })
    expect(r).toHaveLength(0)
  })

  it('can report both factors for one miss', () => {
    const r = findCorrelates({
      misses: [fajr],
      sleepMinutesByDate: {
        '2026-09-01': 300, '2026-09-02': 300, '2026-09-05': 450, '2026-09-06': 450,
      },
      screenMinutesByDate: {
        '2026-09-01': 240, '2026-09-02': 240, '2026-09-05': 60, '2026-09-06': 60,
      },
    })
    expect(r.map((c) => c.factor)).toEqual(['sleep', 'screen_time'])
  })
})

describe('describeCorrelate', () => {
  it('states both numbers and both denominators, and does not claim the night before', () => {
    const s = describeCorrelate({
      subject: 'prayer:fajr', label: 'Fajr', emoji: '🌅', factor: 'sleep',
      missedAvgMinutes: 305, doneAvgMinutes: 440, missedDays: 4, doneDays: 3,
    })
    expect(s).toBe('Fajr: sleep recorded that day averaged 5h 5m on the 4 days it was missed, 7h 20m on the 3 it was done')
    expect(s).not.toMatch(/night before/)
  })

  it('words screen time plainly', () => {
    const s = describeCorrelate({
      subject: 'habit:h1', label: 'Reading', emoji: '📖', factor: 'screen_time',
      missedAvgMinutes: 230, doneAvgMinutes: 100, missedDays: 4, doneDays: 3,
    })
    expect(s).toBe('Reading: phone screen time averaged 3h 50m on the 4 days it was missed, 1h 40m on the 3 it was done')
  })
})

describe('hm', () => {
  it('formats minutes as hours and minutes', () => {
    expect(hm(45)).toBe('45m')
    expect(hm(120)).toBe('2h')
    expect(hm(310)).toBe('5h 10m')
    expect(hm(0)).toBe('0m')
  })
})
