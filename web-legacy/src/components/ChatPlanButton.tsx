'use client'

import { useState } from 'react'
import { ClipboardList, Loader2, X } from 'lucide-react'
import PlanSteps from '@/components/PlanSteps'
import type { RecoveryPlan } from '@/lib/plan'

/**
 * "Build a plan from this" — turns the conversation into something real.
 *
 * The coach chat listens and works the problem out with you. This is the step
 * after: it sends the whole conversation to the deep path, where a stronger
 * model reads what was actually said and proposes concrete tasks, habits and
 * challenges. Nothing is created until the user ticks and confirms.
 *
 * It sits under the chat rather than firing on its own, because it is a paid
 * call and because a plan built before the user has finished explaining is a
 * plan for the wrong problem.
 */

interface Msg { role: 'user' | 'assistant'; content: string }

interface Props {
  messages: Msg[]
  /** Called after steps are created, so the page can refresh. */
  onCreated?: () => void
  onNavigate?: () => void
  /** Tighter spacing for the floating bubble. */
  compact?: boolean
}

export default function ChatPlanButton({ messages, onCreated, onNavigate, compact }: Props) {
  const [plan, setPlan] = useState<RecoveryPlan | null>(null)
  const [dropped, setDropped] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)

  // Nothing to plan from until the user has actually said something.
  const said = messages.filter((m) => m.role === 'user').length
  if (said === 0) return null

  async function build() {
    if (busy) return
    setBusy(true); setError(null); setNote(null)
    try {
      const res = await fetch('/api/ai/plan-from-chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: messages.slice(-12) }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data?.recovery) throw new Error(data?.error || 'Could not build a plan from this yet.')
      setPlan(data.recovery)
      setDropped(Number(data.dropped) || 0)
      if (data.fellBack) {
        setNote(`Built by the free model — Claude did not answer. ${data.fellBackReason ?? ''}`.trim())
      }
    } catch (e: any) {
      setError(e?.message || 'Could not build a plan from this yet.')
    } finally {
      setBusy(false)
    }
  }

  if (plan) {
    return (
      <div className={compact ? 'space-y-2' : 'space-y-3'}>
        <div className="flex items-center justify-between">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-gold">Proposed plan</p>
          <button onClick={() => setPlan(null)} aria-label="Dismiss the plan"
            className="rounded-lg p-1 text-muted-foreground transition-colors hover:text-foreground">
            <X size={14} />
          </button>
        </div>
        {note && <p className="text-[11px] text-muted-foreground">{note}</p>}
        <PlanSteps plan={plan} dropped={dropped} showHeader
          onCreated={onCreated} onNavigate={onNavigate}
          onDiscard={() => setPlan(null)} discardLabel="Discard" />
      </div>
    )
  }

  return (
    <div>
      <button onClick={build} disabled={busy}
        className="flex w-full items-center justify-center gap-2 rounded-xl border border-gold/30 bg-gold/10
                   py-2.5 text-xs font-semibold text-gold transition-all hover:bg-gold/15
                   active:scale-[0.99] disabled:opacity-50">
        {busy
          ? <><Loader2 size={14} className="animate-spin" /> Working out a plan…</>
          : <><ClipboardList size={14} /> Build a plan from this</>}
      </button>
      {error && <p className="mt-1.5 text-[11px] text-red-400">{error}</p>}
    </div>
  )
}
