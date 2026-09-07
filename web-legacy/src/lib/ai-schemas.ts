/**
 * Runtime schemas for the structured AI routes — AI-01.
 *
 * The suggestion shapes repeat across routes: a habit suggested by the goal
 * starter, the challenge starter, the habit starter and the health plan is the
 * same object, and all four feed the same habit-creation code. Defining it once
 * means a model that invents a habit `type` is rejected identically everywhere,
 * rather than in whichever route happened to check.
 */

import type { Schema } from './schema'

/** A suggested habit. Shared by four routes and by habit creation. */
export const HABIT_SUGGESTION: Schema = {
  kind: 'object',
  // A simple habit carries no target; counter and duration habits do. The
  // model is told which, but an omission must not fail the whole pack — the
  // routes already fall back sensibly when a target is missing.
  optional: ['target_value', 'unit', 'time_target_mins'],
  fields: {
    name: { kind: 'string', minLength: 2, maxLength: 80 },
    emoji: { kind: 'string', minLength: 1, maxLength: 8 },
    type: { kind: 'enum', values: ['simple', 'counter', 'duration'] },
    target_value: { kind: 'number', min: 1, max: 1000 },
    unit: { kind: 'string', maxLength: 24 },
    time_target_mins: { kind: 'number', min: 1, max: 600, int: true },
  },
}

const habitList = (max: number): Schema => ({
  kind: 'array', minItems: 1, maxItems: max, of: HABIT_SUGGESTION,
})

const shortText = (max = 400): Schema => ({ kind: 'string', minLength: 3, maxLength: max })

/** `/api/ai/habit-starter` */
export const HABIT_STARTER_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    pattern_insight: shortText(),
    how_to_succeed: shortText(),
    best_time: shortText(120),
    related_habits: habitList(4),
  },
}

/** `/api/ai/challenge-starter` */
export const CHALLENGE_STARTER_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    why_this_works: shortText(),
    hardest_obstacle: shortText(),
    daily_anchor: shortText(),
    supporting_habits: habitList(4),
  },
}

/** `/api/ai/goal-starter` */
export const GOAL_STARTER_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    summary: shortText(600),
    suggested_tasks: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: {
        kind: 'object',
        fields: {
          title: { kind: 'string', minLength: 3, maxLength: 120 },
          priority: { kind: 'enum', values: ['low', 'medium', 'high'] },
        },
      },
    },
    suggested_habits: habitList(6),
    suggested_challenges: {
      kind: 'array', minItems: 1, maxItems: 4,
      of: {
        kind: 'object',
        fields: {
          title: { kind: 'string', minLength: 3, maxLength: 120 },
          emoji: { kind: 'string', minLength: 1, maxLength: 8 },
          reason: shortText(300),
          frequency: { kind: 'enum', values: ['daily', 'weekly', 'monthly', 'yearly'] },
          // Bounded so a runaway number can't create a thousand-year challenge.
          duration_days: { kind: 'number', min: 1, max: 1825, int: true },
        },
      },
    },
  },
}

/** `/api/ai/goal-alignment` */
export const GOAL_ALIGNMENT_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    // The route clamps to 0..100 anyway; rejecting out-of-range here means a
    // wild value is retried rather than quietly squashed into a real-looking score.
    score: { kind: 'number', min: 0, max: 100 },
    doing_well: shortText(),
    missing: shortText(),
    suggested_action: shortText(),
  },
}

/** `/api/ai/health-recommend` */
export const HEALTH_RECOMMENDATION_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    summary: shortText(600),
    priorities: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: { kind: 'string', minLength: 3, maxLength: 200 },
    },
    suggested_goals: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: {
        kind: 'object',
        fields: {
          title: { kind: 'string', minLength: 3, maxLength: 120 },
          type: { kind: 'enum', values: ['weekly', 'monthly', 'yearly'] },
          category: { kind: 'enum', values: ['health', 'deen', 'personal'] },
        },
      },
    },
    suggested_habits: habitList(6),
  },
}

/**
 * `/api/ai/plan` — a plain-English intention turned into ONE proposed item.
 *
 * The model picks the kind and fills only the matching block; the route
 * checks the block for the chosen kind is actually present, since the
 * validator has no unions. Nothing here creates anything — a proposal is
 * shown to the user and only their confirmation goes through the normal,
 * authorised create routes.
 */
export const PLAN_SCHEMA: Schema = {
  kind: 'object',
  optional: ['task', 'habit', 'challenge'],
  fields: {
    kind: { kind: 'enum', values: ['task', 'habit', 'challenge'] },
    title: { kind: 'string', minLength: 3, maxLength: 120 },
    emoji: { kind: 'string', minLength: 1, maxLength: 8 },
    // Why this kind and not the others — shown to the user, so it has to be
    // a real reason rather than filler.
    reason: { kind: 'string', minLength: 10, maxLength: 300 },
    task: {
      kind: 'object',
      optional: ['due_time', 'note'],
      fields: {
        priority: { kind: 'enum', values: ['low', 'medium', 'high'] },
        due_time: { kind: 'string', minLength: 5, maxLength: 5 }, // HH:MM
        note: { kind: 'string', maxLength: 300 },
      },
    },
    habit: {
      kind: 'object',
      optional: ['target_value', 'unit', 'time_target_mins', 'schedule_days'],
      fields: {
        type: { kind: 'enum', values: ['simple', 'counter', 'duration'] },
        target_value: { kind: 'number', min: 1, max: 1000 },
        unit: { kind: 'string', maxLength: 24 },
        // 12 hours a day is 720; cap leaves room for that and rejects nonsense.
        time_target_mins: { kind: 'number', min: 1, max: 1440, int: true },
        schedule_kind: { kind: 'enum', values: ['daily', 'weekdays'] },
        schedule_days: {
          kind: 'array', maxItems: 7,
          of: { kind: 'enum', values: ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] },
        },
      },
    },
    challenge: {
      kind: 'object',
      fields: {
        frequency: { kind: 'enum', values: ['daily', 'weekly', 'monthly', 'yearly'] },
        duration_days: { kind: 'number', min: 1, max: 1825, int: true },
        requires_photo: { kind: 'boolean' },
      },
    },
  },
}

/**
 * `/api/ai/plan` in overcome mode — a small plan out of a slip. One to four
 * steps, each the same shape as a single plan item, so each one goes through
 * the same normaliser and the same create route.
 */
export const RECOVERY_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    // The slip restated in one plain line, so the user can see it was understood.
    problem: { kind: 'string', minLength: 5, maxLength: 200 },
    // The idea behind the plan. The only prose the model writes here.
    approach: { kind: 'string', minLength: 20, maxLength: 500 },
    steps: { kind: 'array', minItems: 1, maxItems: 4, of: PLAN_SCHEMA },
  },
}

/** Maps a structured-AI failure to the HTTP status a route should return. */
export function statusForAiFailure(code: string): number {
  if (code === 'rate_limited') return 429
  if (code === 'unauthorized' || code === 'not_configured') return 503
  return 502
}
