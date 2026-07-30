import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import ChallengesClient from './ChallengesClient'

export default async function ChallengesPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const { data: challenges } = await supabase
    .from('challenges')
    .select('*, challenge_checkins(*)')
    .eq('user_id', user.id)
    .order('created_at', { ascending: false })

  return <ChallengesClient challenges={challenges ?? []} userId={user.id} />
}
