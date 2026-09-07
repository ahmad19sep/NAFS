import { describe, it, expect } from 'vitest'
import { findRepeatedMisses, describeMiss, type MissInput } from './misses'

/**
 * Repeated-miss detection. The property every test here protects:
 * unrecorded is not missed. Only what the records actually show counts.
 */

const TODAY = '2026-09-08' // Tuesday; the window is Sep 1–7
const days = ['2026-09-01', '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-05', '2026-09-06', '2026-09-07']

const reading = { id: 'h1', name: 'Reading', emoji: '📖', type: 'simple' }

function base(over: Partial<MissInput> = {}): MissInput {
  return { today: TODAY, habits: [], habitLogs: [], prayerLogs: [], deenEnabled: true, ...over }
}

/** A prayer row where every prayer was recorded, with fajr as given. */
function prayerRow(date: string, fajr: number) {
  return { date, fajr, dhuhr: 1, asr: 1, maghrib: 1, isha: 1 }
}

describe('habits — a miss needs an active day', () => {
  it('reports a habit skipped on days the user was otherwise active', () => {
    // Active every day (a prayer row exists), but Reading only done twice.
    const r = findRepeatedMisses(base({
      habits: [reading],
      prayerLogs: days.map((d) => prayerRow(d, 1)),
      habitLogs: [
        { habit_id: 'h1', date: '2026-09-01', completed: true, value: 1 },
        { habit_id: 'h1', date: '2026-09-02', completed: true, value: 1 },
      ],
    }))
    expect(r).toHaveLength(1)
    expect(r[0]).toMatchObject({ kind: 'habit', label: 'Reading', missed: 5, of: 7 })
  })

  it('does not count a day with nothing logged at all', () => {
    // Nothing recorded on any day. That is "forgot the app", not "skipped Reading".
    const r = findRepeatedMisses(base({ habits: [reading] }))
    expect(r).toHaveLength(0)
  })

  it('only counts scheduled days', () => {
    const weekdays = { ...reading, schedule_kind: 'weekdays', schedule_days: ['mon', 'wed', 'fri'] }
    const r = findRepeatedMisses(base({
      habits: [weekdays],
      prayerLogs: days.map((d) => prayerRow(d, 1)),
    }))
    // Sep 1 Tue, 2 Wed, 3 Thu, 4 Fri, 5 Sat, 6 Sun, 7 Mon → Wed, Fri, Mon scheduled = 3
    expect(r[0]).toMatchObject({ missed: 3, of: 3 })
  })

  it('leaves a paused habit alone', () => {
    const r = findRepeatedMisses(base({
      habits: [{ ...reading, is_paused: true }],
      prayerLogs: days.map((d) => prayerRow(d, 1)),
    }))
    expect(r).toHaveLength(0)
  })

  it('stays quiet below the threshold', () => {
    const r = findRepeatedMisses(base({
      habits: [reading],
      prayerLogs: days.map((d) => prayerRow(d, 1)),
      habitLogs: days.slice(0, 5).map((d) => ({ habit_id: 'h1', date: d, completed: true, value: 1 })),
    }))
    expect(r).toHaveLength(0) // 2 misses < 3
  })

  it('needs the target met for counter and duration habits', () => {
    const pages = { id: 'h2', name: 'Pages', emoji: '📖', type: 'counter', target_value: 10 }
    const r = findRepeatedMisses(base({
      habits: [pages],
      prayerLogs: days.map((d) => prayerRow(d, 1)),
      habitLogs: days.map((d) => ({ habit_id: 'h2', date: d, completed: true, value: 3 })),
    }))
    expect(r[0]).toMatchObject({ missed: 7, of: 7 })
  })
})

describe('prayers — only an explicit miss is a miss', () => {
  it('counts a prayer recorded as missed', () => {
    const r = findRepeatedMisses(base({
      prayerLogs: days.map((d, i) => prayerRow(d, i < 4 ? 0 : 1)), // fajr missed 4 of 7
    }))
    expect(r).toHaveLength(1)
    expect(r[0]).toMatchObject({ kind: 'prayer', label: 'Fajr', missed: 4, of: 7 })
  })

  it('never counts a day with no prayer row as a miss', () => {
    // Only three days recorded, fajr missed on all three. The other four are
    // unknown and must not inflate the count.
    const r = findRepeatedMisses(base({
      prayerLogs: days.slice(0, 3).map((d) => prayerRow(d, 0)),
    }))
    expect(r[0]).toMatchObject({ missed: 3, of: 3 })
  })

  it('reports nothing when faith mode is off', () => {
    const r = findRepeatedMisses(base({
      deenEnabled: false,
      prayerLogs: days.map((d) => prayerRow(d, 0)),
    }))
    expect(r).toHaveLength(0)
  })
})

describe('shape and wording', () => {
  it('excludes today, which is still in progress', () => {
    const r = findRepeatedMisses(base({
      habits: [reading],
      // Active and skipped on today only.
      prayerLogs: [prayerRow(TODAY, 1)],
    }))
    expect(r).toHaveLength(0)
  })

  it('orders the worst pattern first', () => {
    const r = findRepeatedMisses(base({
      habits: [reading],
      prayerLogs: days.map((d, i) => prayerRow(d, i < 6 ? 0 : 1)), // fajr missed 6
    }))
    // Reading missed 7 of 7 (active every day, never done); Fajr 6 of 7.
    expect(r.map((m) => m.label)).toEqual(['Reading', 'Fajr'])
  })

  it('describes a miss with its denominator and the right verb', () => {
    const dates = { missedDates: [], doneDates: [] }
    expect(describeMiss({ kind: 'prayer', id: 'fajr', label: 'Fajr', emoji: '🌅', missed: 4, of: 7, windowDays: 7, ...dates }))
      .toBe('Fajr recorded missed 4 of the last 7 recorded days')
    expect(describeMiss({ kind: 'habit', id: 'h1', label: 'Reading', emoji: '📖', missed: 3, of: 5, windowDays: 7, ...dates }))
      .toBe('Reading not done 3 of the last 5 active days')
  })

  it('returns the days behind the counts', () => {
    const r = findRepeatedMisses(base({
      prayerLogs: days.map((d, i) => prayerRow(d, i < 4 ? 0 : 1)),
    }))
    expect(r[0].missedDates).toEqual(days.slice(0, 4))
    expect(r[0].doneDates).toEqual(days.slice(4))
  })
})
