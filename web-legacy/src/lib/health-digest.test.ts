import { describe, it, expect } from 'vitest'
import { buildHealthDigest, describeHealthDigest, type HealthLogRow } from './health-digest'

/**
 * Fixture A04 from the NAFS Improvement Blueprint, plus the missing-data rules
 * the digest exists to enforce: an unrecorded day is unknown, never a zero,
 * and every average states how many days it actually covers.
 */

const PERIOD = { startDate: '2026-09-01', endDate: '2026-09-07', eligibleDays: 7 }

function digest(rows: HealthLogRow[]) {
  return buildHealthDigest(rows, PERIOD)
}

describe('A04 — real sleep sessions are used even when legacy fields are null', () => {
  const d = digest([
    { date: '2026-09-01', sleep_sessions: [{ id: 'a', start: '23:00', end: '06:30' }] }, // 7h30
    { date: '2026-09-02', sleep_sessions: [{ id: 'b', start: '00:30', end: '06:00' }] }, // 5h30
  ])

  it('reads the sessions rather than the removed profile fields', () => {
    expect(d.sleep.recordedNights).toBe(2)
    expect(d.sleep.meanMinutes).toBe(390) // (450 + 330) / 2
    expect(d.sleep.shortestMinutes).toBe(330)
    expect(d.sleep.longestMinutes).toBe(450)
  })

  it('states coverage rather than implying a full week', () => {
    expect(d.sleep.eligibleNights).toBe(7)
    expect(d.limitations.join(' ')).toMatch(/recorded on 2 of 7/i)
  })
})

describe('no sleep recorded stays unknown, never zero', () => {
  const d = digest([{ date: '2026-09-01', water_glasses: 4 }])

  it('reports null, not 0', () => {
    expect(d.sleep.meanMinutes).toBeNull()
    expect(d.sleep.recordedNights).toBe(0)
  })

  it('says so explicitly for the prompt', () => {
    expect(d.limitations.join(' ')).toMatch(/no sleep was recorded/i)
    expect(describeHealthDigest(d)).toMatch(/not recorded/)
  })
})

describe('naps and overlapping sessions', () => {
  it('counts a night plus a nap as one night with a nap', () => {
    const d = digest([{
      date: '2026-09-01',
      sleep_sessions: [
        { id: 'n', start: '23:00', end: '06:30' }, // 450
        { id: 'p', start: '14:00', end: '14:45' }, //  45
      ],
    }])
    expect(d.sleep.meanMinutes).toBe(495)
    expect(d.sleep.nightsWithNaps).toBe(1)
  })

  it('does not count an overlapping session twice', () => {
    // The same night entered twice — real double counting, not extra sleep.
    const d = digest([{
      date: '2026-09-01',
      sleep_sessions: [
        { id: 'a', start: '23:00', end: '06:30' },
        { id: 'b', start: '23:30', end: '06:00' },
      ],
    }])
    expect(d.sleep.meanMinutes).toBe(450)
    expect(d.sleep.overlappingSessionsIgnored).toBe(1)
    expect(d.limitations.join(' ')).toMatch(/overlapped/i)
  })
})

describe('meals', () => {
  const d = digest([
    {
      date: '2026-09-01',
      meals: [
        { id: 'm1', key: 'breakfast', label: 'Breakfast', emoji: '🌅',
          items: [{ id: 'f1', name: 'Naan', emoji: '🫓' }, { id: 'f2', name: 'Chai', emoji: '☕' }] },
        { id: 'm2', key: 'lunch', label: 'Lunch', emoji: '☀️',
          items: [{ id: 'f3', name: 'Chicken Biryani', emoji: '🍛' }] },
        { id: 'm3', key: 'dinner', label: 'Dinner', emoji: '🌙', items: [] },
      ] as any,
    },
  ])

  it('counts only meals that actually hold food', () => {
    // An empty dinner slot is not a meal eaten.
    expect(d.meals.recordedMeals).toBe(2)
    expect(d.meals.daysWithAnyMeal).toBe(1)
    expect(d.meals.totalFoods).toBe(3)
  })

  it('groups foods by menu category so the model reasons over types', () => {
    expect(d.meals.categoryCounts.bread).toBe(1)   // Naan
    expect(d.meals.categoryCounts.drink).toBe(1)   // Chai
    expect(d.meals.categoryCounts.rice).toBe(1)    // Chicken Biryani
  })

  it('marks hand-typed foods as unknown instead of guessing a category', () => {
    const custom = digest([{
      date: '2026-09-01',
      meals: [{ id: 'm', key: 'breakfast', label: 'Breakfast', emoji: '🌅',
        items: [{ id: 'f', name: 'Grandmothers special stew', emoji: '🍽️' }] }] as any,
    }])
    expect(custom.meals.unknownFoods).toBe(1)
    expect(Object.keys(custom.meals.categoryCounts)).toHaveLength(0)
    expect(custom.limitations.join(' ')).toMatch(/not in the menu/i)
  })

  it('reports no meals as unrecorded, not as fasting or skipping', () => {
    const none = digest([{ date: '2026-09-01', steps: 4000 }])
    expect(none.meals.daysWithAnyMeal).toBe(0)
    expect(none.meals.meanMealsPerRecordedDay).toBeNull()
    expect(none.limitations.join(' ')).toMatch(/no meals were recorded/i)
  })
})

describe('exercise recorded without a duration', () => {
  const d = digest([
    { date: '2026-09-01', exercise_done: true },
    { date: '2026-09-02', exercise_done: true, exercise_minutes: 30 },
  ])

  it('keeps duration unknown rather than asserting zero minutes', () => {
    expect(d.exercise.daysExercised).toBe(2)
    expect(d.exercise.totalMinutes).toBe(30)
    expect(d.limitations.join(' ')).toMatch(/no duration recorded/i)
  })
})

describe('the rendered prompt', () => {
  it('always states coverage alongside every average', () => {
    const text = describeHealthDigest(digest([
      { date: '2026-09-01', water_glasses: 6, steps: 8000 },
    ]))
    expect(text).toMatch(/Recorded on \d+ of 7 nights/)
    expect(text).toMatch(/of 7 days/)
    expect(text).toMatch(/recorded days/)
  })

  it('lists what is not known, so gaps are not filled with assumptions', () => {
    const text = describeHealthDigest(digest([]))
    expect(text).toMatch(/WHAT IS NOT KNOWN/)
    expect(text).toMatch(/do not fill these gaps/i)
  })
})
