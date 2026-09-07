'use client'

import { useRef, useState } from 'react'
import { Check, Loader2 } from 'lucide-react'
import { LIFE_TOPICS, latestBySubject, type CoachNote } from '@/lib/coach-notes'

/**
 * The coach's four questions about a life.
 *
 * What you want, what is going on, what your days actually go to, and the
 * thing you would do if you knew it would work. The answers are kept in the
 * user's own words and read by the coach alongside their data, so it can say
 * when what they are doing and what they said they want have come apart.
 *
 * Each save is a new note; the coach reads the latest per question and can
 * see when an answer changed.
 */

interface Props {
  /** Life notes, newest first. */
  notes: CoachNote[]
}

function newRequestId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export default function LifeNotes({ notes }: Props) {
  const latest = latestBySubject(notes, 'life')
  return (
    <div className="nafs-card p-5">
      <p className="section-header">Tell the coach about your life</p>
      <p className="mt-1 text-xs text-muted-foreground leading-relaxed">
        Four questions. Your answers are kept in your words and read with your data,
        so the coach can tell you when what you are doing and what you said you want
        have come apart. Update them whenever things change.
      </p>
      <div className="mt-4 space-y-5">
        {LIFE_TOPICS.map((t) => (
          <TopicField key={t.key} topicKey={t.key} question={t.question} hint={t.hint}
            previous={latest.get(t.key) ?? null} />
        ))}
      </div>
    </div>
  )
}

function TopicField({ topicKey, question, hint, previous }: {
  topicKey: string
  question: string
  hint: string
  previous: CoachNote | null
}) {
  const [text, setText] = useState(previous?.content ?? '')
  const [savedText, setSavedText] = useState(previous?.content ?? '')
  const [savedOn, setSavedOn] = useState<string | null>(previous?.date ?? null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const requestId = useRef<string | null>(null)

  const dirty = text.trim() !== savedText.trim()

  async function save() {
    const content = text.trim()
    if (!content || !dirty || saving) return
    if (!requestId.current) requestId.current = newRequestId()
    setSaving(true)
    setError(null)
    try {
      const res = await fetch('/api/coach/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ kind: 'life', subject: topicKey, content, request_id: requestId.current }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) throw new Error(data?.error || 'Could not save')
      setSavedText(content)
      setSavedOn(data?.note?.date ?? savedOn)
      requestId.current = null
    } catch (e: any) {
      setError(e?.message || 'Could not save — try again')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <p className="text-sm font-semibold text-foreground">{question}</p>
      <p className="text-[11px] text-muted-foreground">{hint}</p>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={3}
        placeholder="In your own words…"
        className="log-input mt-2 w-full resize-none text-sm"
      />
      <div className="mt-1.5 flex items-center gap-3 min-h-[24px]">
        {dirty ? (
          <button onClick={save} disabled={!text.trim() || saving}
            className="flex items-center gap-1 rounded-lg bg-gold px-3 py-1.5 text-[11px] font-semibold text-[#0b1a2b]
                       transition-all active:scale-95 disabled:opacity-40">
            {saving ? <Loader2 size={12} className="animate-spin" /> : 'Save'}
          </button>
        ) : savedOn ? (
          <span className="flex items-center gap-1 text-[11px] text-emerald-300">
            <Check size={11} /> Saved {savedOn}
          </span>
        ) : null}
        {error && <span className="text-[11px] text-red-400">{error}</span>}
      </div>
    </div>
  )
}
