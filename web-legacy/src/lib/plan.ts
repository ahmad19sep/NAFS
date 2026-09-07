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
/**
 * One emoji character: a surrogate pair (most emoji), or one of the symbol
 * ranges that hold the rest (⏰ ⏱️ ♂), plus the joiners that glue a sequence
 * together. Written without the /u flag and \p{...} classes, which need a
 * newer compile target than this project sets.
 */
const EMOJI_CHAR =
  '(?:[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]' +
  '|[\\u2190-\\u21FF\\u2300-\\u23FF\\u25A0-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u200D\\u20E3])'
const LEADING_EMOJI = new RegExp(`^(?:${EMOJI_CHAR}+[\\s\\u200B]*)+`)

/**
 * The emoji is its own field and is rendered beside the title, so a title
 * that also starts with one shows two ("🗓️" + "📅 Gym plan" — seen live).
 * Strips leading emoji, and keeps the original when stripping would leave
 * nothing worth showing.
 */
export function stripLeadingEmoji(title: string): string {
  const stripped = title.replace(LEADING_EMOJI, '').trim()
  return stripped.length >= 2 ? stripped : title.trim()
}

/** The fields that belong inside each kind's block. */
const BLOCK_FIELDS: Record<PlanProposal['kind'], string[]> = {
  task: ['priority', 'due_time', 'note'],
  habit: ['type', 'target_value', 'unit', 'time_target_mins', 'schedule_kind', 'schedule_days'],
  challenge: ['frequency', 'duration_days', 'requires_photo'],
}

/**
 * Re-nest a step whose fields were written flat.
 *
 * Models flatten nested objects. Measured live on claude-sonnet-5: every step
 * came back as `{kind:"task", title, emoji, reason, priority, due_time, note}`
 * with no `task: {...}` wrapper. The runtime schema tolerates unknown keys, so
 * this passed validation and then lost every step here — a whole plan thrown
 * away over punctuation.
 *
 * Which fields belong to which kind is fixed and unambiguous, so lifting them
 * is deterministic, not a guess. A step that already has its block is left
 * exactly as it is.
 */
export function liftFlattenedBlock(p: PlanProposal): PlanProposal {
  if (!p?.kind || !BLOCK_FIELDS[p.kind] || p[p.kind]) return p

  const flat = p as unknown as Record<string, unknown>
  const block: Record<string, unknown> = {}
  for (const field of BLOCK_FIELDS[p.kind]) {
    if (flat[field] !== undefined) block[field] = flat[field]
  }
  if (Object.keys(block).length === 0) return p

  return { ...p, [p.kind]: block } as PlanProposal
}

/**
 * "22:45," and "9:05" both mean a time; "later" does not. Returns HH:MM or
 * undefined, so a stray character cannot fail the whole proposal.
 */
export function cleanTime(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const m = value.match(/(\d{1,2}):(\d{2})/)
  if (!m) return undefined
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return undefined
  return `${String(h).padStart(2, '0')}:${m[2]}`
}

export function normalizeProposal(raw: PlanProposal): NormalizeResult {
  const lifted = liftFlattenedBlock(raw)
  const block = lifted?.[lifted?.kind]
  if (!lifted?.kind || !block) {
    return { ok: false, error: 'The AI returned an incomplete plan. Try rephrasing.' }
  }

  // Applies to every kind, so it happens before the branches rather than
  // inside one of them.
  const p = { ...lifted, title: stripLeadingEmoji(lifted.title ?? '') }

  if (p.kind === 'task') {
    const t = { ...p.task! }
    // A model will write "22:45," or "9:05"; the create route wants HH:MM.
    t.due_time = cleanTime(t.due_time)
    return { ok: true, proposal: { ...p, task: t } }
  }

  if (p.kind === 'habit') {
    const h = { ...p.habit! }
    // Seen live: a duration habit with the minutes in target_value/unit
    // ("60", "minutes") and time_target_mins missing. The intent is plain,
    // so it is read as minutes rather than rejected.
    if (h.type === 'duration' && !(h.time_target_mins && h.time_target_mins > 0)) {
      const mins = minutesFrom(h.target_value, h.unit)
      if (mins) { h.time_target_mins = mins; h.target_value = undefined; h.unit = undefined }
    }
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

/** A number with a time unit, as minutes; null when the unit is not a time. */
export function minutesFrom(value: number | undefined, unit: string | undefined): number | null {
  if (!value || value <= 0) return null
  const u = (unit ?? '').trim().toLowerCase()
  if (/^(min|mins|minute|minutes|m)$/.test(u)) return Math.round(value)
  if (/^(h|hr|hrs|hour|hours)$/.test(u)) return Math.round(value * 60)
  return null
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

/**
 * A way out of a slip: "I've been scrolling three hours a night" becomes a
 * first move for today, a habit that fills the gap, and maybe a short
 * challenge. Each step is an ordinary proposal and is confirmed one by one.
 */
export interface RecoveryPlan {
  problem: string
  approach: string
  steps: PlanProposal[]
}

export type RecoveryResult =
  | { ok: true; plan: RecoveryPlan; dropped: number }
  | { ok: false; error: string }

const MAX_STEPS = 4

/**
 * Every step goes through normalizeProposal. A step that fails is dropped
 * rather than failing the whole plan — one bad item should not cost the user
 * the three good ones — and the count is returned so the UI can say so. At
 * most one challenge: two fixed-length commitments for one slip is a burden,
 * not a plan.
 */
export function normalizeRecovery(r: RecoveryPlan): RecoveryResult {
  if (!r || !Array.isArray(r.steps)) {
    return { ok: false, error: 'The AI returned an incomplete plan. Try rephrasing.' }
  }

  const steps: PlanProposal[] = []
  let dropped = 0
  let hasChallenge = false

  for (const s of r.steps) {
    const n = normalizeProposal(s)
    if (!n.ok) { dropped++; continue }
    if (n.proposal.kind === 'challenge') {
      if (hasChallenge) { dropped++; continue }
      hasChallenge = true
    }
    steps.push(n.proposal)
    if (steps.length === MAX_STEPS) break
  }

  if (steps.length === 0) {
    return {
      ok: false,
      error: 'The AI could not turn that into concrete steps. Add a detail — when it happens, or what you used to do instead.',
    }
  }

  return {
    ok: true,
    plan: {
      problem: (r.problem ?? '').trim(),
      approach: (r.approach ?? '').trim(),
      steps,
    },
    dropped,
  }
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
