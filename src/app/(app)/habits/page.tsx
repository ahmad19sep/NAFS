import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import HabitsClient from './HabitsClient'
import { todayString } from '@/lib/utils'

export default async function HabitsPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const today = todayString()
  const ninetyDaysAgo = new Date()
  ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90)
  const ninetyDaysStr = ninetyDaysAgo.toISOString().split('T')[0]

  const [{ data: habits }, { data: logs }] = await Promise.all([
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true).order('sort_order'),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', ninetyDaysStr).order('date'),
  ])

  return (
    <HabitsClient
      userId={user.id}
      habits={habits ?? []}
      logs={logs ?? []}
      today={today}
    />
  )
}
