import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import LogsClient from './LogsClient'

export default async function LogsPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const { data: logs } = await supabase
    .from('daily_logs')
    .select('*')
    .eq('user_id', user.id)
    .order('date', { ascending: false })
    .limit(60)

  return <LogsClient logs={logs ?? []} />
}
