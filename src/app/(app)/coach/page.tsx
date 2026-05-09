import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import CoachClient from './CoachClient'

export default async function CoachPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const { data: reports } = await supabase
    .from('ai_reports')
    .select('*')
    .eq('user_id', user.id)
    .order('generated_at', { ascending: false })
    .limit(10)

  const { data: letters } = await supabase
    .from('future_self_letters')
    .select('*')
    .eq('user_id', user.id)
    .order('written_at', { ascending: false })

  const { data: conversations } = await supabase
    .from('ai_conversations')
    .select('*')
    .eq('user_id', user.id)
    .order('created_at', { ascending: false })
    .limit(1)
    .single()

  return (
    <CoachClient
      userId={user.id}
      reports={reports ?? []}
      letters={letters ?? []}
      lastConversation={conversations}
    />
  )
}
