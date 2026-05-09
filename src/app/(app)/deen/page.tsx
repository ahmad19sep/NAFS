import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import DeenClient from './DeenClient'

export default async function DeenPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  // Last 90 days of logs for heatmap
  const ninetyDaysAgo = new Date()
  ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90)

  const { data: logs } = await supabase
    .from('daily_logs')
    .select('date, prayers, identity_score')
    .eq('user_id', user.id)
    .gte('date', ninetyDaysAgo.toISOString().split('T')[0])
    .order('date', { ascending: true })

  const { data: quranLogs } = await supabase
    .from('quran_log')
    .select('date, pages_read')
    .eq('user_id', user.id)
    .gte('date', ninetyDaysAgo.toISOString().split('T')[0])
    .order('date', { ascending: true })

  return <DeenClient logs={logs ?? []} quranLogs={quranLogs ?? []} />
}
