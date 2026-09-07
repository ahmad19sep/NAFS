/**
 * Coach notes — what the user told the coach, in their own words.
 *
 * The coach's memory. A reason given for a miss, what is going on in their
 * life, what they want. Saved when the user writes it, read back into every
 * coach answer, so the coach can say "last time you said…" and can notice
 * when what they said they wanted and what the records show have come apart.
 *
 * Nothing here is generated. Every note is the user's own text, and the coach
 * is prompted to quote it, not paraphrase it into something they did not say.
 */

import type { RepeatedMiss } from './misses'

export type NoteKind = 'miss_reason' | 'low_score' | 'life'

export interface CoachNote {
  id?: string
  kind: NoteKind
  /**
   * What the note is about.
   *  miss_reason: 'habit:<id>' or 'prayer:<key>' — see subjectFor().
   *  life: a LIFE_TOPICS key.
   *  low_score: null; the date says which day.
   */
  subject: string | null
  content: string
  /** The user's own calendar day it was written on. */
  date: string
}

/**
 * The questions the coach asks about a life, once, and again whenever the
 * answer changes. The latest answer per topic is what the coach reads.
 */
export const LIFE_TOPICS = [
  {
    key: 'want',
    question: 'What do you want your life to look like in three years?',
    hint: 'Work, faith, family, money, health — whatever actually matters to you. Be specific.',
  },
  {
    key: 'now',
    question: "What's going on in your life right now?",
    hint: 'The real situation: what is hard, what is going well, what changed recently.',
  },
  {
    key: 'doing',
    question: 'What are your days actually going to?',
    hint: 'Not the plan — the honest answer.',
  },
  {
    key: 'dream',
    question: 'What would you do if you knew it would work?',
    hint: 'The one you do not say out loud.',
  },
] as const

export type LifeTopic = (typeof LIFE_TOPICS)[number]['key']

export function subjectFor(m: Pick<RepeatedMiss, 'kind' | 'id'>): string {
  return `${m.kind}:${m.id}`
}

/** The newest note per subject, for "last time you said". */
export function latestBySubject(notes: CoachNote[], kind: NoteKind): Map<string, CoachNote> {
  const out = new Map<string, CoachNote>()
  for (const n of newestFirst(notes)) {
    if (n.kind !== kind) continue
    const key = n.subject ?? ''
    if (!out.has(key)) out.set(key, n)
  }
  return out
}

/** What the coach reads. Compact, newest first, capped so it stays cheap. */
export interface CoachMemory {
  /** Latest answer per life question, keyed by the question itself. */
  about_their_life: Record<string, { said: string; on: string }>
  /** Reasons given for a miss, keyed by what was missed, newest first. */
  reasons_given_for_misses: Record<string, { on: string; said: string }[]>
  /** What they said on days that went badly, newest first. */
  when_a_day_went_badly: { on: string; said: string }[]
}

export interface MemoryOptions {
  /** Turns 'habit:<id>' into the habit's name for the model. Defaults to the subject itself. */
  labelFor?: (subject: string) => string
  /** Reasons kept per subject. */
  perSubject?: number
  /** Bad-day notes kept. */
  lowScore?: number
}

export function buildCoachMemory(notes: CoachNote[], opts: MemoryOptions = {}): CoachMemory {
  const labelFor = opts.labelFor ?? ((s: string) => s)
  const perSubject = opts.perSubject ?? 4
  const lowScore = opts.lowScore ?? 4

  const memory: CoachMemory = {
    about_their_life: {},
    reasons_given_for_misses: {},
    when_a_day_went_badly: [],
  }

  for (const n of newestFirst(notes)) {
    const said = n.content.trim()
    if (!said) continue

    if (n.kind === 'life') {
      const topic = LIFE_TOPICS.find((t) => t.key === n.subject)
      const key = topic?.question ?? n.subject ?? 'about them'
      if (!memory.about_their_life[key]) memory.about_their_life[key] = { said, on: n.date }
      continue
    }

    if (n.kind === 'miss_reason') {
      const key = labelFor(n.subject ?? '')
      const list = (memory.reasons_given_for_misses[key] ??= [])
      if (list.length < perSubject) list.push({ on: n.date, said })
      continue
    }

    if (memory.when_a_day_went_badly.length < lowScore) {
      memory.when_a_day_went_badly.push({ on: n.date, said })
    }
  }

  return memory
}

/** True when there is anything at all for the coach to remember. */
export function memoryIsEmpty(m: CoachMemory): boolean {
  return Object.keys(m.about_their_life).length === 0
    && Object.keys(m.reasons_given_for_misses).length === 0
    && m.when_a_day_went_badly.length === 0
}

/** By date, newest first; ties keep their given order (the query orders by created_at desc). */
function newestFirst(notes: CoachNote[]): CoachNote[] {
  return notes
    .map((n, i) => ({ n, i }))
    .sort((a, b) => (a.n.date < b.n.date ? 1 : a.n.date > b.n.date ? -1 : a.i - b.i))
    .map((x) => x.n)
}
