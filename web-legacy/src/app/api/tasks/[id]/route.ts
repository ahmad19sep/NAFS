import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

const ALLOWED_FIELDS = ['title', 'note', 'priority', 'due_time'] as const

export async function PATCH(req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()
  const update: Record<string, any> = {}
  for (const k of ALLOWED_FIELDS) if (k in body) update[k] = body[k]
  if (Object.keys(update).length === 0)
    return NextResponse.json({ error: 'No valid fields' }, { status: 400 })
  update.updated_at = new Date().toISOString()

  const { data, error } = await supabase
    .from('tasks').update(update)
    .eq('id', params.id).eq('user_id', user.id)
    .select().single()
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ task: data })
}

/**
 * Soft delete, so the action can be undone.
 *
 * The row stays and is hidden by the SELECT policy from task_soft_delete.sql —
 * every read is filtered in the database rather than in thirteen separate
 * queries, so a deleted task cannot reappear in a report or in what the AI is
 * told because one of them forgot a condition.
 *
 * Falls back to a hard delete when the migration has not been run, so deleting
 * keeps working; only the undo is unavailable.
 */
export async function DELETE(_req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const { error } = await supabase
    .from('tasks')
    .update({ deleted_at: new Date().toISOString() })
    .eq('id', params.id).eq('user_id', user.id)

  if (error) {
    const missingColumn = /deleted_at|column .* does not exist/i.test(error.message)
    if (!missingColumn) {
      return NextResponse.json({ error: 'Could not delete the task.' }, { status: 500 })
    }
    await supabase.from('tasks').delete().eq('id', params.id).eq('user_id', user.id)
    return NextResponse.json({ ok: true, undoable: false })
  }

  return NextResponse.json({ ok: true, undoable: true })
}
