// Helpers for the Goals system.

export type GoalType = 'weekly' | 'monthly' | 'quarterly' | 'yearly' | 'long_term'

export interface GoalTypeMeta {
  key: GoalType
  label: string
  shortLabel: string
  emoji: string
  defaultDays: number
  accent: 'teal' | 'gold' | 'pink' | 'emerald' | 'purple'
}

export const GOAL_TYPES: GoalTypeMeta[] = [
  { key: 'weekly',    label: 'Weekly',    shortLabel: 'Wk',  emoji: '📅', defaultDays: 7,    accent: 'teal'    },
  { key: 'monthly',   label: 'Monthly',   shortLabel: 'Mo',  emoji: '🗓️', defaultDays: 30,   accent: 'gold'    },
  { key: 'quarterly', label: 'Quarterly', shortLabel: 'Q',   emoji: '📊', defaultDays: 90,   accent: 'pink'    },
  { key: 'yearly',    label: 'Yearly',    shortLabel: 'Yr',  emoji: '🎯', defaultDays: 365,  accent: 'emerald' },
  { key: 'long_term', label: 'Long-term', shortLabel: 'LT',  emoji: '🌌', defaultDays: 1095, accent: 'purple'  },
]

export const GOAL_TYPE_BY_KEY: Record<GoalType, GoalTypeMeta> =
  GOAL_TYPES.reduce((acc, t) => ({ ...acc, [t.key]: t }), {} as any)

// Common categories
export interface CategoryMeta { key: string; label: string; emoji: string }
export const GOAL_CATEGORIES: CategoryMeta[] = [
  { key: 'deen',      label: 'Deen',      emoji: '🕌' },
  { key: 'career',    label: 'Career',    emoji: '💼' },
  { key: 'learning',  label: 'Learning',  emoji: '📚' },
  { key: 'health',    label: 'Health',    emoji: '❤️' },
  { key: 'financial', label: 'Financial', emoji: '💰' },
  { key: 'family',    label: 'Family',    emoji: '👨‍👩‍👧' },
  { key: 'personal',  label: 'Personal',  emoji: '✨' },
]

// Default deadline = start_date + type's default duration
export function defaultDeadlineFor(type: GoalType, today: string): string {
  const meta = GOAL_TYPE_BY_KEY[type]
  const d = new Date(today + 'T12:00:00')
  d.setDate(d.getDate() + meta.defaultDays)
  return d.toISOString().split('T')[0]
}

// Days remaining until deadline (negative if overdue)
export function daysUntilDeadline(deadline: string | null, today: string): number | null {
  if (!deadline) return null
  const a = new Date(today + 'T12:00:00').getTime()
  const b = new Date(deadline + 'T12:00:00').getTime()
  return Math.ceil((b - a) / 86400000)
}

// Compute "true" progress from milestones (overrides progress_pct when milestones exist)
export function progressFromMilestones(milestones: { done: boolean }[]): number {
  if (milestones.length === 0) return 0
  const doneCount = milestones.filter((m) => m.done).length
  return Math.round((doneCount / milestones.length) * 100)
}
