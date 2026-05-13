import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()

  const { data: existing } = await supabase
    .from('habits')
    .select('sort_order')
    .eq('user_id', user.id)
    .order('sort_order', { ascending: false })
    .limit(1)
    .maybeSingle()

  const nextOrder = (existing?.sort_order ?? 0) + 1

  const insert: Record<string, any> = {
    user_id: user.id,
    name: body.name,
    emoji: body.emoji ?? '⭐',
    type: body.type ?? 'simple',
    target_value: body.target_value ?? 1,
    time_target_mins: body.time_target_mins ?? 0,
    unit: body.unit ?? '',
    category: body.category ?? 'custom',
    score_weight: body.score_weight ?? 1,
    note_template: body.note_template ?? null,
    why: body.why ?? null,
    schedule_kind: body.schedule_kind ?? 'daily',
    schedule_days: body.schedule_days ?? null,
    weekly_target: body.weekly_target ?? null,
    reminder_time: body.reminder_time ?? null,
    subject_name: body.subject_name ?? null,
    subject_total: body.subject_total ?? null,
    subject_position: body.subject_position ?? 0,
    subject_unit: body.subject_unit ?? null,
    current_streak: 0,
    longest_streak: 0,
    is_active: true,
    is_paused: false,
    sort_order: nextOrder,
  }

  const { data, error } = await supabase.from('habits').insert(insert).select().single()
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ habit: data })
}
