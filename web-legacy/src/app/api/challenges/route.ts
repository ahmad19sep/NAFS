import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { withIdempotency } from '@/lib/idempotency'
import { todayInTZ } from '@/lib/utils'

const FREQUENCIES = ['daily', 'weekly', 'monthly', 'yearly'] as const

/**
 * Create a challenge.
 *
 * The Challenges page inserts directly from the client; this route exists so
 * a confirmed AI proposal can be created through the same kind of authorised
 * server mutation tasks and habits use, with the same request-id guard against
 * a retried creation making two.
 */
export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json().catch(() => ({}))
  const title: string = (body.title ?? '').trim()
  if (!title) return NextResponse.json({ error: 'Title required' }, { status: 400 })

  const frequency = FREQUENCIES.includes(body.frequency) ? body.frequency : 'daily'
  const duration_days = Math.min(1825, Math.max(1, Math.round(Number(body.duration_days) || 30)))
  const description: string | null = body.description?.trim() ? body.description.trim() : null

  // The challenge starts on the account's own calendar day, not the server's.
  const { data: profile } = await supabase
    .from('users').select('timezone').eq('id', user.id).maybeSingle()
  const start_date = todayInTZ(profile?.timezone || 'UTC')

  const payload = {
    title, emoji: body.emoji || '🎯', description, frequency, duration_days,
    requires_photo: !!body.requires_photo, start_date,
  }

  const { outcome, result } = await withIdempotency(
    supabase, user.id, body.request_id, payload,
    async () => {
      const { data, error } = await supabase.from('challenges').insert({
        user_id: user.id,
        ...payload,
        sadqa_amount: null,
        sadqa_currency: 'PKR',
        current_streak: 0,
        longest_streak: 0,
        restart_count: 0,
        status: 'active',
      }).select().single()
      if (error) throw new Error(error.message)
      return data
    },
  )

  if (outcome === 'conflict') {
    return NextResponse.json({ error: 'That request id was already used for a different challenge.' }, { status: 409 })
  }
  if (outcome === 'in_flight') {
    return NextResponse.json({ error: 'This challenge is already being created.' }, { status: 409 })
  }
  return NextResponse.json({ challenge: result, replayed: outcome === 'replayed' })
}
