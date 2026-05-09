import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import DreamsClient from './DreamsClient'

export default async function DreamsPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

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
