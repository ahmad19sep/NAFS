import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import CoachClient from './CoachClient'
import { deepProvider } from '@/lib/ai'

export default async function CoachPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  // The latest growth review on its own, so it is found however many
  // verdicts have been generated since.
  const { data: growth } = await supabase
    .from('ai_reports')
    .select('id, content_md, generated_at, model_used')
    .eq('user_id', user.id)
    .eq('type', 'growth')
    .order('generated_at', { ascending: false })
    .limit(1)
    .maybeSingle()

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
      growthReview={growth ?? null}
      deepModel={deepProvider()}
    />
  )
}
