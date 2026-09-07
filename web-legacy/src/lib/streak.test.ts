import { describe, it, expect } from 'vitest'
import { currentStreak, type StreakHabit, type StreakLog } from './streak'

/**
 * The behaviour the stored current_streak column was failing to show: a missed
 * day breaks the run immediately, rather than the old number sitting on screen
 * until the next time something is logged.
 */

const TODAY = '2026-09-07' // Monday
const daily: StreakHabit = { type: 'simple' }

function done(...dates: string[]): StreakLog[] {
  return dates.map((date) => ({ date, completed: true, value: 1 }))
}

describe('a missed day breaks the streak', () => {
  it('counts consecutive completed days', () => {
    expect(currentStreak(daily, done('2026-09-07', '2026-09-06', '2026-09-05'), TODAY)).toBe(3)
  })

  it('drops to zero when yesterday was missed and today is not done', () => {
    // Read Fri and Sat, nothing Sunday, nothing yet today. The old stored
    // column would still be showing 2.
    expect(currentStreak(daily, done('2026-09-04', '2026-09-05'), TODAY)).toBe(0)
  })

  it('counts only today when yesterday was missed but today is done', () => {
    expect(currentStreak(daily, done('2026-09-07', '2026-09-05'), TODAY)).toBe(1)
  })

  it('stops at the gap rather than counting everything ever done', () => {
    const logs = done('2026-09-07', '2026-09-06', '2026-09-04', '2026-09-03')
    expect(currentStreak(daily, logs, TODAY)).toBe(2) // the 5th breaks it
  })

  it('is zero with no logs at all', () => {
    expect(currentStreak(daily, [], TODAY)).toBe(0)
  })
})

describe('today is still in progress', () => {
  it('keeps yesterday-and-back standing when today is not done yet', () => {
    // The streak must not wipe itself at midnight just because the day is new.
    expect(currentStreak(daily, done('2026-09-06', '2026-09-05', '2026-09-04'), TODAY)).toBe(3)
  })

  it('includes today once it is done', () => {
    const logs = done('2026-09-07', '2026-09-06', '2026-09-05', '2026-09-04')
    expect(currentStreak(daily, logs, TODAY)).toBe(4)
  })
})

describe('rest days are not failures', () => {
  const weekdaysOnly: StreakHabit = {
    type: 'simple', schedule_kind: 'weekdays',
    schedule_days: ['mon', 'tue', 'wed', 'thu', 'fri'],
  }

  it('skips unscheduled days instead of breaking on them', () => {
    // Mon 7th done, Fri 4th done. Sat and Sun are not scheduled, so the run
    // continues across them.
    expect(currentStreak(weekdaysOnly, done('2026-09-07', '2026-09-04'), TODAY)).toBe(2)
  })

  it('still breaks on a missed scheduled day', () => {
    // Thursday the 3rd was scheduled and missed.
    const logs = done('2026-09-07', '2026-09-04', '2026-09-02')
    expect(currentStreak(weekdaysOnly, logs, TODAY)).toBe(2)
  })

  it('reports zero for a paused habit', () => {
    const paused: StreakHabit = { type: 'simple', is_paused: true }
    expect(currentStreak(paused, done('2026-09-07', '2026-09-06'), TODAY)).toBe(0)
  })
})

describe('a log only counts when it met the target', () => {
  it('needs the counter target, not just an entry', () => {
    const counter: StreakHabit = { type: 'counter', target_value: 5 }
    const short = [{ date: '2026-09-06', completed: true, value: 2 }]
    expect(currentStreak(counter, short, TODAY)).toBe(0)

    const met = [{ date: '2026-09-06', completed: true, value: 5 }]
    expect(currentStreak(counter, met, TODAY)).toBe(1)
  })

  it('needs the duration target', () => {
    const duration: StreakHabit = { type: 'duration', time_target_mins: 20 }
    expect(currentStreak(duration, [{ date: '2026-09-06', completed: true, duration_mins: 5 }], TODAY)).toBe(0)
    expect(currentStreak(duration, [{ date: '2026-09-06', completed: true, duration_mins: 20 }], TODAY)).toBe(1)
  })

  it('ignores a row explicitly marked not completed', () => {
    const logs = [{ date: '2026-09-06', completed: false, value: 0 }]
    expect(currentStreak(daily, logs, TODAY)).toBe(0)
  })
})

describe('boundaries', () => {
  it('crosses months and years', () => {
    expect(currentStreak(daily, done('2026-01-01', '2025-12-31', '2025-12-30'), '2026-01-01')).toBe(3)
  })

  it('does not run away on a long unbroken history', () => {
    const dates: string[] = []
    const d = new Date('2026-09-07T12:00:00Z')
    for (let i = 0; i < 500; i++) {
      dates.push(d.toISOString().slice(0, 10))
      d.setUTCDate(d.getUTCDate() - 1)
    }
    expect(currentStreak(daily, done(...dates), TODAY)).toBe(400) // capped lookback
  })
})
