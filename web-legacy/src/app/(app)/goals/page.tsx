import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import GoalsClient from './GoalsClient'
import { todayString } from '@/lib/utils'

export default async function GoalsPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const [{ data: goals }, { data: habits }] = await Promise.all([
    supabase
      .from('goals')
      .select('*, goal_milestones(*)')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false }),
    supabase
      .from('habits')
      .select('id, name, emoji, current_streak')
      .eq('user_id', user.id)
      .eq('is_active', true),
  ])

  return <GoalsClient userId={user.id} goals={goals ?? []} habits={habits ?? []} today={todayString()} />
}
