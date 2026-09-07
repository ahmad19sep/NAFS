/**
 * Completion and coverage metrics for scheduled opportunities.
 *
 * Groundwork for DATA-02 from the NAFS Improvement Blueprint. Nothing consumes
 * this yet — the existing report keeps its own aggregation until DATA-01's
 * audit is signed off. It lives here, pure and tested, so the migration has a
 * definition to move to rather than inventing one mid-refactor.
 *
 * The distinction it exists to protect: a day with no record is *unknown*, not
 * a failure. Reporting 100% off a single recorded day out of seven is the
 * failure mode this replaces.
 */

/** The lifecycle of one scheduled opportunity. See blueprint page 5. */
export type OpportunityState =
  | 'not_scheduled' // rest day or outside the active schedule — not eligible
  | 'pending'       // window still open — no historical verdict yet
  | 'completed'     // met the target snapshot that applied at the time
  | 'partial'       // recorded progress that fell short of the target
  | 'missed'        // explicitly recorded as not done
  | 'unknown'       // window closed with nothing recorded
  | 'excused'       // deliberately excluded under an approved policy

export interface CoverageMetrics {
  /** Eligible closed opportunities: everything except not_scheduled/pending/excused. */
  eligible: number
  /** Of those, the ones with a known outcome — completed, partial or missed. */
  recorded: number
  /** Completed opportunities. */
  completed: number
  /** Eligible but never recorded. */
  unknown: number
  /** Excluded by policy, reported separately so the exclusion stays visible. */
  excused: number
  /** C / R — completion among opportunities we actually know about. Null when R = 0. */
  completionOnRecorded: number | null
  /** R / E — how much of the eligible window was recorded at all. Null when E = 0. */
  recordingCoverage: number | null
  /**
   * [C / E, (C + U) / E] — the range the true all-opportunity completion must
   * fall in, treating every unknown first as a miss and then as a success.
   * A missing-data bound, NOT a statistical confidence interval. Null when E = 0.
   */
  possibleCompletion: { min: number; max: number } | null
}

/**
 * Counts states into the metrics above. `pending` is excluded from every
 * denominator: an opportunity whose window is still open has not been missed,
 * so opening the app in the morning must not manufacture failures.
 */
export function coverageMetrics(states: readonly OpportunityState[]): CoverageMetrics {
  let completed = 0
  let partial = 0
  let missed = 0
  let unknown = 0
  let excused = 0

  for (const s of states) {
    if (s === 'completed') completed++
    else if (s === 'partial') partial++
    else if (s === 'missed') missed++
    else if (s === 'unknown') unknown++
    else if (s === 'excused') excused++
    // not_scheduled and pending are not eligible and are simply skipped
  }

  const recorded = completed + partial + missed
  const eligible = recorded + unknown

  return {
    eligible,
    recorded,
    completed,
    unknown,
    excused,
    completionOnRecorded: recorded > 0 ? completed / recorded : null,
    recordingCoverage: eligible > 0 ? recorded / eligible : null,
    possibleCompletion: eligible > 0
      ? { min: completed / eligible, max: (completed + unknown) / eligible }
      : null,
  }
}

/**
 * How the headline should read, so a percentage never appears without the
 * denominator that produced it.
 *
 *   "80% completed among recorded opportunities · 5 of 7 recorded"
 *   "No outcomes recorded"   — eligible but nothing known
 *   "Nothing scheduled"      — not eligible at all
 */
export function describeCoverage(m: CoverageMetrics): string {
  if (m.eligible === 0) return 'Nothing scheduled'
  if (m.recorded === 0) return `No outcomes recorded · 0 of ${m.eligible}`
  const pct = Math.round((m.completionOnRecorded ?? 0) * 100)
  return `${pct}% completed among recorded opportunities · ${m.recorded} of ${m.eligible} recorded`
}
