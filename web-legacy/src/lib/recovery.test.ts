import { describe, it, expect } from 'vitest'
import { normalizeRecovery, normalizeProposal, minutesFrom, stripLeadingEmoji, type PlanProposal, type RecoveryPlan } from './plan'
import { nextAttemptBudget, STRUCTURED_TOTAL_BUDGET_MS } from './ai'

describe('stripLeadingEmoji', () => {
  it('removes a leading emoji so it is not shown twice', () => {
    // Seen live: emoji field "🗓️", title "📅 Gym plan".
    expect(stripLeadingEmoji('📅 Gym plan')).toBe('Gym plan')
    expect(stripLeadingEmoji('🚫📱 No phone after 10pm')).toBe('No phone after 10pm')
    expect(stripLeadingEmoji('🏋️‍♂️ Gym 3×/week')).toBe('Gym 3×/week')
  })

  it('leaves an ordinary title alone', () => {
    expect(stripLeadingEmoji('Move the charger to the kitchen')).toBe('Move the charger to the kitchen')
    expect(stripLeadingEmoji('  Read 20 min  ')).toBe('Read 20 min')
  })

  it('keeps the original when stripping would leave nothing', () => {
    expect(stripLeadingEmoji('🔥')).toBe('🔥')
    expect(stripLeadingEmoji('📅 x')).toBe('📅 x')
  })

  it('does not eat a leading digit or punctuation', () => {
    expect(stripLeadingEmoji('10 pages a day')).toBe('10 pages a day')
    expect(stripLeadingEmoji('"No phone" rule')).toBe('"No phone" rule')
  })
})

describe('nextAttemptBudget', () => {
  it('gives the first attempt a full-length budget', () => {
    expect(nextAttemptBudget(0, 1)).toBe(45_000)
  })

  it('shrinks a retry to the time that is actually left', () => {
    expect(nextAttemptBudget(20_000, 2)).toBe(30_000)
  })

  it('refuses a retry that could not finish', () => {
    expect(nextAttemptBudget(STRUCTURED_TOTAL_BUDGET_MS - 5_000, 2)).toBeNull()
  })

  it('never returns a uselessly short budget', () => {
    expect(nextAttemptBudget(STRUCTURED_TOTAL_BUDGET_MS + 10_000, 1)).toBe(5_000)
  })
})

describe('minutesFrom', () => {
  it('reads minute and hour units, and nothing else', () => {
    expect(minutesFrom(60, 'minutes')).toBe(60)
    expect(minutesFrom(45, 'min')).toBe(45)
    expect(minutesFrom(1.5, 'hours')).toBe(90)
    expect(minutesFrom(2, 'h')).toBe(120)
    expect(minutesFrom(10, 'pages')).toBeNull()
    expect(minutesFrom(0, 'minutes')).toBeNull()
    expect(minutesFrom(undefined, 'minutes')).toBeNull()
  })
})

describe('normalizeProposal salvages minutes put in the wrong field', () => {
  it('moves target_value + a time unit into time_target_mins', () => {
    const r = normalizeProposal({
      kind: 'habit', title: 'Read', emoji: '📖', reason: 'Fills the slot.',
      habit: { type: 'duration', target_value: 60, unit: 'minutes', schedule_kind: 'daily' },
    })
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.proposal.habit).toMatchObject({ type: 'duration', time_target_mins: 60 })
    expect(r.proposal.habit?.target_value).toBeUndefined()
  })

  it('still rejects a duration habit with no time anywhere', () => {
    const r = normalizeProposal({
      kind: 'habit', title: 'Read', emoji: '📖', reason: 'Fills the slot.',
      habit: { type: 'duration', target_value: 10, unit: 'pages', schedule_kind: 'daily' },
    })
    expect(r.ok).toBe(false)
  })
})

const task = (title: string): PlanProposal => ({
  kind: 'task', title, emoji: '✅', reason: 'A first move that is small enough to do tonight.',
  task: { priority: 'high' },
})
const habit = (title: string): PlanProposal => ({
  kind: 'habit', title, emoji: '📵', reason: 'Fills the slot scrolling used to fill.',
  habit: { type: 'simple', schedule_kind: 'daily' },
})
const challenge = (title: string, days = 14): PlanProposal => ({
  kind: 'challenge', title, emoji: '🔥', reason: 'Two weeks of accountability, default length.',
  challenge: { frequency: 'daily', duration_days: days, requires_photo: false },
})

const plan = (steps: PlanProposal[], over: Partial<RecoveryPlan> = {}): RecoveryPlan => ({
  problem: 'Scrolling three hours a night', approach: 'Replace the slot and move the phone.', steps, ...over,
})

describe('normalizeRecovery', () => {
  it('keeps a good plan whole', () => {
    const r = normalizeRecovery(plan([task('Move the charger to the kitchen'), habit('Phone out of the room by 11'), challenge('No phone in bed')]))
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.plan.steps).toHaveLength(3)
    expect(r.dropped).toBe(0)
    expect(r.plan.problem).toBe('Scrolling three hours a night')
  })

  it('drops a step that cannot be created and keeps the rest', () => {
    const bad: PlanProposal = {
      kind: 'habit', title: 'Read', emoji: '📖', reason: 'Replace scrolling with reading.',
      habit: { type: 'duration', schedule_kind: 'daily' }, // no minutes
    }
    const r = normalizeRecovery(plan([task('Charger to kitchen'), bad]))
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.plan.steps.map((s) => s.title)).toEqual(['Charger to kitchen'])
    expect(r.dropped).toBe(1)
  })

  it('keeps only the first challenge', () => {
    const r = normalizeRecovery(plan([challenge('A'), habit('B'), challenge('C')]))
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.plan.steps.map((s) => s.title)).toEqual(['A', 'B'])
    expect(r.dropped).toBe(1)
  })

  it('caps at four steps', () => {
    const r = normalizeRecovery(plan([task('1'), task('2'), task('3'), habit('4'), habit('5')]))
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.plan.steps).toHaveLength(4)
  })

  it('fails when nothing survives', () => {
    const r = normalizeRecovery(plan([{ kind: 'task', title: 'x', emoji: '', reason: '' } as PlanProposal]))
    expect(r.ok).toBe(false)
  })

  it('fails on a missing steps array', () => {
    expect(normalizeRecovery({ problem: 'p', approach: 'a' } as any).ok).toBe(false)
  })

  it('applies the single-proposal rules to each step', () => {
    // A weekday schedule with no days is downgraded to daily, not rejected.
    const weekdays: PlanProposal = {
      kind: 'habit', title: 'Walk', emoji: '🚶', reason: 'Gets you out of the chair.',
      habit: { type: 'simple', schedule_kind: 'weekdays' },
    }
    const r = normalizeRecovery(plan([weekdays]))
    expect(r.ok).toBe(true)
    if (!r.ok) return
    expect(r.plan.steps[0].habit?.schedule_kind).toBe('daily')
  })
})
