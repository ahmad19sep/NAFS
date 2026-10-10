import type { SupabaseClient } from '@supabase/supabase-js'
import { ApiRouteError } from './api-errors'
import { isValidRequestId } from './idempotency'

/** A checked RLS write; a timeout after attempting it remains an unresolved request. */
export async function saveAdvice(db: Pick<SupabaseClient, 'from'>, table: string, userId: string, id: string, patch: Record<string, unknown>, requestId: string | null = null) {
  try {
    if (table === "users" && id !== userId) throw new Error("Wrong profile")
    if (requestId && isValidRequestId(requestId)) Object.values(patch).forEach(value => { if (value && typeof value === "object" && !Array.isArray(value)) Object.assign(value, { request_id: requestId }) })
    const query = db.from(table).update(patch).eq('id', id)
    const { data, error } = await (table === 'users' ? query : query.eq('user_id', userId)).select('id').maybeSingle()
    if (error || !data) throw new Error('Advice save not confirmed')
  } catch {
    throw new ApiRouteError(503, 'result_unconfirmed', 'The generated advice save could not be confirmed. Reload saved advice before retrying.')
  }
}
