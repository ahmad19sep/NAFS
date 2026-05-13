import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import DreamsClient from './DreamsClient'

export default async function DreamsPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const { data: profile } = await supabase
    .from('users')
    .select('*, dreams(*, activity_weights(*))')
    .eq('id', user.id)
    .single()

  const { data: logs } = await supabase
    .from('daily_logs')
    .select('date, weighted_hours_today, todays_pull_days')
    .eq('user_id', user.id)
    .order('date', { ascending: true })

  return <DreamsClient dream={profile?.dreams} logs={logs ?? []} />
}
