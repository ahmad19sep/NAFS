/**
 * Executable specification for subject-habit progress — LOG-01.
 *
 * The real update runs inside the `log_subject_habit` Postgres function
 * (supabase/atomic_habit_progress.sql), because only the database can hold a
 * row lock across the read and the write. That function cannot be unit-tested
 * from here, so the arithmetic it must implement lives here instead, tested,
 * as the definition the SQL is checked against.
 *
 * **If you change one, change both.** The duplication is deliberate: the
 * alternative is an untested rule living only in a migration nobody reads.
 *
 * The rule, in one line: a day's log stores the ABSOLUTE amount read that day,
 * and the running position moves by the difference from whatever was stored
 * before. That is what makes a retry safe — replaying the same request applies
 * a zero adjustment instead of counting the pages twice.
 */

export interface ProgressInput {
  /** Running position before this write, e.g. page 120 of a book. */
  currentPosition: number
  /** Amount already recorded for this date, which the new value replaces. */
  storedDeltaForDate: number
  /** The new absolute amount for this date. */
  newDeltaForDate: number
  /** Length of the book, if known. Progress never passes it. */
  total?: number | null
}

export interface ProgressResult {
  /** Where the position lands. */
  position: number
  /** How far it actually moved, after clamping. Zero for a replayed request. */
  appliedDelta: number
  /** The value stored on the day's log. */
  storedDelta: number
}

export function applySubjectProgress(input: ProgressInput): ProgressResult {
  const total = input.total ?? Number.MAX_SAFE_INTEGER
  // A negative amount read is meaningless; treat it as none.
  const storedDelta = Math.max(0, input.newDeltaForDate)
  const adjustment = storedDelta - Math.max(0, input.storedDeltaForDate)

  const position = Math.max(0, Math.min(total, input.currentPosition + adjustment))

  return {
    position,
    // Reports the real movement, which clamping may have shortened — so a
    // caller can tell "finished the book" from "read 40 more pages".
    appliedDelta: position - input.currentPosition,
    storedDelta,
  }
}
