/**
 * Open the quick-add sheet from anywhere, with a starting point.
 *
 * The sheet lives inside the bottom dock, which is not a parent of the Home
 * cards or the coach bubble that want to open it. A window event keeps them
 * decoupled: a caller dispatches, the dock listens.
 */

export interface QuickAddIntent {
  mode: 'plan'
  /** Plan something to do, or plan a way out of something. */
  planMode?: 'plan' | 'overcome'
  /** Text to start the AI box with, in the user's words. */
  intent?: string
}

export const QUICK_ADD_EVENT = 'nafs:quick-add'

export function openQuickAdd(detail: QuickAddIntent): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent<QuickAddIntent>(QUICK_ADD_EVENT, { detail }))
}
