import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

/**
 * Undo a task deletion — LOG-01.
 *
 * Targets the original row rather than creating a replacement, so the task
 * keeps its id, its creation time and its place in history. Re-inserting a copy
 * would look the same on screen and be a different task everywhere else.
 *
 * Idempotent: restoring an already-live task is a no-op, so a double-tap on
 * Undo cannot do anything odd.
 *
 * Reaching a soft-deleted row depends on the UPDATE policy from
 * task_soft_delete.sql, which deliberately does not filter on deleted_at.
 */
export async function POST(_req: NextRequest, { params }: { params: { id: string } }) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const { data, error } = await supabase
    .from('tasks')
    .update({ deleted_at: null, updated_at: new Date().toISOString() })
    .eq('id', params.id).eq('user_id', user.id)
    .select().maybeSingle()

  if (error) {
    return NextResponse.json({ error: 'Could not restore the task.' }, { status: 500 })
  }
  if (!data) {
    // Hard-deleted before the migration existed, or never the caller's task.
    return NextResponse.json({ error: 'That task can no longer be restored.' }, { status: 404 })
  }

  return NextResponse.json({ task: data })
}
