/**
 * Classifying the result of a version-checked write — LOG-01.
 *
 * An absolute write sends every field from a form, so without a guard it is
 * last-write-wins: two devices open the same day, both save, and the second
 * silently replaces the first — including fields it never touched and whose
 * values it never saw.
 *
 * The guard is the row's `updated_at`, read when the page loaded and required
 * to still match at save time. If it moved, someone else wrote, and the right
 * answer is to stop and say so rather than pick a winner.
 *
 * Split out here so the rules are testable without a database, and so
 * "no rows matched" is never quietly read as success.
 */

export type WriteOutcome =
  /** Written. The caller should adopt the returned version. */
  | { kind: 'saved' }
  /** Someone else changed the row first. Nothing was written. */
  | { kind: 'conflict' }
  /** Something else went wrong. */
  | { kind: 'failed' }

/**
 * Postgres reports a unique-key violation differently through PostgREST than
 * through the driver, so both the SQLSTATE and the message are checked. 23505
 * is the unique_violation class.
 */
export function isDuplicateKeyError(err: { code?: string; message?: string } | null): boolean {
  if (!err) return false
  if (err.code === '23505') return true
  return /duplicate key|unique constraint|already exists/i.test(err.message ?? '')
}

/**
 * @param matched  whether the write actually touched a row. For a guarded
 *                 update this is the crux: no row matched means the version
 *                 moved, NOT that the write succeeded with nothing to do.
 */
export function classifyWrite(
  err: { code?: string; message?: string } | null,
  matched: boolean,
  isInsert = false,
): WriteOutcome {
  if (err) {
    // An insert colliding on (user_id, date) means the row appeared while this
    // page was open — the same situation as a failed version check.
    return isDuplicateKeyError(err) && isInsert ? { kind: 'conflict' } : { kind: 'failed' }
  }
  return matched ? { kind: 'saved' } : { kind: 'conflict' }
}
