import { describe, it, expect } from 'vitest'
import {
  normalizeProposal, describeProposal, proposalToCreateBody, type PlanProposal,
} from './plan'

/**
 * The planner's pure rules. The model proposes; these decide whether a
 * proposal is usable, how to describe it, and which create route records it.
 */

const twelveHours: PlanProposal = {
  kind: 'habit', title: 'Work 12 hours', emoji: '💼',
  reason: 'You said every day, so this is an ongoing habit measured in time.',
  habit: { type: 'duration', time_target_mins: 720, schedule_kind: 'daily' },
}

describe('normalizeProposal — what the schema cannot check', () => {
  it('passes a complete proposal through', () => {
    const r = normalizeProposal(twelveHours)
    expect(r.ok).toBe(true)
  })

  it('rejects a kind with no matching block', () => {
    const r = normalizeProposal({ kind: 'challenge', title: 'X', emoji: '🎯', reason: 'because reasons' })
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.error).toMatch(/incomplete/i)
  })

  it('rejects a duration habit with no minutes', () => {
    const r = normalizeProposal({
      ...twelveHours, habit: { type: 'duration', schedule_kind: 'daily' },
    })
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.error).toMatch(/minutes/i)
  })

  it('rejects a counter habit with no target', () => {
    const r = normalizeProposal({
      ...twelveHours, habit: { type: 'counter', unit: 'pages', schedule_kind: 'daily' },
    })
    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.error).toMatch(/how many/i)
  })

  it('downgrades a weekday schedule with no days to daily instead of failing', () => {
    const r = normalizeProposal({
      ...twelveHours, habit: { ...twelveHours.habit!, schedule_kind: 'weekdays', schedule_days: [] },
    })
    expect(r.ok).toBe(true)
    if (r.ok) expect(r.proposal.habit!.schedule_kind).toBe('daily')
  })

  it('clamps a challenge length into range and defaults a missing one', () => {
    const base = { kind: 'challenge' as const, title: 'X', emoji: '🎯', reason: 'a fixed run' }
    const huge = normalizeProposal({ ...base, challenge: { frequency: 'daily', duration_days: 99999, requires_photo: false } })
    if (huge.ok) expect(huge.proposal.challenge!.duration_days).toBe(1825)
    const none = normalizeProposal({ ...base, challenge: { frequency: 'daily', duration_days: 0 as any, requires_photo: false } })
    if (none.ok) expect(none.proposal.challenge!.duration_days).toBe(30)
  })

  it('does not throw on garbage', () => {
    expect(normalizeProposal(null as any).ok).toBe(false)
    expect(normalizeProposal({} as any).ok).toBe(false)
  })
})

describe('describeProposal — one honest line', () => {
  it('describes a duration habit in hours when it divides cleanly', () => {
    expect(describeProposal(twelveHours)).toBe('12h a day · daily')
  })

  it('falls back to minutes otherwise', () => {
    const p = { ...twelveHours, habit: { type: 'duration' as const, time_target_mins: 25, schedule_kind: 'daily' as const } }
    expect(describeProposal(p)).toBe('25 min a day · daily')
  })

  it('describes a counter with its unit', () => {
    const p: PlanProposal = {
      kind: 'habit', title: 'Read', emoji: '📖', reason: 'ongoing',
      habit: { type: 'counter', target_value: 10, unit: 'pages', schedule_kind: 'daily' },
    }
    expect(describeProposal(p)).toBe('10 pages a day · daily')
  })

  it('names the days for a weekday schedule', () => {
    const p: PlanProposal = {
      kind: 'habit', title: 'Gym', emoji: '🏋️', reason: 'ongoing',
      habit: { type: 'simple', schedule_kind: 'weekdays', schedule_days: ['mon', 'wed', 'fri'] },
    }
    expect(describeProposal(p)).toBe('yes / no each day · Mon · Wed · Fri')
  })

  it('describes a challenge by length and cadence', () => {
    const p: PlanProposal = {
      kind: 'challenge', title: '30 days of deep work', emoji: '🎯', reason: 'fixed run',
      challenge: { frequency: 'daily', duration_days: 30, requires_photo: true },
    }
    expect(describeProposal(p)).toBe('30 days · daily check-in · photo proof')
  })

  it('only mentions a due time the user actually set', () => {
    const p: PlanProposal = {
      kind: 'task', title: 'Tax return', emoji: '🧾', reason: 'one-off',
      task: { priority: 'high' },
    }
    expect(describeProposal(p)).toBe('high priority')
    expect(describeProposal({ ...p, task: { priority: 'high', due_time: '17:00' } })).toBe('high priority · due 17:00')
  })
})

describe('proposalToCreateBody — the right route with the right fields', () => {
  it('sends a habit to /api/habits with the duration in minutes', () => {
    const r = proposalToCreateBody(twelveHours, 'req-1')
    expect(r.path).toBe('/api/habits')
    expect(r.href).toBe('/habits')
    expect(r.body).toMatchObject({
      name: 'Work 12 hours', type: 'duration', time_target_mins: 720,
      schedule_kind: 'daily', schedule_days: null, why: twelveHours.reason,
    })
  })

  it('sends a task to /api/tasks carrying the request id', () => {
    const p: PlanProposal = {
      kind: 'task', title: 'Tax return', emoji: '🧾', reason: 'one-off',
      task: { priority: 'high', due_time: '17:00' },
    }
    const r = proposalToCreateBody(p, 'req-2')
    expect(r.path).toBe('/api/tasks')
    expect(r.body).toMatchObject({ title: 'Tax return', priority: 'high', due_time: '17:00', type: 'daily', request_id: 'req-2' })
  })

  it('sends a challenge to /api/challenges with the reason as its description', () => {
    const p: PlanProposal = {
      kind: 'challenge', title: '30 days of deep work', emoji: '🎯', reason: 'fixed run',
      challenge: { frequency: 'daily', duration_days: 30, requires_photo: false },
    }
    const r = proposalToCreateBody(p, 'req-3')
    expect(r.path).toBe('/api/challenges')
    expect(r.body).toMatchObject({ title: '30 days of deep work', description: 'fixed run', duration_days: 30, request_id: 'req-3' })
  })

  it('does not carry a counter target onto a simple habit', () => {
    const p: PlanProposal = {
      kind: 'habit', title: 'Pray Fajr', emoji: '🕌', reason: 'ongoing',
      habit: { type: 'simple', schedule_kind: 'daily', target_value: 99 },
    }
    expect(proposalToCreateBody(p, 'r').body).toMatchObject({ type: 'simple', target_value: 1, time_target_mins: 0 })
  })
})
