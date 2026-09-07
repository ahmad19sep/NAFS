import { describe, it, expect } from 'vitest'
import { levelFor, countDaysLogged, LEVELS } from './levels'

describe('levels only ever go up', () => {
  it('has no level before anything is recorded', () => {
    const p = levelFor(0)
    expect(p.current).toBeNull()
    expect(p.next?.at).toBe(1)
  })

  it('reaches the first level on the first day', () => {
    expect(levelFor(1).current?.name).toBe('Day one')
  })

  it('never drops as days accumulate', () => {
    let last = 0
    for (let d = 0; d <= 800; d++) {
      const n = levelFor(d).current?.number ?? 0
      expect(n).toBeGreaterThanOrEqual(last)
      last = n
    }
  })

  it('lands exactly on each threshold', () => {
    for (const l of LEVELS) {
      expect(levelFor(l.at).current?.number).toBe(l.number)
      // One day short is still the previous level.
      if (l.at > 1) expect(levelFor(l.at - 1).current?.number).toBe(l.number - 1)
    }
  })

  it('caps at the last level without breaking', () => {
    const p = levelFor(10_000)
    expect(p.current?.number).toBe(LEVELS[LEVELS.length - 1].number)
    expect(p.next).toBeNull()
    expect(p.daysToNext).toBeNull()
    expect(p.pctToNext).toBe(100)
  })
})

describe('progress toward the next level', () => {
  it('counts the days still needed', () => {
    expect(levelFor(5).daysToNext).toBe(2)   // 5 -> 7
    expect(levelFor(29).daysToNext).toBe(1)  // 29 -> 30
  })

  it('measures across the band, not from zero', () => {
    // Level 4 at 14, level 5 at 30. Day 22 is halfway through that band.
    expect(levelFor(22).pctToNext).toBe(50)
    expect(levelFor(14).pctToNext).toBe(0)
  })

  it('stays within 0–100', () => {
    for (let d = 0; d <= 800; d++) {
      const p = levelFor(d)
      expect(p.pctToNext).toBeGreaterThanOrEqual(0)
      expect(p.pctToNext).toBeLessThanOrEqual(100)
    }
  })

  it('handles junk input without throwing', () => {
    expect(levelFor(-5).current).toBeNull()
    expect(levelFor(3.7).current?.number).toBe(2) // floored
  })
})

describe('counting days logged', () => {
  it('counts a date once however many sources it appears in', () => {
    const health = [{ date: '2026-09-01' }, { date: '2026-09-02' }]
    const prayers = [{ date: '2026-09-02' }, { date: '2026-09-03' }]
    expect(countDaysLogged(health, prayers)).toBe(3)
  })

  it('deduplicates repeats within one source', () => {
    // habit_logs has a row per habit per day.
    const habits = [{ date: '2026-09-01' }, { date: '2026-09-01' }, { date: '2026-09-01' }]
    expect(countDaysLogged(habits)).toBe(1)
  })

  it('ignores rows with no usable date rather than counting them', () => {
    expect(countDaysLogged([{ date: null }, { date: '' }, {}, { date: 'nonsense' }] as any)).toBe(0)
  })

  it('tolerates a full timestamp', () => {
    expect(countDaysLogged([{ date: '2026-09-01T10:00:00Z' }])).toBe(1)
  })

  it('handles missing sources', () => {
    expect(countDaysLogged(null, undefined, [])).toBe(0)
  })
})
