import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import TasksClient from './TasksClient'
import { todayString } from '@/lib/utils'

export default async function TasksPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  // Pull enough history to render 30 days daily, 12 weeks weekly, 6 months monthly.
  const sixMonthsAgo = new Date()
  sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6)
  const start = sixMonthsAgo.toISOString().split('T')[0]

  const { data: tasks } = await supabase
    .from('tasks')
    .select('*')
    .eq('user_id', user.id)
    .gte('period_date', start)
    .order('priority', { ascending: true })
    .order('created_at', { ascending: false })

  return <TasksClient tasks={tasks ?? []} today={todayString()} />
}
