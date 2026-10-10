import { ApiRouteError } from './api-errors'
import { hashPayload, isValidRequestId, decideFromExisting } from './idempotency'
import { isDuplicateKeyError } from './write-conflict'

interface UserScopedDb { from(table: string): any }

/** Expensive generation fails closed if its claim cannot be durably recorded. */
export async function withStrictIdempotency<T>(
  db: UserScopedDb, userId: string, requestId: string, payload: unknown, work: () => Promise<T>,
): Promise<{ result: T; replayed: boolean }> {
  if (!isValidRequestId(requestId)) throw new ApiRouteError(400, 'invalid_request', 'A stable request id is required.')
  const hash = hashPayload(payload)
  const { error: claimError } = await db.from('mutations').insert({ user_id: userId, request_id: requestId, payload_hash: hash })
  if (claimError) {
    if (!isDuplicateKeyError(claimError)) throw new ApiRouteError(503, 'ledger_unavailable', 'AI request storage is temporarily unavailable.')
    const { data, error } = await db.from('mutations').select('payload_hash,result')
      .eq('user_id', userId).eq('request_id', requestId).maybeSingle()
    if (error || !data) throw new ApiRouteError(503, 'ledger_unavailable', 'AI request storage is temporarily unavailable.')
    const existing = decideFromExisting<T>(data, hash)
    if (existing.kind === 'replay') return { result: existing.result, replayed: true }
    if (existing.kind === 'conflict') throw new ApiRouteError(409, 'conflict', 'This request id belongs to a different action.')
    throw new ApiRouteError(409, 'in_flight', 'This AI request is still running. Retry shortly or reload your saved data.')
  }
  let result: T
  try { result = await work() } catch (error) {
    // A network/response failure after a write was attempted may have committed.
    if (error instanceof ApiRouteError && error.code === 'result_unconfirmed') throw error
    // Work must never throw after its authoritative write succeeded.
    await db.from('mutations').delete().eq('user_id', userId).eq('request_id', requestId)
    throw error
  }
  try {
    const { data: completed, error } = await db.from('mutations')
      .update({ result, completed_at: new Date().toISOString() })
      .eq('user_id', userId).eq('request_id', requestId).select('request_id').maybeSingle()
    if (error || !completed) throw new Error('Unconfirmed ledger completion')
  } catch {
    // Retain the claim; the authoritative saved data can be recovered by reloading.
    throw new ApiRouteError(503, 'result_unconfirmed', 'Reload your saved data to recover the result before retrying.')
  }
  return { result, replayed: false }
}
