'use client'

import { useState } from 'react'
import { TrendingUp, Loader2, RefreshCw } from 'lucide-react'
import RichText from '@/components/RichText'
import { timeAgo } from '@/lib/utils'

/**
 * The growth review: what you are improving at, where you lack, the pattern
 * underneath, three rules for the week, one question.
 *
 * Runs on demand, not on a schedule — it is the one paid call in the app
 * when Claude is configured, so the user decides when to spend it. Once per
 * day; asking again the same day returns the morning's review. The card
 * says which model answered, honestly, including when Claude was set up but
 * unavailable and the free model stepped in.
 */

export interface GrowthReview {
  id: string
  content_md: string
  generated_at: string
  model_used?: string | null
}

interface Props {
  initial: GrowthReview | null
  /** Which model will answer right now. From the server, so it is true. */
  deepModel: 'anthropic' | 'cloudflare'
}

export default function GrowthReviewCard({ initial, deepModel }: Props) {
  const [review, setReview] = useState<GrowthReview | null>(initial)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)

  async function run() {
    if (running) return
    setRunning(true)
    setError(null)
    setNote(null)
    try {
      const res = await fetch('/api/ai/growth-review', { method: 'POST' })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data?.review) throw new Error(data?.error || 'The review could not be written right now.')
      setReview(data.review)
      if (data.cached) {
        setNote('Already reviewed today — this is today’s. Tomorrow brings a fresh one.')
      } else {
        const parts: string[] = []
        if (data.fellBack) {
          parts.push(`Written by the free model — Claude did not answer. ${data.fellBackReason ?? ''}`.trim())
        }
        if (data.saved === false && data.hint) parts.push(data.hint)
        if (parts.length) setNote(parts.join(' '))
      }
    } catch (e: any) {
      setError(e?.message || 'The review could not be written right now.')
    } finally {
      setRunning(false)
    }
  }

  const modelLine = deepModel === 'anthropic'
    ? 'Written by Claude · once a day'
    : 'Written by the free model · once a day'

  return (
    <div className="nafs-card p-5 mb-6">
      <div className="flex items-center justify-between mb-1">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gold/10 border border-gold/20">
            <TrendingUp size={15} className="text-gold" />
          </div>
          <div>
            <p className="text-sm font-bold text-foreground leading-tight">Growth review</p>
            <p className="text-[10px] text-muted-foreground">{modelLine}</p>
          </div>
        </div>
        {review && (
          <span className="text-[11px] text-muted-foreground">{timeAgo(review.generated_at)}</span>
        )}
      </div>

      <p className="mt-2 text-xs text-muted-foreground leading-relaxed">
        What you are improving at, where you lack, the pattern underneath, three rules
        for the week, and one question — from your last 30 days, this week against last,
        and this month against last.
      </p>

      {review ? (
        <div className="mt-4 text-sm text-foreground leading-relaxed">
          <RichText text={review.content_md} />
        </div>
      ) : (
        <p className="mt-4 text-sm text-muted-foreground">No review yet.</p>
      )}

      {note && <p className="mt-3 text-[11px] text-muted-foreground">{note}</p>}
      {error && <p className="mt-3 text-xs text-red-400">{error}</p>}

      <button onClick={run} disabled={running}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-4 py-3
                   text-sm font-semibold text-white transition-all active:scale-[0.99] disabled:opacity-50">
        {running
          ? <><Loader2 size={15} className="animate-spin" /> Reading your month…</>
          : review
            ? <><RefreshCw size={15} /> Review again</>
            : <><TrendingUp size={15} /> Write my review</>}
      </button>
    </div>
  )
}
