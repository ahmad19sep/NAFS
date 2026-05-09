import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()

  // Get current max sort_order
  const { data: existing } = await supabase
    .from('habits')
    .select('sort_order')
    .eq('user_id', user.id)
    .order('sort_order', { ascending: false })
    .limit(1)
    .single()

  const nextOrder = (existing?.sort_order ?? 0) + 1

  const { data, error } = await supabase.from('habits').insert({
    user_id: user.id,
    name: body.name,
    emoji: body.emoji ?? '⭐',
    type: body.type ?? 'boolean',
    target_value: body.target_value ?? 1,
    time_target_mins: body.time_target_mins ?? 0,
    unit: body.unit ?? '',
    category: body.category ?? 'custom',
    score_weight: body.score_weight ?? 1,
    note_template: body.note_template ?? null,
    current_streak: 0,
    longest_streak: 0,
    is_active: true,
    sort_order: nextOrder,
  }).select().single()

  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ habit: data })
}
