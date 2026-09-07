/**
 * Deciding a task's next completion state — LOG-01.
 *
 * Split out from the route so the rule is testable without a database, and so
 * the idempotency property is pinned rather than assumed.
 *
 * The property: given a desired state, applying it any number of times leaves
 * the task in that state. A "toggle" cannot promise that — replaying it flips
 * the task back, which is how a lost response silently undid a completed task.
 */

export type TaskStatus = 'active' | 'completed'

export interface StatusDecision {
  status: TaskStatus
  /** False when the task is already in the requested state — nothing to write. */
  changed: boolean
}

/**
 * @param current  the task's stored status
 * @param desired  the state the caller wants. `undefined` means flip, which is
 *                 kept only so a client that sends no body still works.
 */
export function resolveTaskStatus(current: string, desired?: unknown): StatusDecision {
  const isCompleted = current === 'completed'

  // Only a real boolean is a declared intent. A string "true", a number, or
  // null are treated as absent rather than coerced, so a malformed client
  // cannot accidentally mark a task done.
  if (typeof desired === 'boolean') {
    const status: TaskStatus = desired ? 'completed' : 'active'
    return { status, changed: desired !== isCompleted }
  }

  return { status: isCompleted ? 'active' : 'completed', changed: true }
}
