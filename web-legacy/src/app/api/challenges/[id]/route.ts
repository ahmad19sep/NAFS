import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

// Streaks and check-ins are earned, not edited — only the challenge's own
// definition can change.
const ALLOWED_FIELDS = [
  'title', 'emoji', 'description', 'requires_photo', 'duration_days', 'frequency',
] as const

export async function PATCH(req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()
  const update: Record<string, any> = {}
  for (const k of ALLOWED_FIELDS) if (k in body) update[k] = body[k]
  if (Object.keys(update).length === 0)
    return NextResponse.json({ error: 'No valid fields' }, { status: 400 })

  const { data, error } = await supabase
    .from('challenges').update(update)
    .eq('id', params.id).eq('user_id', user.id)
    .select().single()
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ challenge: data })
}

export async function DELETE(_req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  await supabase.from('challenges').delete().eq('id', params.id).eq('user_id', user.id)
  return NextResponse.json({ ok: true })
}
