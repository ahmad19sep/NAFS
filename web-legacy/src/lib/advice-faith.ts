import type { SupabaseClient } from '@supabase/supabase-js'
import { ApiRouteError } from './api-errors'

export async function readFaithPreference(db: Pick<SupabaseClient, 'from'>, userId: string): Promise<boolean> {
  const { data, error } = await db.from('users').select('deen_enabled').eq('id', userId).single()
  if (error || !data || typeof data.deen_enabled !== 'boolean') throw new ApiRouteError(503, 'unavailable', 'Your preferences could not be verified.')
  return data.deen_enabled
}

export function adviceSystem(base: string, enabled: boolean): string {
  if (enabled) return base
  return base.replace(/Muslim/g, 'personal development').replace(/After Fajr \(06:30\)/g, 'After waking') +
    '\nUser preference: faith mode is OFF. Give practical secular advice. Do not assume a religion, use religious scheduling anchors, or assess prayer records. Discuss a religious goal only when the user explicitly chose that goal.'
}
