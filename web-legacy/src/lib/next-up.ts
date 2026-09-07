/**
 * Choosing what to put in front of the user — UI-01.
 *
 * Deliberately no model and no cleverness. The blueprint is explicit that this
 * is a selection rule, and two of its constraints matter more than the ordering:
 *
 *   - Never invent urgency. A prayer is not "due" because of the clock, and a
 *     task without a due time does not become urgent as the day wears on. Only
 *     times the user actually set are treated as deadlines.
 *   - Keep the order stable during an interaction. Nothing here reads the
 *     current time, so the list cannot reshuffle under a finger mid-tap.
 *
 * Everything already done, unscheduled or paused is excluded — the point is
 * what is left, not a summary of the day.
 */

const PRIORITY_RANK: Record<string, number> = { high: 0, medium: 1, low: 2 }

export interface NextUpItem {
  id: string
  kind: 'task' | 'habit' | 'prayer'
  label: string
  emoji: string
  /** Where tapping it goes — the control that can actually record it. */
  href: string
  /** Short, factual context. Never a manufactured urgency. */
  detail?: string
}

export interface NextUpInput {
  today: string
  tasks: {
    id: string; title: string; status: string; type: string
    period_date: string; priority: string; due_time: string | null
    created_at?: string
  }[]
  habits: {
    id: string; name: string; emoji: string
    is_paused?: boolean; schedule_kind?: string; schedule_days?: string[] | null
    sort_order?: number
  }[]
  /** Habit ids already completed today. */
  doneHabitIds: Set<string> | string[]
  deenEnabled: boolean
  /** How many of the five daily prayers have been recorded. */
  prayersRecorded: number
}

const WEEKDAY_CODES = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']

function weekdayOf(dateStr: string): string {
  return WEEKDAY_CODES[new Date(`${dateStr}T12:00:00`).getDay()]
}

function scheduledToday(h: NextUpInput['habits'][number], today: string): boolean {
  if (h.is_paused) return false
  if (h.schedule_kind === 'weekdays') return (h.schedule_days ?? []).includes(weekdayOf(today))
  return true
}

/** "18:30:00" → 1110. Null when unset or unparseable. */
function dueMinutes(due: string | null): number | null {
  if (!due) return null
  const m = /^(\d{1,2}):(\d{2})/.exec(due)
  if (!m) return null
  return Number(m[1]) * 60 + Number(m[2])
}

function formatDue(due: string): string {
  const mins = dueMinutes(due)
  if (mins == null) return ''
  const h = Math.floor(mins / 60)
  const mm = String(mins % 60).padStart(2, '0')
  const suffix = h < 12 ? 'am' : 'pm'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return `due ${h12}:${mm}${suffix}`
}

/**
 * Up to `limit` things still to do, most actionable first.
 *
 * Order: tasks the user gave a time to (earliest first), then remaining tasks
 * by the priority they chose, then habits in their own order, then prayers.
 * Ties break on a stable identifier so repeated calls agree.
 */
export function selectNextUp(input: NextUpInput, limit = 3): NextUpItem[] {
  const done = input.doneHabitIds instanceof Set
    ? input.doneHabitIds
    : new Set(input.doneHabitIds)

  const openTasks = input.tasks.filter(
    (t) => t.type === 'daily' && t.period_date === input.today && t.status !== 'completed',
  )

  const timed = openTasks
    .filter((t) => dueMinutes(t.due_time) != null)
    .sort((a, b) =>
      (dueMinutes(a.due_time)! - dueMinutes(b.due_time)!) || (a.id < b.id ? -1 : 1))

  const untimed = openTasks
    .filter((t) => dueMinutes(t.due_time) == null)
    .sort((a, b) =>
      ((PRIORITY_RANK[a.priority] ?? 1) - (PRIORITY_RANK[b.priority] ?? 1))
      || (a.id < b.id ? -1 : 1))

  const openHabits = input.habits
    .filter((h) => scheduledToday(h, input.today) && !done.has(h.id))
    .sort((a, b) => ((a.sort_order ?? 0) - (b.sort_order ?? 0)) || (a.id < b.id ? -1 : 1))

  const items: NextUpItem[] = [
    ...timed.map((t) => ({
      id: t.id, kind: 'task' as const, label: t.title, emoji: '✅',
      href: '/tasks', detail: formatDue(t.due_time!),
    })),
    ...untimed.map((t) => ({
      id: t.id, kind: 'task' as const, label: t.title, emoji: '✅',
      href: '/tasks',
      // Only worth saying when the user actually raised it.
      detail: t.priority === 'high' ? 'high priority' : undefined,
    })),
    ...openHabits.map((h) => ({
      id: h.id, kind: 'habit' as const, label: h.name, emoji: h.emoji || '🔁',
      href: '/habits',
    })),
  ]

  // Prayers go last and say only how many are unrecorded. Which prayer is "due"
  // depends on location and time, neither of which is known here, and guessing
  // it wrong in a religious context is worse than not saying it.
  if (input.deenEnabled && input.prayersRecorded < 5) {
    const remaining = 5 - input.prayersRecorded
    items.push({
      id: 'prayers', kind: 'prayer', label: 'Prayers', emoji: '🕌', href: '/deen',
      detail: `${remaining} of 5 unrecorded`,
    })
  }

  return items.slice(0, limit)
}
