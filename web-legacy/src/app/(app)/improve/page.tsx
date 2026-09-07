import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import ImproveClient from './ImproveClient'
import type { AppNote } from '@/lib/app-notes'

export const dynamic = 'force-dynamic'

export default async function ImprovePage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  // Everything, so the page can show what has been done as well as what is
  // open — a list that only ever grows is discouraging to open.
  const { data } = await supabase
    .from('app_notes')
    .select('id, kind, title, detail, where_seen, status, created_at, resolved_at')
    .eq('user_id', user.id)
    .order('created_at', { ascending: false })
    .limit(500)

  return <ImproveClient userId={user.id} initialNotes={(data ?? []) as AppNote[]} />
}
