import type { SupabaseClient } from '@supabase/supabase-js'
import { ApiRouteError } from './api-errors'

type OwnedActivityRow = { id: string; [column: string]: any }

/** Server row caps may be smaller than the requested page; advance by actual rows. */
export async function readAllOwnedRows(client: Pick<SupabaseClient, 'from'>, userId: string, table: string, columns: string) {
  const rows: any[] = []
  let expectedTotal: number | undefined
  const seen = new Set<string>()
  for (let page = 0; page < 100; page++) {
    const { data, error, count } = await client.from(table).select(columns, { count: 'exact' })
      .eq('user_id', userId).order('id').range(rows.length, rows.length + 999)
      .overrideTypes<OwnedActivityRow[], { merge: false }>()
    if (error || !data || count == null || count < 0 || count > 100000) {
      throw new ApiRouteError(503, 'unavailable', 'Activity could not be loaded completely.')
    }
    expectedTotal ??= count
    if (expectedTotal !== count || data.some(row => !row.id || seen.has(row.id))) {
      throw new ApiRouteError(503, 'unavailable', 'Activity changed during the report. Please refresh.')
    }
    data.forEach(row => seen.add(row.id))
    rows.push(...data)
    if (rows.length === count) return rows
    if (data.length === 0 || rows.length > count) break
  }
  throw new ApiRouteError(503, 'unavailable', 'Activity could not be loaded completely.')
}
