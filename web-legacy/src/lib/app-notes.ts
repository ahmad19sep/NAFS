/**
 * Bugs and ideas about the app itself — the pure half.
 *
 * The point of the list is the moment it gets handed over: "here is what I
 * hit in the last two weeks, fix these." So the formatter matters as much as
 * the storage. It writes something a person or a model can act on without
 * having to ask what any line meant: what kind, where, when, and the detail
 * in the user's own words.
 */

export type NoteKind = 'bug' | 'idea'
export type NoteStatus = 'open' | 'done' | 'dismissed'

export interface AppNote {
  id: string
  kind: NoteKind
  title: string
  detail: string | null
  where_seen: string | null
  status: NoteStatus
  created_at: string
  resolved_at: string | null
}

export const KIND_LABEL: Record<NoteKind, string> = { bug: 'Bug', idea: 'Idea' }

/** Days since a note was written, for "13 days ago" and for ordering by age. */
export function ageInDays(createdAt: string, now: Date = new Date()): number {
  const then = new Date(createdAt).getTime()
  if (!Number.isFinite(then)) return 0
  return Math.max(0, Math.floor((now.getTime() - then) / 86_400_000))
}

/** "today", "yesterday", "5 days ago". */
export function ageLabel(createdAt: string, now: Date = new Date()): string {
  const d = ageInDays(createdAt, now)
  if (d === 0) return 'today'
  if (d === 1) return 'yesterday'
  return `${d} days ago`
}

/**
 * The whole open list as plain text, ready to paste into a message.
 *
 * Bugs first — something broken outranks something missing — then oldest
 * first inside each group, because the note that has waited longest is the
 * one most likely to be forgotten again.
 */
export function formatNotesForHandoff(notes: AppNote[], now: Date = new Date()): string {
  const open = notes.filter((n) => n.status === 'open')
  if (open.length === 0) return 'No open items.'

  const rank: Record<NoteKind, number> = { bug: 0, idea: 1 }
  const sorted = [...open].sort((a, b) =>
    (rank[a.kind] - rank[b.kind]) || (a.created_at < b.created_at ? -1 : a.created_at > b.created_at ? 1 : 0))

  const bugs = sorted.filter((n) => n.kind === 'bug').length
  const ideas = sorted.length - bugs
  const parts = [
    `NAFS — ${sorted.length} open item${sorted.length === 1 ? '' : 's'}` +
    ` (${bugs} bug${bugs === 1 ? '' : 's'}, ${ideas} idea${ideas === 1 ? '' : 's'})`,
    `Listed ${now.toISOString().slice(0, 10)}. Oldest first, bugs before ideas.`,
    '',
  ]

  sorted.forEach((n, i) => {
    const where = n.where_seen?.trim() ? ` · ${n.where_seen.trim()}` : ''
    parts.push(`${i + 1}. [${KIND_LABEL[n.kind].toUpperCase()}]${where} ${n.title.trim()}`)
    if (n.detail?.trim()) {
      for (const line of n.detail.trim().split('\n')) parts.push(`   ${line.trim()}`)
    }
    parts.push(`   added ${ageLabel(n.created_at, now)} (${n.created_at.slice(0, 10)})`)
    parts.push('')
  })

  return parts.join('\n').trimEnd()
}
