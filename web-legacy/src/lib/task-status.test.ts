import { describe, it, expect } from 'vitest'
import { resolveTaskStatus } from './task-status'

/**
 * LOG-01 / D08 for task completion.
 *
 * The bug this replaces: the endpoint read the stored status and flipped it.
 * If the response was lost and the client retried, the second request flipped
 * the task back — silently undoing what the user had just done. A fast
 * double-tap did the same. A declared state cannot do that.
 */

describe('a declared state is idempotent', () => {
  it('completes an active task', () => {
    expect(resolveTaskStatus('active', true)).toEqual({ status: 'completed', changed: true })
  })

  it('does nothing when the task is already in that state', () => {
    expect(resolveTaskStatus('completed', true)).toEqual({ status: 'completed', changed: false })
    expect(resolveTaskStatus('active', false)).toEqual({ status: 'active', changed: false })
  })

  it('survives any number of replays', () => {
    // The property that matters: repeating the request never moves the task
    // away from what was asked for.
    let status = 'active'
    for (let i = 0; i < 5; i++) {
      const d = resolveTaskStatus(status, true)
      status = d.status
    }
    expect(status).toBe('completed')
  })

  it('reports changed:false on a replay, so completed_at is not reset', () => {
    // Rewriting would move the completion timestamp and make an old task look
    // freshly done in reports and streaks.
    expect(resolveTaskStatus('completed', true).changed).toBe(false)
  })

  it('can undo, and undoing twice stays undone', () => {
    expect(resolveTaskStatus('completed', false)).toEqual({ status: 'active', changed: true })
    expect(resolveTaskStatus('active', false).changed).toBe(false)
  })
})

describe('the old flip behaviour, kept only as a fallback', () => {
  it('flips when no state is declared', () => {
    expect(resolveTaskStatus('active', undefined)).toEqual({ status: 'completed', changed: true })
    expect(resolveTaskStatus('completed', undefined)).toEqual({ status: 'active', changed: true })
  })

  it('is exactly what made a retry destructive', () => {
    // Two flips return to the start — this is the bug, pinned so nobody
    // reintroduces it as the default path.
    const once = resolveTaskStatus('active', undefined)
    const twice = resolveTaskStatus(once.status, undefined)
    expect(twice.status).toBe('active')
  })
})

describe('only a real boolean counts as intent', () => {
  it('does not coerce strings or numbers into a completion', () => {
    // A malformed client must not accidentally mark a task done; these fall
    // through to the flip rather than being read as "true".
    for (const junk of ['true', 'false', 1, 0, null, {}, []]) {
      const d = resolveTaskStatus('active', junk)
      expect(d).toEqual({ status: 'completed', changed: true }) // flip, not declared
    }
  })

  it('treats false as a genuine request to un-complete', () => {
    expect(resolveTaskStatus('completed', false).status).toBe('active')
  })
})
