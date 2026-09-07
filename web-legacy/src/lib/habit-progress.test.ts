import { describe, it, expect } from 'vitest'
import { applySubjectProgress } from './habit-progress'

/**
 * LOG-01 write-safety fixtures, and the blueprint's D06/D08 cases.
 *
 * These pin the arithmetic that `log_subject_habit` must implement. The
 * property that matters most is idempotency: replaying a request after a lost
 * response must not count the same pages twice.
 */

describe('D08 — replaying a request does not double count', () => {
  it('applies nothing the second time', () => {
    // First write: 40 pages on a day with nothing recorded.
    const first = applySubjectProgress({
      currentPosition: 100, storedDeltaForDate: 0, newDeltaForDate: 40, total: 440,
    })
    expect(first.position).toBe(140)
    expect(first.appliedDelta).toBe(40)

    // The response was lost, so the client retries the identical request.
    // The day already stores 40, so the adjustment is zero.
    const retry = applySubjectProgress({
      currentPosition: first.position, storedDeltaForDate: first.storedDelta,
      newDeltaForDate: 40, total: 440,
    })
    expect(retry.position).toBe(140)
    expect(retry.appliedDelta).toBe(0)
  })

  it('stays stable however many times it is replayed', () => {
    let position = 100
    for (let i = 0; i < 5; i++) {
      position = applySubjectProgress({
        currentPosition: position, storedDeltaForDate: i === 0 ? 0 : 40,
        newDeltaForDate: 40, total: 440,
      }).position
    }
    expect(position).toBe(140)
  })
})

describe('correcting a day that was already logged', () => {
  it('moves by the difference, not the whole new amount', () => {
    // Logged 40, correcting to 55: the position moves 15, not 55.
    const r = applySubjectProgress({
      currentPosition: 140, storedDeltaForDate: 40, newDeltaForDate: 55, total: 440,
    })
    expect(r.position).toBe(155)
    expect(r.appliedDelta).toBe(15)
  })

  it('walks backwards when a day is corrected downwards', () => {
    const r = applySubjectProgress({
      currentPosition: 140, storedDeltaForDate: 40, newDeltaForDate: 10, total: 440,
    })
    expect(r.position).toBe(110)
    expect(r.appliedDelta).toBe(-30)
  })

  it('undoing a day removes exactly what it added', () => {
    const r = applySubjectProgress({
      currentPosition: 140, storedDeltaForDate: 40, newDeltaForDate: 0, total: 440,
    })
    expect(r.position).toBe(100)
    expect(r.storedDelta).toBe(0)
  })
})

describe('D06 — bounds', () => {
  it('never passes the end of the book', () => {
    const r = applySubjectProgress({
      currentPosition: 430, storedDeltaForDate: 0, newDeltaForDate: 50, total: 440,
    })
    expect(r.position).toBe(440)
    // Reports the real movement, not the requested 50.
    expect(r.appliedDelta).toBe(10)
  })

  it('never goes below zero', () => {
    const r = applySubjectProgress({
      currentPosition: 10, storedDeltaForDate: 40, newDeltaForDate: 0, total: 440,
    })
    expect(r.position).toBe(0)
  })

  it('treats a negative amount as none rather than reversing', () => {
    const r = applySubjectProgress({
      currentPosition: 100, storedDeltaForDate: 0, newDeltaForDate: -20, total: 440,
    })
    expect(r.storedDelta).toBe(0)
    expect(r.position).toBe(100)
  })

  it('works with no known total', () => {
    const r = applySubjectProgress({
      currentPosition: 100, storedDeltaForDate: 0, newDeltaForDate: 40, total: null,
    })
    expect(r.position).toBe(140)
  })

  it('records zero without claiming progress', () => {
    // An explicit zero is a valid record — it just completes nothing.
    const r = applySubjectProgress({
      currentPosition: 100, storedDeltaForDate: 0, newDeltaForDate: 0, total: 440,
    })
    expect(r.position).toBe(100)
    expect(r.appliedDelta).toBe(0)
    expect(r.storedDelta).toBe(0)
  })
})

describe('the property the old code broke', () => {
  it('two sequential writes both survive', () => {
    // The old route read the position, added in JS and wrote it back, so two
    // requests racing between read and write lost one. Applied in sequence —
    // which the row lock now guarantees — both must land.
    const a = applySubjectProgress({
      currentPosition: 100, storedDeltaForDate: 0, newDeltaForDate: 30, total: 440,
    })
    const b = applySubjectProgress({
      currentPosition: a.position, storedDeltaForDate: 0, newDeltaForDate: 25, total: 440,
    })
    // 30 from one day, 25 from another: both counted.
    expect(b.position).toBe(155)
  })
})
