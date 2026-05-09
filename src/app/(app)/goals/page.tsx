import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import GoalsClient from './GoalsClient'

export default async function GoalsPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const [{ data: goals }, { data: habits }] = await Promise.all([
    supabase.from('goals').select('*, goal_milestones(*)').eq('user_id', user.id).order('created_at', { ascending: false }),
    supabase.from('habits').select('id, name, emoji, current_streak').eq('user_id', user.id).eq('is_active', true),
  ])

  return <GoalsClient userId={user.id} goals={goals ?? []} habits={habits ?? []} />
}
