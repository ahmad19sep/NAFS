import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import DreamsClient from './DreamsClient'

export default async function DreamsPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const [{ data: dream }, { data: logs }] = await Promise.all([
    supabase.from('dreams').select('*').eq('user_id', user.id).maybeSingle(),
    supabase.from('daily_logs')
      .select('date, weighted_hours_today, todays_pull_days')
      .eq('user_id', user.id)
      .order('date', { ascending: true }),
  ])

  return <DreamsClient dream={dream} logs={logs ?? []} />
}
