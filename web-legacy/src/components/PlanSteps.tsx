'use client'

import { useRef, useState } from 'react'
import Link from 'next/link'
import { Check, Loader2, AlertCircle, Square, CheckSquare } from 'lucide-react'
import { cn } from '@/lib/utils'
import { describeProposal, proposalToCreateBody, type PlanProposal, type RecoveryPlan } from '@/lib/plan'

/**
 * A proposed plan, step by step, with the user deciding what becomes real.
 *
 * Shared by the "get out of something" sheet and by the coach chat, so a plan
 * looks and behaves the same wherever it was proposed. Every step shows its
 * kind, its exact numbers and the reason it was suggested; the user unticks
 * what they do not want and adds the rest.
 *
 * Each step is created through the same route a manual entry uses and carries
 * its own request id, so a retry after a dropped connection adds nothing twice
 * and one failed step can be retried without duplicating the others.
 */

function newRequestId(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `r-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

const KIND_STYLE: Record<PlanProposal['kind'], string> = {
  task: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300',
  habit: 'border-blue-400/40 bg-blue-500/10 text-blue-300',
  challenge: 'border-orange-400/40 bg-orange-500/10 text-orange-300',
}

const OPEN_LINKS: Record<PlanProposal['kind'], { href: string; label: string }> = {
  task: { href: '/tasks', label: 'tasks' },
  habit: { href: '/habits', label: 'habits' },
  challenge: { href: '/challenges', label: 'challenges' },
}

type StepStatus = 'pending' | 'adding' | 'added' | 'failed'

interface Props {
  plan: RecoveryPlan
  /** Steps the model proposed that could not be created as described. */
  dropped?: number
  /** Show the restated problem and the approach above the steps. */
  showHeader?: boolean
  /** Called after a link is followed, so a sheet can close itself. */
  onNavigate?: () => void
  /** Called when the user rejects the whole plan. Omit to hide the button. */
  onDiscard?: () => void
  /** Refresh the page's data after something is created. */
  onCreated?: () => void
  discardLabel?: string
}

export default function PlanSteps({
  plan, dropped = 0, showHeader = true, onNavigate, onDiscard, onCreated,
  discardLabel = 'Not this',
}: Props) {
  const [selected, setSelected] = useState<boolean[]>(() => plan.steps.map(() => true))
  const [status, setStatus] = useState<StepStatus[]>(() => plan.steps.map(() => 'pending'))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  // Fixed when the plan arrives, so retrying a step is the same intent
  // rather than a second one.
  const requestIds = useRef<string[]>(plan.steps.map(() => newRequestId()))

  async function addSelected() {
    if (busy) return
    setBusy(true); setError(null)
    const next = [...status]
    for (let i = 0; i < plan.steps.length; i++) {
      if (!selected[i] || next[i] === 'added') continue
      next[i] = 'adding'; setStatus([...next])
      const req = proposalToCreateBody(plan.steps[i], requestIds.current[i])
      try {
        const res = await fetch(req.path, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(req.body),
        })
        next[i] = res.ok ? 'added' : 'failed'
      } catch {
        next[i] = 'failed'
      }
      setStatus([...next])
    }
    setBusy(false)
    onCreated?.()
    if (next.every((s, i) => !selected[i] || s === 'added')) setDone(true)
    else setError('Some steps were not added. Tap Add again to retry just those.')
  }

  const toAdd = plan.steps.filter((_, i) => selected[i] && status[i] !== 'added').length
  const addedKinds = Array.from(new Set(
    plan.steps.filter((_, i) => status[i] === 'added').map((s) => s.kind)))

  if (done) {
    return (
      <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4">
        <p className="flex items-center gap-1.5 text-sm font-semibold text-emerald-300">
          <Check size={14} /> Plan in place
        </p>
        {plan.problem && <p className="mt-1 text-xs text-muted-foreground">{plan.problem}</p>}
        <ul className="mt-2 space-y-1">
          {plan.steps.map((s, i) => status[i] === 'added' && (
            <li key={i} className="text-sm text-foreground"><span className="mr-1.5">{s.emoji}</span>{s.title}</li>
          ))}
        </ul>
        <div className="mt-3 flex flex-wrap gap-2">
          {addedKinds.map((k) => (
            <Link key={k} href={OPEN_LINKS[k].href} onClick={onNavigate}
              className="rounded-lg bg-primary px-3 py-2 text-xs font-semibold text-white transition-all hover:bg-teal-light active:scale-95">
              Open {OPEN_LINKS[k].label}
            </Link>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {showHeader && (plan.problem || plan.approach) && (
        <div className="rounded-xl border border-white/10 bg-white/5 p-4">
          {plan.problem && (
            <>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground">The problem</p>
              <p className="mt-0.5 text-sm font-semibold text-foreground">{plan.problem}</p>
            </>
          )}
          {plan.approach && (
            <p className="mt-2 text-xs leading-relaxed text-muted-foreground">{plan.approach}</p>
          )}
        </div>
      )}

      <div className="space-y-2">
        {plan.steps.map((s, i) => {
          const st = status[i]
          const on = selected[i]
          return (
            <button key={i} type="button"
              onClick={() => { if (st !== 'added' && !busy) setSelected((p) => p.map((v, j) => (j === i ? !v : v))) }}
              className={cn('w-full rounded-xl border p-3 text-left transition-all',
                st === 'added' ? 'border-emerald-500/40 bg-emerald-500/10'
                  : on ? 'border-white/15 bg-white/[0.06]' : 'border-white/10 bg-white/[0.02] opacity-60',
              )}>
              <div className="flex items-start gap-2.5">
                <span className="mt-0.5 flex-shrink-0 text-muted-foreground">
                  {st === 'added' ? <Check size={16} className="text-emerald-400" />
                    : st === 'adding' ? <Loader2 size={16} className="animate-spin text-gold" />
                    : st === 'failed' ? <AlertCircle size={16} className="text-red-400" />
                    : on ? <CheckSquare size={16} className="text-gold" /> : <Square size={16} />}
                </span>
                <div className="min-w-0 flex-1">
                  <div className="mb-1 flex items-center gap-2">
                    <span className={cn('rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider',
                      KIND_STYLE[s.kind])}>
                      {s.kind}
                    </span>
                    <span className="truncate text-[11px] text-muted-foreground">{describeProposal(s)}</span>
                  </div>
                  <p className="text-sm font-semibold text-foreground">
                    <span className="mr-1.5">{s.emoji}</span>{s.title}
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{s.reason}</p>
                  {st === 'failed' && <p className="mt-1 text-[11px] text-red-400">Not added — will retry.</p>}
                </div>
              </div>
            </button>
          )
        })}
      </div>

      {dropped > 0 && (
        <p className="text-[11px] text-muted-foreground">
          {dropped} suggested step{dropped === 1 ? '' : 's'} left out because {dropped === 1 ? 'it' : 'they'} could not be created as described.
        </p>
      )}
      {error && <p className="text-xs text-red-400">{error}</p>}

      <div className="flex gap-2">
        <button onClick={addSelected} disabled={busy || toAdd === 0}
          className="flex flex-[2] items-center justify-center gap-1.5 rounded-lg bg-primary py-2.5 text-sm
                     font-semibold text-white transition-all hover:bg-teal-light active:scale-95 disabled:opacity-50">
          {busy ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
          Add {toAdd} step{toAdd === 1 ? '' : 's'}
        </button>
        {onDiscard && (
          <button onClick={onDiscard} disabled={busy}
            className="flex-1 rounded-lg border border-white/10 py-2.5 text-sm text-muted-foreground
                       transition-all hover:text-foreground active:scale-95 disabled:opacity-50">
            {discardLabel}
          </button>
        )}
      </div>
    </div>
  )
}
