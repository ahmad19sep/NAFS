import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function POST(_req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const { data: task } = await supabase
    .from('tasks').select('status')
    .eq('id', params.id).eq('user_id', user.id).single()
  if (!task) return NextResponse.json({ error: 'Not found' }, { status: 404 })

  const nextStatus = task.status === 'completed' ? 'active' : 'completed'
  const { data, error } = await supabase
    .from('tasks').update({
      status: nextStatus,
      completed_at: nextStatus === 'completed' ? new Date().toISOString() : null,
      updated_at: new Date().toISOString(),
    })
    .eq('id', params.id).eq('user_id', user.id)
    .select().single()
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ task: data })
}
