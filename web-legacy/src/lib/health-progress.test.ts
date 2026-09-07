import { describe, it, expect } from 'vitest'
import { healthProgress } from './health-progress'

/**
 * UI-01 / fixture D14: Home and the Health page must show the same denominator
 * for the same day under the same enabled-module configuration.
 *
 * They disagreed before — Home hardcoded 4 (water, steps, sleep, exercise)
 * while Health derived its own from the enabled categories. Both were
 * self-consistent, which is what made it confusing rather than obviously
 * broken. There is now one selector; these pin what it counts.
 */

const DEFAULT_HIDDEN = ['water', 'steps', 'exercise', 'weight']

describe('D14 — one denominator, whatever is enabled', () => {
  it('counts only the permanent two when the optional ones are hidden', () => {
    // health_optin.sql hides the built-ins by default, so a new account tracks
    // sleep and meals and nothing else. Home used to insist on 4 regardless.
    const p = healthProgress(null, DEFAULT_HIDDEN, [])
    expect(p.total).toBe(2)
    expect(p.categories.map((c) => c.id)).toEqual(['sleep', 'meals'])
  })

  it('grows as optional metrics are re-added', () => {
    expect(healthProgress(null, ['steps', 'exercise'], []).total).toBe(3) // + water
    expect(healthProgress(null, [], []).total).toBe(5)                    // all three
  })

  it('includes configured custom metrics', () => {
    const extras = [{ id: 'vit-d', name: 'Vitamin D', emoji: '💊', type: 'boolean' as const }]
    expect(healthProgress(null, DEFAULT_HIDDEN, extras).total).toBe(3)
  })

  it('gives the same answer for the stored row and the on-screen values', () => {
    // Home reads the saved row; Health passes what is in its form. The same
    // content must produce the same numbers, or the two screens drift again.
    const row = {
      sleep_sessions: [{ id: 's', start: '23:00', end: '06:30' }],
      meals: [],
      water_glasses: 6,
      steps: 8000,
      exercise_done: false,
    }
    const fromHome = healthProgress(row, ['exercise'], [])
    const fromPage = healthProgress({ ...row }, ['exercise'], [])
    expect(fromHome).toEqual(fromPage)
    expect(fromHome.total).toBe(4)  // sleep, meals, water, steps
    expect(fromHome.done).toBe(3)   // meals not recorded
  })
})

describe('what counts as done', () => {
  it('counts sleep from sessions', () => {
    const p = healthProgress(
      { sleep_sessions: [{ id: 's', start: '23:00', end: '06:30' }] }, DEFAULT_HIDDEN, [])
    expect(p.categories.find((c) => c.id === 'sleep')!.done).toBe(true)
  })

  it('still counts sleep on rows saved before sessions existed', () => {
    const p = healthProgress({ sleep_hours: 7 }, DEFAULT_HIDDEN, [])
    expect(p.categories.find((c) => c.id === 'sleep')!.done).toBe(true)
  })

  it('does not count a session with no real duration', () => {
    const p = healthProgress(
      { sleep_sessions: [{ id: 's', start: '', end: '' }] }, DEFAULT_HIDDEN, [])
    expect(p.categories.find((c) => c.id === 'sleep')!.done).toBe(false)
  })

  it('counts meals only when something was actually eaten', () => {
    const empty = healthProgress({ meals: [] }, DEFAULT_HIDDEN, [])
    expect(empty.categories.find((c) => c.id === 'meals')!.done).toBe(false)

    const eaten = healthProgress({
      meals: [{ id: 'm', key: 'breakfast', label: 'Breakfast', emoji: '🌅',
        items: [{ id: 'f', name: 'Naan', emoji: '🫓' }] }] as any,
    }, DEFAULT_HIDDEN, [])
    expect(eaten.categories.find((c) => c.id === 'meals')!.done).toBe(true)
  })

  it('treats an empty meal slot as not eaten', () => {
    // Creating a meal container is not food intake — the blueprint calls this
    // out directly.
    const p = healthProgress({
      meals: [{ id: 'm', key: 'dinner', label: 'Dinner', emoji: '🌙', items: [] }] as any,
    }, DEFAULT_HIDDEN, [])
    expect(p.categories.find((c) => c.id === 'meals')!.done).toBe(false)
  })

  it('never reports done above total', () => {
    const p = healthProgress(
      { sleep_hours: 8, water_glasses: 9, steps: 12000, exercise_done: true }, [], [])
    expect(p.done).toBeLessThanOrEqual(p.total)
  })

  it('reports nothing done for a day with no row at all', () => {
    const p = healthProgress(null, [], [])
    expect(p.done).toBe(0)
    expect(p.total).toBe(5)
  })
})
