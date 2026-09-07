import { describe, it, expect } from 'vitest'
import { liftFlattenedBlock, cleanTime, normalizeProposal, type PlanProposal } from './plan'

/**
 * Models flatten nested objects. Measured live on claude-sonnet-5: every step
 * arrived with its fields beside "kind" rather than inside the block. The
 * schema tolerates unknown keys, so it validated and was then dropped — a
 * whole plan lost to punctuation. These pin the recovery.
 */

const base = { title: 'Move the charger', emoji: '🔌', reason: 'So the phone is out of reach at 11pm.' }

describe('liftFlattenedBlock', () => {
  it('re-nests a flattened task', () => {
    const flat = { kind: 'task', ...base, priority: 'high', due_time: '22:45', note: 'Kitchen counter.' } as unknown as PlanProposal
    expect(liftFlattenedBlock(flat).task).toEqual({ priority: 'high', due_time: '22:45', note: 'Kitchen counter.' })
  })

  it('re-nests a flattened habit', () => {
    const flat = { kind: 'habit', ...base, type: 'duration', time_target_mins: 20, schedule_kind: 'daily' } as unknown as PlanProposal
    expect(liftFlattenedBlock(flat).habit).toEqual({ type: 'duration', time_target_mins: 20, schedule_kind: 'daily' })
  })

  it('re-nests a flattened challenge', () => {
    const flat = { kind: 'challenge', ...base, frequency: 'daily', duration_days: 14, requires_photo: false } as unknown as PlanProposal
    expect(liftFlattenedBlock(flat).challenge).toEqual({ frequency: 'daily', duration_days: 14, requires_photo: false })
  })

  it('leaves a correctly nested step untouched', () => {
    const good: PlanProposal = { kind: 'task', ...base, task: { priority: 'low' } }
    expect(liftFlattenedBlock(good)).toBe(good)
  })

  it('takes only the fields that belong to that kind', () => {
    const flat = { kind: 'task', ...base, priority: 'high', duration_days: 14, schedule_kind: 'daily' } as unknown as PlanProposal
    expect(liftFlattenedBlock(flat).task).toEqual({ priority: 'high' })
  })

  it('leaves a step with nothing to lift alone', () => {
    const bare = { kind: 'task', ...base } as unknown as PlanProposal
    expect(liftFlattenedBlock(bare).task).toBeUndefined()
  })
})

describe('cleanTime', () => {
  it('recovers a time with a stray character', () => {
    // Seen live: Claude wrote "22:45," with the comma inside the value.
    expect(cleanTime('22:45,')).toBe('22:45')
    expect(cleanTime(' 09:05 ')).toBe('09:05')
  })

  it('zero-pads a single-digit hour', () => {
    expect(cleanTime('9:05')).toBe('09:05')
  })

  it('rejects anything that is not a time', () => {
    expect(cleanTime('later')).toBeUndefined()
    expect(cleanTime('25:00')).toBeUndefined()
    expect(cleanTime('12:75')).toBeUndefined()
    expect(cleanTime(undefined)).toBeUndefined()
    expect(cleanTime(930)).toBeUndefined()
  })
})

describe('normalizeProposal accepts what the model actually sends', () => {
  it('accepts a fully flattened task and drops a malformed time', () => {
    const flat = { kind: 'task', ...base, priority: 'high', due_time: 'sometime' } as unknown as PlanProposal
    const r = normalizeProposal(flat)
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.proposal.task).toEqual({ priority: 'high', due_time: undefined })
  })

  it('accepts a flattened habit and still applies its rules', () => {
    // Flattened AND with the minutes in the wrong field — both repairs at once.
    const flat = { kind: 'habit', ...base, type: 'duration', target_value: 20, unit: 'minutes', schedule_kind: 'daily' } as unknown as PlanProposal
    const r = normalizeProposal(flat)
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.proposal.habit).toMatchObject({ type: 'duration', time_target_mins: 20 })
  })

  it('still rejects a step with no usable fields at all', () => {
    expect(normalizeProposal({ kind: 'task', ...base } as unknown as PlanProposal).ok).toBe(false)
  })
})
