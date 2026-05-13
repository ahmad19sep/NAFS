import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

const ALLOWED_FIELDS = [
  'name', 'emoji', 'type',
  'target_value', 'time_target_mins', 'unit',
  'category', 'score_weight',
  'why', 'schedule_kind', 'schedule_days', 'weekly_target', 'reminder_time',
  'subject_name', 'subject_total', 'subject_position', 'subject_unit',
  'is_active', 'is_paused', 'sort_order',
  'ai_starter_pack',
] as const

export async function PATCH(req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()
  const update: Record<string, any> = {}
  for (const k of ALLOWED_FIELDS) {
    if (k in body) update[k] = body[k]
  }

  if (Object.keys(update).length === 0) {
    return NextResponse.json({ error: 'No valid fields' }, { status: 400 })
  }

  const { data, error } = await supabase
    .from('habits')
    .update(update)
    .eq('id', params.id)
    .eq('user_id', user.id)
    .select()
    .single()

  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ habit: data })
}

export async function DELETE(_req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  await supabase.from('habits').delete().eq('id', params.id).eq('user_id', user.id)
  return NextResponse.json({ ok: true })
}
