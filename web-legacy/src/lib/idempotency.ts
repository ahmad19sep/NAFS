import { createHash } from 'node:crypto'

/**
 * Request-level idempotency — LOG-01.
 *
 * A client gives each user intent a stable id. The first request carrying that
 * id claims it and does the work; a retry of the same intent finds the claim
 * and gets the original result back instead of doing the work again.
 *
 * This is for operations that are not idempotent on their own. Where the write
 * can simply be made declarative — set this status, store this absolute value —
 * do that instead: it needs no bookkeeping and cannot get stuck. Creation is
 * the case that genuinely needs this, because "make a new row" repeated is two
 * rows however carefully it is written.
 *
 * Requires supabase/mutations.sql.
 */

/**
 * Deterministic JSON, so the same intent always hashes the same way.
 *
 * `JSON.stringify` preserves insertion order, so `{a,b}` and `{b,a}` would
 * otherwise look like different payloads and turn an honest retry into a
 * spurious conflict. Keys are sorted at every depth.
 */
export function canonicalJson(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value) ?? 'null'
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`

  const entries = Object.entries(value as Record<string, unknown>)
    // undefined is absent, not a value — dropping it keeps `{a:1}` and
    // `{a:1,b:undefined}` the same intent.
    .filter(([, v]) => v !== undefined)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))

  return `{${entries.map(([k, v]) => `${JSON.stringify(k)}:${canonicalJson(v)}`).join(',')}}`
}

export function hashPayload(payload: unknown): string {
  return createHash('sha256').update(canonicalJson(payload)).digest('hex')
}

/** A request id must be client-generated, opaque and bounded. */
export function isValidRequestId(id: unknown): id is string {
  return typeof id === 'string' && id.length >= 8 && id.length <= 200
}

export type ClaimOutcome<T> =
  /** This request owns the work — go and do it. */
  | { kind: 'claimed' }
  /** Already done under this id. Return the stored result, do not repeat. */
  | { kind: 'replay'; result: T }
  /** The same id is mid-flight elsewhere. The caller should retry shortly. */
  | { kind: 'in_flight' }
  /** The id was reused with different content — a client bug, not a retry. */
  | { kind: 'conflict' }

/**
 * Decides what to do about an existing row, given the incoming payload hash.
 * Pure, so the rule is testable without a database.
 */
export function decideFromExisting<T>(
  existing: { payload_hash: string; result: T | null } | null,
  incomingHash: string,
): ClaimOutcome<T> {
  if (!existing) return { kind: 'claimed' }
  if (existing.payload_hash !== incomingHash) return { kind: 'conflict' }
  // Claimed but not yet finished: a duplicate arrived while the first is still
  // running. Returning "in flight" is honest — the caller must not do the work
  // a second time, and must not invent a result it does not have.
  if (existing.result === null || existing.result === undefined) return { kind: 'in_flight' }
  return { kind: 'replay', result: existing.result }
}

interface SupabaseLike {
  from(table: string): any
}

/**
 * Runs `work` at most once per (user, requestId).
 *
 * Without a request id it simply runs the work — callers that have not adopted
 * ids yet keep their current behaviour rather than breaking.
 */
export async function withIdempotency<T>(
  supabase: SupabaseLike,
  userId: string,
  requestId: unknown,
  payload: unknown,
  work: () => Promise<T>,
): Promise<{ outcome: 'done' | 'replayed' | 'in_flight' | 'conflict'; result?: T }> {
  if (!isValidRequestId(requestId)) {
    return { outcome: 'done', result: await work() }
  }

  const hash = hashPayload(payload)

  // Claim by inserting. The primary key makes this atomic: exactly one
  // concurrent request can succeed, the rest see the conflict.
  const { error: claimErr } = await supabase
    .from('mutations')
    .insert({ user_id: userId, request_id: requestId, payload_hash: hash })

  if (claimErr) {
    // Something already holds this id — read it and decide.
    const { data: existing } = await supabase
      .from('mutations')
      .select('payload_hash, result')
      .eq('user_id', userId).eq('request_id', requestId)
      .maybeSingle()

    const decision = decideFromExisting<T>(existing ?? null, hash)
    if (decision.kind === 'replay')    return { outcome: 'replayed', result: decision.result }
    if (decision.kind === 'conflict')  return { outcome: 'conflict' }
    if (decision.kind === 'in_flight') return { outcome: 'in_flight' }
    // 'claimed' here means the row vanished between the two queries, which
    // only happens if it was cleaned up mid-request. Doing the work is right.
  }

  const result = await work()

  // Record the result so a later retry replays instead of repeating. A failure
  // to record is not fatal: the work succeeded, and the worst case is that a
  // retry does it again — the same position we were in before this existed.
  await supabase
    .from('mutations')
    .update({ result, completed_at: new Date().toISOString() })
    .eq('user_id', userId).eq('request_id', requestId)

  return { outcome: 'done', result }
}
