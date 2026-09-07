import { describe, it, expect } from 'vitest'
import { shiftDate, todayInTZ } from './utils'

/**
 * DATA-03 date rules from the NAFS Improvement Blueprint.
 *
 * The failure this guards against: the scheduled reports ran on a fixed UTC
 * hour and derived "today" from the server's date. That lands on a different
 * calendar day depending where the account is, so an account far enough from
 * UTC got a report for the wrong day. Dates are now resolved per account with
 * todayInTZ, and shifted with shiftDate, which never touches a timezone.
 */

describe('shiftDate — pure calendar arithmetic', () => {
  it('moves back and forward by whole days', () => {
    expect(shiftDate('2026-09-08', -1)).toBe('2026-09-07')
    expect(shiftDate('2026-09-08', 1)).toBe('2026-09-09')
    expect(shiftDate('2026-09-08', 0)).toBe('2026-09-08')
  })

  it('crosses month boundaries', () => {
    expect(shiftDate('2026-09-01', -1)).toBe('2026-08-31')
    expect(shiftDate('2026-08-31', 1)).toBe('2026-09-01')
  })

  it('crosses year boundaries', () => {
    expect(shiftDate('2026-01-01', -1)).toBe('2025-12-31')
    expect(shiftDate('2025-12-31', 1)).toBe('2026-01-01')
  })

  it('handles February in a leap year and a common year', () => {
    expect(shiftDate('2028-02-28', 1)).toBe('2028-02-29') // 2028 is a leap year
    expect(shiftDate('2026-02-28', 1)).toBe('2026-03-01')
  })

  it('builds a seven-day window that ends on the given day', () => {
    const end = '2026-09-06'
    const days = Array.from({ length: 7 }, (_, i) => shiftDate(end, -(6 - i)))
    expect(days).toEqual([
      '2026-08-31', '2026-09-01', '2026-09-02', '2026-09-03',
      '2026-09-04', '2026-09-05', '2026-09-06',
    ])
    expect(days).toHaveLength(new Set(days).size) // no duplicates or gaps
  })

  it('is unaffected by the machine timezone, unlike a Date round-trip', () => {
    // The old pattern — new Date(str), setDate, toISOString — mixes local
    // getters with a UTC serialisation and can slip a day. This must not.
    for (const d of ['2026-01-01', '2026-06-15', '2026-12-31', '2026-03-29']) {
      expect(shiftDate(shiftDate(d, -1), 1)).toBe(d)
      expect(shiftDate(shiftDate(d, 7), -7)).toBe(d)
    }
  })
})

describe('todayInTZ — the account decides what day it is', () => {
  it('returns a well-formed date for a valid zone', () => {
    expect(todayInTZ('Asia/Karachi')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(todayInTZ('America/Los_Angeles')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('gives different zones dates at most one day apart', () => {
    // Two zones far apart are either on the same date or one day either side —
    // never further. This is what makes a fixed-UTC cron hour unsafe.
    const east = todayInTZ('Pacific/Auckland')
    const west = todayInTZ('Pacific/Honolulu')
    const gap = Math.abs(
      (new Date(`${east}T12:00:00Z`).getTime() - new Date(`${west}T12:00:00Z`).getTime())
      / 86_400_000,
    )
    expect(gap).toBeLessThanOrEqual(1)
  })

  it('falls back rather than throwing on a bad zone', () => {
    expect(todayInTZ('Not/AZone')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
