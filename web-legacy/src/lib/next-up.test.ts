import { describe, it, expect } from 'vitest'
import { selectNextUp, type NextUpInput } from './next-up'

/**
 * UI-01 Next Up selection rules.
 *
 * The two that matter most are negative: never invent urgency, and never let
 * the list reorder itself while someone is reaching for it.
 */

const TODAY = '2026-09-07' // a Monday

function task(over: Partial<NextUpInput['tasks'][number]> = {}) {
  return {
    id: 't1', title: 'Task', status: 'active', type: 'daily',
    period_date: TODAY, priority: 'medium', due_time: null,
    ...over,
  }
}
function habit(over: Partial<NextUpInput['habits'][number]> = {}) {
  return { id: 'h1', name: 'Habit', emoji: '📖', sort_order: 0, ...over }
}
function input(over: Partial<NextUpInput> = {}): NextUpInput {
  return {
    today: TODAY, tasks: [], habits: [], doneHabitIds: new Set<string>(),
    deenEnabled: false, prayersRecorded: 5,
    ...over,
  }
}

describe('what is excluded', () => {
  it('leaves out completed tasks', () => {
    const r = selectNextUp(input({ tasks: [task({ status: 'completed' })] }))
    expect(r).toHaveLength(0)
  })

  it('leaves out tasks for another day', () => {
    const r = selectNextUp(input({ tasks: [task({ period_date: '2026-09-06' })] }))
    expect(r).toHaveLength(0)
  })

  it('leaves out habits already done today', () => {
    const r = selectNextUp(input({ habits: [habit()], doneHabitIds: ['h1'] }))
    expect(r).toHaveLength(0)
  })

  it('leaves out paused habits', () => {
    const r = selectNextUp(input({ habits: [habit({ is_paused: true })] }))
    expect(r).toHaveLength(0)
  })

  it('leaves out habits not scheduled for today', () => {
    // Monday, scheduled Tue/Thu only.
    const r = selectNextUp(input({
      habits: [habit({ schedule_kind: 'weekdays', schedule_days: ['tue', 'thu'] })],
    }))
    expect(r).toHaveLength(0)
  })

  it('includes a habit scheduled for today', () => {
    const r = selectNextUp(input({
      habits: [habit({ schedule_kind: 'weekdays', schedule_days: ['mon'] })],
    }))
    expect(r).toHaveLength(1)
  })
})

describe('ordering', () => {
  it('puts tasks the user gave a time to first, earliest first', () => {
    const r = selectNextUp(input({
      tasks: [
        task({ id: 'late', title: 'Late', due_time: '18:00:00' }),
        task({ id: 'early', title: 'Early', due_time: '09:00:00' }),
        task({ id: 'none', title: 'Untimed', priority: 'high' }),
      ],
    }))
    expect(r.map((i) => i.label)).toEqual(['Early', 'Late', 'Untimed'])
  })

  it('orders untimed tasks by the priority the user chose', () => {
    const r = selectNextUp(input({
      tasks: [
        task({ id: 'a', title: 'Low', priority: 'low' }),
        task({ id: 'b', title: 'High', priority: 'high' }),
        task({ id: 'c', title: 'Med', priority: 'medium' }),
      ],
    }))
    expect(r.map((i) => i.label)).toEqual(['High', 'Med', 'Low'])
  })

  it('puts tasks before habits, and habits in their own order', () => {
    const r = selectNextUp(input({
      tasks: [task({ title: 'A task' })],
      habits: [habit({ id: 'h2', name: 'Second', sort_order: 2 }),
               habit({ id: 'h1', name: 'First', sort_order: 1 })],
    }))
    expect(r.map((i) => i.label)).toEqual(['A task', 'First', 'Second'])
  })

  it('shows at most three', () => {
    const tasks = Array.from({ length: 8 }, (_, i) => task({ id: `t${i}`, title: `T${i}` }))
    expect(selectNextUp(input({ tasks }))).toHaveLength(3)
  })

  it('gives the same answer every time it is called', () => {
    // The list must not reshuffle mid-interaction, so nothing may depend on
    // the clock or on iteration order.
    const args = input({
      tasks: [task({ id: 'b', title: 'B' }), task({ id: 'a', title: 'A' })],
      habits: [habit({ id: 'h2', name: 'H2', sort_order: 0 }),
               habit({ id: 'h1', name: 'H1', sort_order: 0 })],
    })
    const first = selectNextUp(args)
    for (let i = 0; i < 5; i++) expect(selectNextUp(args)).toEqual(first)
  })
})

describe('never inventing urgency', () => {
  it('labels a due time only when the user set one', () => {
    const r = selectNextUp(input({
      tasks: [task({ id: 'a', due_time: '18:30:00' }), task({ id: 'b' })],
    }))
    expect(r[0].detail).toBe('due 6:30pm')
    expect(r[1].detail).toBeUndefined()
  })

  it('marks priority only when the user raised it', () => {
    const r = selectNextUp(input({
      tasks: [task({ id: 'a', priority: 'high' }), task({ id: 'b', priority: 'medium' })],
    }))
    expect(r[0].detail).toBe('high priority')
    expect(r[1].detail).toBeUndefined()
  })

  it('never names which prayer is due', () => {
    // Which prayer is due depends on location and time, neither known here.
    // Guessing wrong in a religious context is worse than staying silent.
    const r = selectNextUp(input({ deenEnabled: true, prayersRecorded: 2 }))
    const prayer = r.find((i) => i.kind === 'prayer')!
    expect(prayer.detail).toBe('3 of 5 unrecorded')
    expect(prayer.detail).not.toMatch(/fajr|dhuhr|asr|maghrib|isha|now|soon|late/i)
  })

  it('omits prayers entirely when all five are recorded', () => {
    expect(selectNextUp(input({ deenEnabled: true, prayersRecorded: 5 }))).toHaveLength(0)
  })

  it('omits prayers when faith mode is off', () => {
    expect(selectNextUp(input({ deenEnabled: false, prayersRecorded: 0 }))).toHaveLength(0)
  })
})

describe('every item points at a control that can record it', () => {
  it('routes each kind somewhere it can actually be done', () => {
    const r = selectNextUp(input({
      tasks: [task()], habits: [habit()], deenEnabled: true, prayersRecorded: 0,
    }), 10)
    expect(r.find((i) => i.kind === 'task')!.href).toBe('/tasks')
    expect(r.find((i) => i.kind === 'habit')!.href).toBe('/habits')
    expect(r.find((i) => i.kind === 'prayer')!.href).toBe('/deen')
  })
})
