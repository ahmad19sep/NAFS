import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import LogsClient from './LogsClient'

export default async function LogsPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const { data: logs } = await supabase
    .from('daily_logs')
    .select('*')
    .eq('user_id', user.id)
    .order('date', { ascending: false })
    .limit(60)

  return <LogsClient logs={logs ?? []} />
}
