import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { withIdempotency } from '@/lib/idempotency'
import { todayInTZ } from '@/lib/utils'
import { LIFE_TOPICS, type NoteKind } from '@/lib/coach-notes'

const KINDS: NoteKind[] = ['miss_reason', 'low_score', 'life']
const MAX_CONTENT = 2000

/**
 * Save something the user told the coach, in their own words.
 *
 * Always an insert, never an update: the coach reads the newest note per
 * subject, and keeping the older ones is how it can see that an answer
 * changed. The same request id retried saves once.
 */
export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json().catch(() => ({}))

  const kind = body.kind as NoteKind
  if (!KINDS.includes(kind)) return NextResponse.json({ error: 'Unknown note kind' }, { status: 400 })

  const content: string = String(body.content ?? '').trim().slice(0, MAX_CONTENT)
  if (!content) return NextResponse.json({ error: 'Write something first' }, { status: 400 })

  let subject: string | null = body.subject == null ? null : String(body.subject).trim().slice(0, 100) || null
  if (kind === 'life' && !LIFE_TOPICS.some((t) => t.key === subject)) {
    return NextResponse.json({ error: 'Unknown life question' }, { status: 400 })
  }
  if (kind === 'miss_reason' && !/^(habit|prayer):[\w-]+$/.test(subject ?? '')) {
    return NextResponse.json({ error: 'A miss reason needs what was missed' }, { status: 400 })
  }
  if (kind === 'low_score') subject = null

  // The note is dated on the account's own calendar day.
  const { data: profile } = await supabase
    .from('users').select('timezone').eq('id', user.id).maybeSingle()
  const date = todayInTZ(profile?.timezone || 'UTC')

  const payload = { kind, subject, content, date }

  const { outcome, result } = await withIdempotency(
    supabase, user.id, body.request_id, payload,
    async () => {
      const { data, error } = await supabase.from('coach_notes')
        .insert({ user_id: user.id, ...payload })
        .select('id, kind, subject, content, date')
        .single()
      if (error) throw new Error(error.message)
      return data
    },
  )

  if (outcome === 'conflict') {
    return NextResponse.json({ error: 'That request id was already used for a different note.' }, { status: 409 })
  }
  if (outcome === 'in_flight') {
    return NextResponse.json({ error: 'This note is already being saved.' }, { status: 409 })
  }
  return NextResponse.json({ note: result, replayed: outcome === 'replayed' })
}
