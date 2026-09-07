/**
 * "I want to work 12 hours a day for the next 30 days" — the pure half.
 *
 * The model proposes (see /api/ai/plan). These helpers check the proposal
 * makes sense, describe it for the user, and map it onto whichever create
 * route would record it. None of them create anything: only the user's
 * confirmation goes through /api/tasks, /api/habits or /api/challenges, the
 * same authorised mutations a manual entry uses.
 *
 * Kept out of the route so the rules are testable without a model.
 */

export interface PlanProposal {
  kind: 'task' | 'habit' | 'challenge'
  title: string
  emoji: string
  /** Why this kind, and any default the model had to assume. Shown to the user. */
  reason: string
  task?: { priority: 'low' | 'medium' | 'high'; due_time?: string; note?: string }
  habit?: {
    type: 'simple' | 'counter' | 'duration'
    target_value?: number
    unit?: string
    time_target_mins?: number
    schedule_kind: 'daily' | 'weekdays'
    schedule_days?: string[]
  }
  challenge?: {
    frequency: 'daily' | 'weekly' | 'monthly' | 'yearly'
    duration_days: number
    requires_photo: boolean
  }
}

export type NormalizeResult =
  | { ok: true; proposal: PlanProposal }
  | { ok: false; error: string }

/**
 * The checks the schema cannot express: the block for the chosen kind must
 * exist, and a habit that needs a number must have one. A weekday schedule
 * with no days named is just a daily habit, so it is downgraded rather than
 * rejected.
 */
export function normalizeProposal(p: PlanProposal): NormalizeResult {
  const block = p?.[p?.kind]
  if (!p?.kind || !block) {
    return { ok: false, error: 'The AI returned an incomplete plan. Try rephrasing.' }
  }

  if (p.kind === 'habit') {
    const h = { ...p.habit! }
    if (h.type === 'duration' && !(h.time_target_mins && h.time_target_mins > 0)) {
      return { ok: false, error: 'The AI did not say how many minutes. Try including a time.' }
    }
    if (h.type === 'counter' && !(h.target_value && h.target_value > 0)) {
      return { ok: false, error: 'The AI did not say how many. Try including a number.' }
    }
    if (h.schedule_kind === 'weekdays' && !(h.schedule_days?.length)) {
      h.schedule_kind = 'daily'
      h.schedule_days = undefined
    }
    return { ok: true, proposal: { ...p, habit: h } }
  }

  if (p.kind === 'challenge') {
    const c = { ...p.challenge! }
    c.duration_days = Math.min(1825, Math.max(1, Math.round(c.duration_days || 30)))
    return { ok: true, proposal: { ...p, challenge: c } }
  }

  return { ok: true, proposal: p }
}

const DAY_LABEL: Record<string, string> = {
  mon: 'Mon', tue: 'Tue', wed: 'Wed', thu: 'Thu', fri: 'Fri', sat: 'Sat', sun: 'Sun',
}

/** One short factual line about what confirming will actually create. */
export function describeProposal(p: PlanProposal): string {
  if (p.kind === 'task') {
    const t = p.task!
    const parts = [`${t.priority} priority`]
    if (t.due_time) parts.push(`due ${t.due_time}`)
    return parts.join(' · ')
  }

  if (p.kind === 'habit') {
    const h = p.habit!
    let what: string
    if (h.type === 'duration') {
      const mins = h.time_target_mins ?? 0
      what = mins >= 60 && mins % 30 === 0
        ? `${mins / 60}h a day`
        : `${mins} min a day`
    } else if (h.type === 'counter') {
      what = `${h.target_value} ${h.unit || ''} a day`.replace(/\s+a day$/, ' a day').trim()
    } else {
      what = 'yes / no each day'
    }
    const when = h.schedule_kind === 'weekdays' && h.schedule_days?.length
      ? h.schedule_days.map((d) => DAY_LABEL[d] ?? d).join(' · ')
      : 'daily'
    return `${what} · ${when}`
  }

  const c = p.challenge!
  const parts = [`${c.duration_days} days`, `${c.frequency} check-in`]
  if (c.requires_photo) parts.push('photo proof')
  return parts.join(' · ')
}

export interface CreateRequest {
  /** The route that records this kind. */
  path: string
  body: Record<string, unknown>
  /** Where to send the user afterwards. */
  href: string
  /** Human label for the confirmation. */
  label: string
}

/**
 * Maps a confirmed proposal onto the create route's contract. The request id
 * rides along so a retried confirmation cannot create the item twice.
 */
export function proposalToCreateBody(p: PlanProposal, requestId: string): CreateRequest {
  if (p.kind === 'task') {
    const t = p.task!
    return {
      path: '/api/tasks',
      href: '/tasks',
      label: 'task',
      body: {
        title: p.title,
        note: t.note ?? null,
        priority: t.priority,
        type: 'daily',
        due_time: t.due_time ?? null,
        request_id: requestId,
      },
    }
  }

  if (p.kind === 'habit') {
    const h = p.habit!
    return {
      path: '/api/habits',
      href: '/habits',
      label: 'habit',
      body: {
        name: p.title,
        emoji: p.emoji,
        type: h.type,
        target_value: h.type === 'counter' ? h.target_value : 1,
        unit: h.unit ?? '',
        time_target_mins: h.type === 'duration' ? h.time_target_mins : 0,
        schedule_kind: h.schedule_kind,
        schedule_days: h.schedule_kind === 'weekdays' ? h.schedule_days : null,
        category: 'custom',
        score_weight: 2,
        // The model's reason doubles as the "why" the habit page shows.
        why: p.reason,
      },
    }
  }

  const c = p.challenge!
  return {
    path: '/api/challenges',
    href: '/challenges',
    label: 'challenge',
    body: {
      title: p.title,
      emoji: p.emoji,
      description: p.reason,
      frequency: c.frequency,
      duration_days: c.duration_days,
      requires_photo: c.requires_photo,
      request_id: requestId,
    },
  }
}
