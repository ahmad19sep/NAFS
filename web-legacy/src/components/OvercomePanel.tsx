'use client'

import { useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Check, Loader2, LifeBuoy, ChevronRight, AlertCircle, Square, CheckSquare } from 'lucide-react'
import { cn } from '@/lib/utils'
import { describeProposal, proposalToCreateBody, type PlanProposal, type RecoveryPlan } from '@/lib/plan'

/**
 * "I've slipped into scrolling" → a small plan out of it.
 *
 * The model proposes one to four steps — a first move for today, a habit that
 * fills the gap, maybe a short challenge — grounded in what the app knows.
 * Every step is shown with its kind, its exact numbers and its reason; the
 * user unticks what they do not want and adds the rest. Each step is created
 * through the same route a manual entry uses, with its own request id, so a
 * retry after a dropped connection adds nothing twice and a failed step can
 * be retried alone.
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
  onClose: () => void
  initialIntent?: string
}

export default function OvercomePanel({ onClose, initialIntent }: Props) {
  const router = useRouter()
  const [intent, setIntent] = useState(initialIntent ?? '')
  const [busy, setBusy] = useState<'plan' | 'add' | null>(null)
  const [plan, setPlan] = useState<RecoveryPlan | null>(null)
  const [selected, setSelected] = useState<boolean[]>([])
  const [status, setStatus] = useState<StepStatus[]>([])
  const [dropped, setDropped] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  // One id per step, fixed when the plan arrives, so retrying a step is the
  // same intent rather than a second one.
  const requestIds = useRef<string[]>([])

  async function makePlan() {
    if (intent.trim().length < 3 || busy) return
    setBusy('plan'); setError(null); setPlan(null); setDone(false)
    try {
      const res = await fetch('/api/ai/plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ intent: intent.trim(), mode: 'overcome' }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data?.recovery) { setError(data?.error || 'Could not plan that.'); return }
      const r: RecoveryPlan = data.recovery
      setPlan(r)
      setSelected(r.steps.map(() => true))
      setStatus(r.steps.map(() => 'pending'))
      setDropped(Number(data.dropped) || 0)
      requestIds.current = r.steps.map(() => newRequestId())
    } catch {
      setError('Not sent — check your connection.')
    } finally {
      setBusy(null)
    }
  }

  async function addSelected() {
    if (!plan || busy) return
    setBusy('add'); setError(null)
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
    setBusy(null)
    router.refresh()   // tasks, habits, challenges and Home read the same rows
    const allIn = next.every((s, i) => !selected[i] || s === 'added')
    if (allIn) setDone(true)
    else setError('Some steps were not added. Tap Add again to retry just those.')
  }

  function reset() {
    setPlan(null); setDone(false); setError(null); setDropped(0)
  }

  const toAdd = plan ? plan.steps.filter((_, i) => selected[i] && status[i] !== 'added').length : 0
  const addedKinds = plan
    ? Array.from(new Set(plan.steps.filter((_, i) => status[i] === 'added').map((s) => s.kind)))
    : []

  // ── Done ────────────────────────────────────────────────────────────────
  if (done && plan) {
    return (
      <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4">
        <p className="text-sm font-semibold text-emerald-300 flex items-center gap-1.5">
          <Check size={14} /> Plan in place
        </p>
        <p className="mt-1 text-xs text-muted-foreground">{plan.problem}</p>
        <ul className="mt-2 space-y-1">
          {plan.steps.map((s, i) => status[i] === 'added' && (
            <li key={i} className="text-sm text-foreground"><span className="mr-1.5">{s.emoji}</span>{s.title}</li>
          ))}
        </ul>
        <div className="mt-3 flex flex-wrap gap-2">
          {addedKinds.map((k) => (
            <Link key={k} href={OPEN_LINKS[k].href} onClick={onClose}
              className="rounded-lg bg-primary px-3 py-2 text-xs font-semibold text-white
                         hover:bg-teal-light transition-all active:scale-95">
              Open {OPEN_LINKS[k].label}
            </Link>
          ))}
          <button onClick={() => { reset(); setIntent('') }}
            className="rounded-lg border border-white/10 px-3 py-2 text-xs font-semibold text-muted-foreground
                       hover:text-foreground transition-all active:scale-95">
            Another
          </button>
        </div>
      </div>
    )
  }

  // ── The plan, step by step ──────────────────────────────────────────────
  if (plan) {
    return (
      <div className="space-y-3">
        <div className="rounded-xl border border-white/10 bg-white/5 p-4">
          <p className="text-[10px] uppercase tracking-wider text-muted-foreground">The slip</p>
          <p className="mt-0.5 text-sm font-semibold text-foreground">{plan.problem}</p>
          <p className="mt-2 text-xs text-muted-foreground leading-relaxed">{plan.approach}</p>
        </div>

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
                  <span className="mt-0.5 shrink-0 text-muted-foreground">
                    {st === 'added' ? <Check size={16} className="text-emerald-400" />
                      : st === 'adding' ? <Loader2 size={16} className="animate-spin text-gold" />
                      : st === 'failed' ? <AlertCircle size={16} className="text-red-400" />
                      : on ? <CheckSquare size={16} className="text-gold" /> : <Square size={16} />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={cn('rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider',
                        KIND_STYLE[s.kind])}>
                        {s.kind}
                      </span>
                      <span className="text-[11px] text-muted-foreground truncate">{describeProposal(s)}</span>
                    </div>
                    <p className="text-sm font-semibold text-foreground">
                      <span className="mr-1.5">{s.emoji}</span>{s.title}
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground leading-relaxed">{s.reason}</p>
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
          <button onClick={addSelected} disabled={!!busy || toAdd === 0}
            className="flex-[2] rounded-lg bg-primary py-2.5 text-sm font-semibold text-white
                       hover:bg-teal-light transition-all active:scale-95 disabled:opacity-50
                       flex items-center justify-center gap-1.5">
            {busy === 'add' ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
            Add {toAdd} step{toAdd === 1 ? '' : 's'}
          </button>
          <button onClick={reset} disabled={!!busy}
            className="flex-1 rounded-lg border border-white/10 py-2.5 text-sm text-muted-foreground
                       hover:text-foreground transition-all active:scale-95 disabled:opacity-50">
            Not this
          </button>
        </div>
      </div>
    )
  }

  // ── Say what you have slipped into ──────────────────────────────────────
  return (
    <div className="space-y-3">
      <textarea value={intent} onChange={(e) => setIntent(e.target.value)}
        rows={3} autoFocus
        placeholder="e.g. I've been scrolling 3–4 hours every night and sleeping at 2am"
        className="log-input w-full resize-none text-sm"
        onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); makePlan() } }} />
      <p className="text-[10px] text-muted-foreground -mt-1 leading-relaxed">
        Say what you&apos;ve slipped into, or dropped. The AI proposes a small way out — a first
        move for today, a habit to fill the gap, maybe a short challenge — using what it knows
        about your habits, screen time and sleep. Nothing is added until you say so.
      </p>
      {error && <p className="text-xs text-red-400">{error}</p>}
      <button onClick={makePlan} disabled={intent.trim().length < 3 || !!busy}
        className="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white
                   hover:bg-teal-light transition-all active:scale-95 disabled:opacity-40
                   flex items-center justify-center gap-1.5">
        {busy === 'plan'
          ? <><Loader2 size={14} className="animate-spin" /> Working it out…</>
          : <><LifeBuoy size={14} /> Plan a way out <ChevronRight size={14} /></>}
      </button>
    </div>
  )
}
