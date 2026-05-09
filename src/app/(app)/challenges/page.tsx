import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import ChallengesClient from './ChallengesClient'

export default async function ChallengesPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const { data: challenges } = await supabase
    .from('challenges')
    .select('*, challenge_checkins(*)')
    .eq('user_id', user.id)
    .order('created_at', { ascending: false })

  return <ChallengesClient challenges={challenges ?? []} userId={user.id} />
}
