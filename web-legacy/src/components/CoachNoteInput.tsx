'use client'

import { useRef, useState } from 'react'
import { Check, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { CoachNote, NoteKind } from '@/lib/coach-notes'

/**
 * A one-line way to tell the coach why.
 *
 * Sits under a repeated miss ("Why?") or under a bad day ("What's getting in
 * the way today?"). Opens a small box, saves the answer as a coach note, and
 * from then on the coach can quote it back. If the user already answered for
 * this same thing before, that answer is shown first — the point is to notice
 * when the reason is the same one again.
 *
 * A failed save keeps the text in the box so nothing typed is lost.
 */

interface Props {
  kind: NoteKind
  subject: string | null
  /** The last thing they said about this same subject, if any. */
  previous?: Pick<CoachNote, 'content' | 'date'> | null
  /** The button that opens the box. */
  prompt: string
  placeholder: string
  /** Matches the card it sits in. */
  tone?: 'orange' | 'gold'
}

function newRequestId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export default function CoachNoteInput({ kind, subject, previous, prompt, placeholder, tone = 'gold' }: Props) {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestId = useRef<string | null>(null)

  const linkTone = tone === 'orange'
    ? 'text-orange-300 hover:text-orange-200'
    : 'text-gold hover:text-gold/80'

  function start() {
    requestId.current = newRequestId()
    setError(null)
    setOpen(true)
  }

  async function save() {
    const content = text.trim()
    if (!content || saving) return
    setSaving(true)
    setError(null)
    try {
      const res = await fetch('/api/coach/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ kind, subject, content, request_id: requestId.current }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) throw new Error(data?.error || 'Could not save')
      setSaved(true)
      setOpen(false)
    } catch (e: any) {
      setError(e?.message || 'Could not save — try again')
    } finally {
      setSaving(false)
    }
  }

  if (saved) {
    return (
      <p className="mt-1 flex items-center gap-1 text-[11px] text-emerald-300">
        <Check size={11} /> Saved — the coach will remember.
      </p>
    )
  }

  return (
    <div className="mt-1">
      {previous && (
        <p className="text-[11px] text-muted-foreground leading-snug">
          Last time you said:{' '}
          <span className="italic text-foreground/85">&ldquo;{previous.content}&rdquo;</span>
          <span className="opacity-70"> · {previous.date}</span>
        </p>
      )}

      {!open ? (
        <button onClick={start} className={cn('text-[11px] font-semibold', linkTone)}>
          {previous ? 'Same thing again? Tell the coach' : prompt}
        </button>
      ) : (
        <div className="mt-1.5 space-y-1.5">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={2}
            autoFocus
            placeholder={placeholder}
            className="log-input w-full resize-none text-sm"
          />
          <div className="flex items-center gap-3">
            <button onClick={save} disabled={!text.trim() || saving}
              className="flex items-center gap-1 rounded-lg bg-gold px-3 py-1.5 text-[11px] font-semibold text-[#0b1a2b]
                         transition-all active:scale-95 disabled:opacity-40">
              {saving ? <Loader2 size={12} className="animate-spin" /> : 'Save'}
            </button>
            <button onClick={() => setOpen(false)} className="text-[11px] text-muted-foreground">Cancel</button>
            {error && <span className="text-[11px] text-red-400">{error}</span>}
          </div>
        </div>
      )}
    </div>
  )
}
