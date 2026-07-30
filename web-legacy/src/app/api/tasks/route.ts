import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { periodAnchorFor, type TaskType, type TaskPriority } from '@/lib/tasks'

const TYPES: TaskType[] = ['daily', 'weekly', 'monthly']
const PRIORITIES: TaskPriority[] = ['low', 'medium', 'high']

function todayISO() {
  return new Date().toISOString().split('T')[0]
}

export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()
  const title: string = (body.title ?? '').trim()
  if (!title) return NextResponse.json({ error: 'Title required' }, { status: 400 })

  const type: TaskType = TYPES.includes(body.type) ? body.type : 'daily'
  const priority: TaskPriority = PRIORITIES.includes(body.priority) ? body.priority : 'medium'
  const note: string | null = body.note?.trim() ? body.note.trim() : null
  // Only daily tasks support due_time (time-of-day deadline). Format: 'HH:MM'.
  const due_time: string | null =
    type === 'daily' && typeof body.due_time === 'string' && /^\d{2}:\d{2}/.test(body.due_time)
      ? body.due_time
      : null

  const period_date = periodAnchorFor(type, todayISO())

  const { data, error } = await supabase.from('tasks').insert({
    user_id: user.id,
    title, note, type, priority,
    status: 'active',
    period_date,
    due_time,
  }).select().single()

  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ task: data })
}
