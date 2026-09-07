import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { resolveTaskStatus } from '@/lib/task-status'

/**
 * Set a task's completion state.
 *
 * LOG-01. This used to read the current status and flip it, which is not
 * idempotent: if the response was lost and the client retried, the second
 * request flipped the task back and quietly undid what the user had just
 * done. A fast double-tap did the same thing.
 *
 * The client now sends the state it wants — `{ completed: true }` — so
 * repeating the request is harmless. The flip is kept only as a fallback for
 * a client that sends no body, so an older build keeps working.
 */
export async function POST(req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  // A body is optional; an empty one means "flip", the old behaviour.
  const body = await req.json().catch(() => ({}))

  const { data: task } = await supabase
    .from('tasks').select('status')
    .eq('id', params.id).eq('user_id', user.id).single()
  if (!task) return NextResponse.json({ error: 'Not found' }, { status: 404 })

  const decision = resolveTaskStatus(task.status, body?.completed)

  // Already in the requested state: return it rather than writing again, so a
  // replay cannot move completed_at and make the task look freshly done.
  if (!decision.changed) {
    const { data } = await supabase
      .from('tasks').select().eq('id', params.id).eq('user_id', user.id).single()
    return NextResponse.json({ task: data, unchanged: true })
  }

  const { data, error } = await supabase
    .from('tasks').update({
      status: decision.status,
      completed_at: decision.status === 'completed' ? new Date().toISOString() : null,
      updated_at: new Date().toISOString(),
    })
    .eq('id', params.id).eq('user_id', user.id)
    .select().single()
  if (error) return NextResponse.json({ error: 'Could not update the task.' }, { status: 500 })
  return NextResponse.json({ task: data })
}
