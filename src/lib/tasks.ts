// Helpers for the tasks system (Daily / Weekly / Monthly).
//
// period_date semantics:
//   daily   → the date the task is for (e.g. '2026-05-13')
//   weekly  → ISO Monday of that week  (e.g. '2026-05-11')
//   monthly → first of that month       (e.g. '2026-05-01')

export type TaskType = 'daily' | 'weekly' | 'monthly'
export type TaskPriority = 'low' | 'medium' | 'high'
export type TaskStatus = 'active' | 'completed'

export interface Task {
  id: string
  user_id: string
  title: string
  note: string | null
  type: TaskType
  priority: TaskPriority
  status: TaskStatus
  period_date: string          // YYYY-MM-DD
  due_time: string | null      // 'HH:MM:SS' from postgres TIME (daily only)
  alerts_sent: string[]        // ['1h', '30m']
  completed_at: string | null
  created_at: string
  updated_at: string
}

// ---------- date arithmetic ----------
function parseLocal(dateStr: string): Date {
  return new Date(dateStr + 'T12:00:00')
}
export function fmt(d: Date): string {
  return d.toISOString().split('T')[0]
}

/** ISO week's Monday (week starts Monday). */
export function isoMonday(dateStr: string): string {
  const d = parseLocal(dateStr)
  const day = (d.getDay() + 6) % 7 // 0 = Mon
  d.setDate(d.getDate() - day)
  return fmt(d)
}

/** First day of the month. */
export function firstOfMonth(dateStr: string): string {
  const d = parseLocal(dateStr)
  d.setDate(1)
  return fmt(d)
}

export function addDays(dateStr: string, n: number): string {
  const d = parseLocal(dateStr); d.setDate(d.getDate() + n); return fmt(d)
}
export function addWeeks(dateStr: string, n: number): string {
  return addDays(dateStr, n * 7)
}
export function addMonths(dateStr: string, n: number): string {
  const d = parseLocal(dateStr); d.setMonth(d.getMonth() + n); return fmt(d)
}

/** What's the anchoring period_date for a brand-new task of this type, given today. */
export function periodAnchorFor(type: TaskType, today: string): string {
  if (type === 'daily')   return today
  if (type === 'weekly')  return isoMonday(today)
  return firstOfMonth(today)
}

/** Previous period anchor. Used for the 1-cycle grace window. */
export function previousPeriodAnchor(type: TaskType, currentAnchor: string): string {
  if (type === 'daily')   return addDays(currentAnchor, -1)
  if (type === 'weekly')  return addWeeks(currentAnchor, -1)
  return addMonths(currentAnchor, -1)
}

/** Pretty label for the period. */
export function periodLabel(type: TaskType, anchor: string, today: string): string {
  const todayAnchor = periodAnchorFor(type, today)
  const prevAnchor = previousPeriodAnchor(type, todayAnchor)

  if (type === 'daily') {
    if (anchor === today)         return 'Today'
    if (anchor === prevAnchor)    return 'Yesterday'
    return new Date(anchor + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
  }
  if (type === 'weekly') {
    if (anchor === todayAnchor) return 'This week'
    if (anchor === prevAnchor)  return 'Last week'
    return `Week of ${new Date(anchor + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`
  }
  // monthly
  if (anchor === todayAnchor) return 'This month'
  if (anchor === prevAnchor)  return 'Last month'
  return new Date(anchor + 'T12:00:00').toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

/** A task is "missed" if it's still active and its period is past. */
export function isMissed(task: Task, today: string): boolean {
  if (task.status !== 'active') return false
  const todayAnchor = periodAnchorFor(task.type, today)
  return task.period_date < todayAnchor
}

/** Should a task be visible in the active+grace list? */
export function isCurrentOrGrace(task: Task, today: string): boolean {
  const todayAnchor = periodAnchorFor(task.type, today)
  const prevAnchor = previousPeriodAnchor(task.type, todayAnchor)
  // Visible if period is current OR exactly previous (grace).
  return task.period_date === todayAnchor || task.period_date === prevAnchor
}

export const PRIORITY_RANK: Record<TaskPriority, number> = {
  high: 0, medium: 1, low: 2,
}

/** Minutes until a daily task's due_time (negative if overdue). null if no due_time. */
export function minutesUntilDue(task: Task, today: string): number | null {
  if (!task.due_time || task.period_date !== today) return null
  const [hh, mm] = task.due_time.split(':').map(Number)
  if (isNaN(hh) || isNaN(mm)) return null
  const now = new Date()
  const due = new Date(now)
  due.setHours(hh, mm, 0, 0)
  return Math.round((due.getTime() - now.getTime()) / 60000)
}

export function formatDueTime(due_time: string | null): string | null {
  if (!due_time) return null
  const [hh, mm] = due_time.split(':').map(Number)
  if (isNaN(hh) || isNaN(mm)) return null
  const ampm = hh >= 12 ? 'PM' : 'AM'
  const h12 = ((hh + 11) % 12) + 1
  return `${h12}:${String(mm).padStart(2, '0')} ${ampm}`
}
