'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, LifeBuoy, ChevronRight } from 'lucide-react'
import PlanSteps from '@/components/PlanSteps'
import type { RecoveryPlan } from '@/lib/plan'

/**
 * "I've slipped into scrolling" → a small plan out of it.
 *
 * This half asks the question and calls the planner. Everything after — the
 * steps, the ticking, the creating — is PlanSteps, shared with the coach
 * chat so a plan behaves the same wherever it was proposed.
 */

interface Props {
  onClose: () => void
  initialIntent?: string
}

export default function OvercomePanel({ onClose, initialIntent }: Props) {
  const router = useRouter()
  const [intent, setIntent] = useState(initialIntent ?? '')
  const [busy, setBusy] = useState(false)
  const [plan, setPlan] = useState<RecoveryPlan | null>(null)
  const [dropped, setDropped] = useState(0)
  const [error, setError] = useState<string | null>(null)

  async function makePlan() {
    if (intent.trim().length < 3 || busy) return
    setBusy(true); setError(null); setPlan(null)
    try {
      const res = await fetch('/api/ai/plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ intent: intent.trim(), mode: 'overcome' }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data?.recovery) { setError(data?.error || 'Could not plan that.'); return }
      setPlan(data.recovery)
      setDropped(Number(data.dropped) || 0)
    } catch {
      setError('Not sent — check your connection.')
    } finally {
      setBusy(false)
    }
  }

  if (plan) {
    return (
      <PlanSteps
        plan={plan}
        dropped={dropped}
        showHeader
        onNavigate={onClose}
        onCreated={() => router.refresh()}
        onDiscard={() => setPlan(null)}
      />
    )
  }

  return (
    <div className="space-y-3">
      <textarea value={intent} onChange={(e) => setIntent(e.target.value)}
        rows={3} autoFocus
        placeholder="e.g. I've been scrolling 3–4 hours every night and sleeping at 2am"
        className="log-input w-full resize-none text-sm"
        onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); makePlan() } }} />
      <p className="-mt-1 text-[10px] leading-relaxed text-muted-foreground">
        Say what you&apos;ve slipped into, or dropped. The AI proposes a small way out — a first
        move for today, a habit to fill the gap, maybe a short challenge — using what it knows
        about your habits, screen time and sleep. Nothing is added until you say so.
      </p>
      {error && <p className="text-xs text-red-400">{error}</p>}
      <button onClick={makePlan} disabled={intent.trim().length < 3 || busy}
        className="flex w-full items-center justify-center gap-1.5 rounded-xl bg-primary py-3 text-sm
                   font-semibold text-white transition-all hover:bg-teal-light active:scale-95 disabled:opacity-40">
        {busy
          ? <><Loader2 size={14} className="animate-spin" /> Working it out…</>
          : <><LifeBuoy size={14} /> Plan a way out <ChevronRight size={14} /></>}
      </button>
    </div>
  )
}
