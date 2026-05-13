// Badge system. Badges are auto-awarded based on user activity.
// Definitions live here so the same module can be imported on the server (to
// compute earned status) and on the client (to render the gallery).

export type BadgeFeature = 'overall' | 'habits' | 'deen' | 'challenges' | 'tasks' | 'goals' | 'health'
export type BadgeTier = 'bronze' | 'silver' | 'gold' | 'platinum'

export interface BadgeDef {
  id: string
  name: string
  description: string
  emoji: string
  feature: BadgeFeature
  tier: BadgeTier
}

// All the data a check function might need
export interface BadgeContext {
  // Profile
  daysSinceSignup: number
  hasHeightWeight: boolean

  // Habits
  habitsCount: number
  maxHabitStreak: number

  // Deen
  hasAnyPrayerLog: boolean
  fajrLast7Done: number      // count of Fajr prayed in last 7 days
  hasJamatPrayer: boolean    // any prayer at status=2 ever
  perfectPrayerDays: number  // count of past days where all 5 prayers >= 1

  // Challenges
  challengesCreatedCount: number
  challengesCompletedCount: number

  // Tasks
  tasksCompletedAllTime: number
  hadPerfectDailyTaskDay: boolean  // a day with ≥3 daily tasks all done

  // Goals
  goalsCount: number
  goalsCompletedCount: number
  yearlyGoalCompletedCount: number

  // Health
  waterTargetStreak7: boolean
  has10kStepsDay: boolean

  // Overall
  consistencyKing7: boolean   // any 7-day streak with avg score >= 80
}

export const BADGES: BadgeDef[] = [
  // ---------- Habits ----------
  { id: 'habit-first',     name: 'First Habit',       description: 'Created your first habit',                emoji: '🌱', feature: 'habits',     tier: 'bronze'   },
  { id: 'habit-streak-7',  name: '7-Day Streak',      description: 'Kept a habit for 7 days in a row',        emoji: '🔥', feature: 'habits',     tier: 'silver'   },
  { id: 'habit-streak-30', name: '30-Day Streak',     description: 'Kept a habit for 30 days in a row',       emoji: '⚡', feature: 'habits',     tier: 'gold'     },
  { id: 'habit-streak-100',name: 'Century Streak',    description: 'Kept a habit for 100 days in a row',      emoji: '💎', feature: 'habits',     tier: 'platinum' },

  // ---------- Deen ----------
  { id: 'deen-first',      name: 'First Prayer',      description: 'Logged your first prayer',                emoji: '🕌', feature: 'deen',       tier: 'bronze'   },
  { id: 'deen-fajr-7',     name: 'Fajr Champion',     description: 'Prayed Fajr 7 days in a row',             emoji: '☀️', feature: 'deen',       tier: 'silver'   },
  { id: 'deen-jamat',      name: 'In Jamat',          description: 'Prayed any prayer in jamat',              emoji: '🌟', feature: 'deen',       tier: 'silver'   },
  { id: 'deen-perfect-30', name: '30 Perfect Days',   description: '30 days with all 5 prayers prayed',       emoji: '📿', feature: 'deen',       tier: 'platinum' },

  // ---------- Challenges ----------
  { id: 'chall-first',     name: 'First Challenge',   description: 'Started your first challenge',            emoji: '🎯', feature: 'challenges', tier: 'bronze'   },
  { id: 'chall-conquer',   name: 'Conqueror',         description: 'Completed a full challenge',              emoji: '🏆', feature: 'challenges', tier: 'gold'     },
  { id: 'chall-master',    name: 'Challenge Master',  description: 'Completed 5 challenges',                  emoji: '👑', feature: 'challenges', tier: 'platinum' },

  // ---------- Tasks ----------
  { id: 'task-first',      name: 'First Task Done',   description: 'Completed your first task',               emoji: '✅', feature: 'tasks',      tier: 'bronze'   },
  { id: 'task-100',        name: '100 Tasks Done',    description: 'Completed 100 tasks lifetime',            emoji: '💪', feature: 'tasks',      tier: 'gold'     },
  { id: 'task-perfect',    name: 'Perfect Day',       description: 'Completed all 3+ daily tasks in one day', emoji: '🌟', feature: 'tasks',      tier: 'silver'   },

  // ---------- Goals ----------
  { id: 'goal-first',      name: 'First Goal',        description: 'Set your first goal',                     emoji: '⭐', feature: 'goals',      tier: 'bronze'   },
  { id: 'goal-completed',  name: 'Goal Achiever',     description: 'Completed a goal',                        emoji: '🎉', feature: 'goals',      tier: 'gold'     },
  { id: 'goal-yearly',     name: 'Yearly Champion',   description: 'Completed a yearly goal',                 emoji: '🏔️', feature: 'goals',      tier: 'platinum' },

  // ---------- Health ----------
  { id: 'health-setup',    name: 'Health Profile',    description: 'Set up your height & weight',             emoji: '❤️', feature: 'health',     tier: 'bronze'   },
  { id: 'health-water-7',  name: 'Hydration Hero',    description: 'Hit 8 glasses of water 7 days in a row',  emoji: '💧', feature: 'health',     tier: 'silver'   },
  { id: 'health-10k',      name: 'Step Master',       description: 'Hit 10K steps in a single day',           emoji: '🚶', feature: 'health',     tier: 'silver'   },

  // ---------- Overall ----------
  { id: 'overall-day-1',   name: 'Welcome',           description: 'Joined NAFS',                             emoji: '🌅', feature: 'overall',    tier: 'bronze'   },
  { id: 'overall-week-1',  name: '1-Week Warrior',    description: 'Active for 7+ days since signup',         emoji: '📅', feature: 'overall',    tier: 'silver'   },
  { id: 'overall-month-1', name: 'Monthly Member',    description: '30+ days since you joined',               emoji: '🗓️', feature: 'overall',    tier: 'gold'     },
  { id: 'overall-king',    name: 'Consistency King',  description: '7 straight days at 80%+ score',           emoji: '✨', feature: 'overall',    tier: 'platinum' },
]

// ---------- Tier styling ----------
export const TIER_COLORS: Record<BadgeTier, { ring: string; bg: string; text: string; label: string }> = {
  bronze:   { ring: 'ring-amber-700/40',   bg: 'bg-amber-700/15',   text: 'text-amber-500',  label: 'Bronze'   },
  silver:   { ring: 'ring-slate-300/40',   bg: 'bg-slate-300/15',   text: 'text-slate-300',  label: 'Silver'   },
  gold:     { ring: 'ring-gold/50',         bg: 'bg-gold/15',        text: 'text-gold',       label: 'Gold'     },
  platinum: { ring: 'ring-fuchsia-400/50',  bg: 'bg-fuchsia-500/15', text: 'text-fuchsia-300', label: 'Platinum' },
}

// ---------- Earned-state computation ----------
export function earnedBadgeIds(ctx: BadgeContext): string[] {
  const earned: string[] = []
  const add = (id: string) => earned.push(id)

  // Habits
  if (ctx.habitsCount >= 1)         add('habit-first')
  if (ctx.maxHabitStreak >= 7)      add('habit-streak-7')
  if (ctx.maxHabitStreak >= 30)     add('habit-streak-30')
  if (ctx.maxHabitStreak >= 100)    add('habit-streak-100')

  // Deen
  if (ctx.hasAnyPrayerLog)          add('deen-first')
  if (ctx.fajrLast7Done >= 7)       add('deen-fajr-7')
  if (ctx.hasJamatPrayer)           add('deen-jamat')
  if (ctx.perfectPrayerDays >= 30)  add('deen-perfect-30')

  // Challenges
  if (ctx.challengesCreatedCount >= 1)   add('chall-first')
  if (ctx.challengesCompletedCount >= 1) add('chall-conquer')
  if (ctx.challengesCompletedCount >= 5) add('chall-master')

  // Tasks
  if (ctx.tasksCompletedAllTime >= 1)   add('task-first')
  if (ctx.tasksCompletedAllTime >= 100) add('task-100')
  if (ctx.hadPerfectDailyTaskDay)       add('task-perfect')

  // Goals
  if (ctx.goalsCount >= 1)              add('goal-first')
  if (ctx.goalsCompletedCount >= 1)     add('goal-completed')
  if (ctx.yearlyGoalCompletedCount >= 1) add('goal-yearly')

  // Health
  if (ctx.hasHeightWeight)              add('health-setup')
  if (ctx.waterTargetStreak7)           add('health-water-7')
  if (ctx.has10kStepsDay)               add('health-10k')

  // Overall
  add('overall-day-1')   // earned just by existing
  if (ctx.daysSinceSignup >= 7)  add('overall-week-1')
  if (ctx.daysSinceSignup >= 30) add('overall-month-1')
  if (ctx.consistencyKing7)      add('overall-king')

  return earned
}

export function getBadge(id: string): BadgeDef | undefined {
  return BADGES.find((b) => b.id === id)
}

export function badgesByFeature(): Record<BadgeFeature, BadgeDef[]> {
  const out: Record<BadgeFeature, BadgeDef[]> = {
    overall: [], habits: [], deen: [], challenges: [], tasks: [], goals: [], health: [],
  }
  for (const b of BADGES) out[b.feature].push(b)
  return out
}
