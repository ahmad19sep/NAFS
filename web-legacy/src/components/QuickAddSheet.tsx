'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  X, ListChecks, Repeat, Flame, Trophy, MoonStar, HeartPulse, Smartphone,
  Check, Loader2, Sparkles, ChevronRight, type LucideIcon,
} from 'lucide-react'
import { createClient } from '@/lib/supabase/client'
import { cn, todayString, formatDate } from '@/lib/utils'
import { isHabitScheduledOn } from '@/lib/history'
import { describeProposal, proposalToCreateBody, type PlanProposal } from '@/lib/plan'

/** Opaque, unique per confirmation, so a retried confirm cannot create twice. */
function newRequestId(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `r-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
import { useBodyScrollLock } from '@/hooks/useBodyScrollLock'
import { useDeenEnabled } from '@/hooks/useDeenEnabled'
import OvercomePanel from '@/components/OvercomePanel'
import type { QuickAddIntent } from '@/lib/quick-add'

interface Props {
  open: boolean
  onClose: () => void
  /** Where to start when opened from a card or the coach; null for the + button. */
  initial?: QuickAddIntent | null
}

const CREATE_ACTIONS: { href: string; icon: LucideIcon; label: string }[] = [
  { href: '/tasks',      icon: ListChecks, label: 'New task' },
  { href: '/habits',     icon: Repeat,     label: 'New habit' },
  { href: '/challenges', icon: Flame,      label: 'New challenge' },
  { href: '/goals',      icon: Trophy,     label: 'New goal' },
  { href: '/deen',       icon: MoonStar,   label: 'Log prayer' },
  { href: '/health',     icon: HeartPulse, label: 'Log health' },
  { href: '/screentime', icon: Smartphone, label: 'Screen time' },
]

interface QuickHabit {
  id: string
  name: string
  emoji: string
  type: 'simple' | 'counter' | 'duration' | 'subject'
  target_value: number
  time_target_mins: number
  unit: string | null
  is_paused: boolean
  schedule_kind: string
  schedule_days: string[] | null
  done: boolean
}

export default function QuickAddSheet({ open, onClose, initial = null }: Props) {
  useBodyScrollLock(open)
  const deenEnabled = useDeenEnabled()
  const router = useRouter()
  const supabase = createClient()
  const today = todayString()

  // Log is the default: recording something you already do is the common case,
  // and the old sheet made you enter a creation flow to do it.
  const [mode, setMode] = useState<'log' | 'create' | 'plan'>('log')
  const [habits, setHabits] = useState<QuickHabit[] | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [justLogged, setJustLogged] = useState<{ id: string; name: string } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setHabits([]); return }

    const [{ data: rows }, { data: logs }] = await Promise.all([
      supabase.from('habits')
        .select('id, name, emoji, type, target_value, time_target_mins, unit, is_paused, schedule_kind, schedule_days, sort_order')
        .eq('user_id', user.id).eq('is_active', true).order('sort_order'),
      supabase.from('habit_logs')
        .select('habit_id, completed').eq('user_id', user.id).eq('date', today),
    ])

    const doneIds = new Set((logs ?? []).filter((l: any) => l.completed).map((l: any) => l.habit_id))
    setHabits((rows ?? []).map((h: any) => ({ ...h, done: doneIds.has(h.id) })))
  }, [supabase, today])

  useEffect(() => {
    if (!open) return
    setMode(initial?.mode ?? 'log'); setJustLogged(null); setError(null)
    setHabits(null)
    load()
  }, [open, load, initial])

  if (!open) return null

  const createActions = CREATE_ACTIONS.filter((a) => deenEnabled || a.href !== '/deen')

  // Only what can genuinely be finished in one tap. A counter or duration habit
  // needs a number, and offering a control that cannot record it honestly is
  // worse than sending the user to the page that can.
  const oneTap = (habits ?? []).filter(
    (h) => h.type === 'simple' && isHabitScheduledOn(h as any, today),
  )
  const needsDetail = (habits ?? []).filter(
    (h) => h.type !== 'simple' && isHabitScheduledOn(h as any, today),
  )

  async function setHabitDone(h: QuickHabit, done: boolean) {
    if (busy) return
    setBusy(h.id); setError(null)
    try {
      const res = await fetch('/api/habits/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        // An absolute state, not a flip: repeating it is harmless.
        body: JSON.stringify({ habitId: h.id, date: today, completed: done, value: done ? 1 : 0 }),
      })
      if (!res.ok) { setError('Could not save that. Try again.'); return }

      setHabits((prev) => (prev ?? []).map((x) => (x.id === h.id ? { ...x, done } : x)))
      setJustLogged(done ? { id: h.id, name: h.name } : null)
      router.refresh()   // dashboard, history and streaks read the same rows
    } catch {
      setError('Not saved — check your connection.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="modal-overlay items-end backdrop-blur-sm animate-in fade-in" onClick={onClose}>
      <div
        className="w-full max-w-md rounded-t-3xl border-t border-white/10
                   bg-gradient-to-b from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                   shadow-[0_-12px_40px_rgba(0,0,0,0.4)] animate-slide-up
                   pb-[max(env(safe-area-inset-bottom),1rem)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-center pt-2.5 pb-1">
          <div className="h-1 w-12 rounded-full bg-white/20" />
        </div>

        {/* Header — the date is shown, because what you log is dated and a
            sheet that hides it invites logging the wrong day. */}
        <div className="flex items-start justify-between px-5 pt-1 pb-3">
          <div>
            <p className="text-base font-bold text-foreground">
              {mode === 'log' ? 'Log something' : mode === 'create' ? 'Create' : 'Ask AI to plan it'}
            </p>
            <p className="text-[11px] text-muted-foreground mt-0.5">{formatDate(today)}</p>
          </div>
          <button onClick={onClose}
            className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors">
            <X size={16} className="text-muted-foreground" />
          </button>
        </div>

        {/* Mode switch */}
        <div className="px-5 pb-3">
          <div className="grid grid-cols-3 gap-1 rounded-xl border border-white/10 bg-white/5 p-1">
            {(['log', 'create', 'plan'] as const).map((m) => (
              <button key={m} onClick={() => setMode(m)}
                className={cn('rounded-lg py-2 text-xs font-semibold transition-all flex items-center justify-center gap-1',
                  mode === m ? 'bg-gold/15 text-gold' : 'text-muted-foreground hover:text-foreground',
                )}>
                {m === 'plan' && <Sparkles size={11} />}
                {m === 'log' ? 'Log' : m === 'create' ? 'Create' : 'Ask AI'}
              </button>
            ))}
          </div>
        </div>

        {mode === 'log' ? (
          <div className="px-5 pb-4">
            {habits === null && (
              <div className="flex items-center justify-center py-8 text-muted-foreground">
                <Loader2 size={18} className="animate-spin" />
              </div>
            )}

            {habits !== null && oneTap.length === 0 && needsDetail.length === 0 && (
              <div className="py-6 text-center">
                <p className="text-sm font-semibold text-foreground">Nothing scheduled today</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Habits you schedule for today appear here for one-tap logging.
                </p>
                <button onClick={() => setMode('create')}
                  className="btn-gold mt-4 px-5 py-2 text-xs">Create something</button>
              </div>
            )}

            {oneTap.length > 0 && (
              <div className="space-y-2">
                {oneTap.map((h) => (
                  <button key={h.id} onClick={() => setHabitDone(h, !h.done)} disabled={!!busy}
                    className={cn('flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-all active:scale-95 disabled:opacity-60',
                      h.done ? 'border-emerald-500/40 bg-emerald-500/10' : 'border-white/10 bg-white/5',
                    )}>
                    <span className="text-lg">{h.emoji}</span>
                    <span className={cn('flex-1 text-sm font-medium',
                      h.done ? 'text-emerald-300' : 'text-foreground')}>{h.name}</span>
                    {busy === h.id
                      ? <Loader2 size={15} className="animate-spin text-muted-foreground" />
                      : h.done
                        ? <Check size={16} className="text-emerald-400" />
                        : <span className="text-[10px] uppercase tracking-wider text-muted-foreground">tap</span>}
                  </button>
                ))}
              </div>
            )}

            {/* Undo, because the whole point of one tap is that it is easy to
                tap the wrong thing. */}
            {justLogged && (
              <div className="mt-3 flex items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-3 py-2.5">
                <p className="flex-1 min-w-0 truncate text-xs text-muted-foreground">
                  Logged <span className="text-foreground font-medium">{justLogged.name}</span>
                </p>
                <button
                  onClick={() => {
                    const h = (habits ?? []).find((x) => x.id === justLogged.id)
                    if (h) setHabitDone(h, false)
                  }}
                  className="shrink-0 rounded-lg border border-gold/40 bg-gold/10 px-3 py-1 text-[11px] font-semibold text-gold">
                  Undo
                </button>
              </div>
            )}

            {error && <p className="mt-3 text-xs text-red-400">{error}</p>}

            {needsDetail.length > 0 && (
              <div className="mt-4">
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground mb-2">
                  Needs a number
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {needsDetail.map((h) => (
                    <Link key={h.id} href="/habits" onClick={onClose}
                      className="flex items-center gap-1.5 rounded-full border border-white/10 bg-white/5 px-3 py-1.5
                                 text-xs text-foreground hover:border-gold/40 transition-all active:scale-95">
                      <span>{h.emoji}</span>{h.name}
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : mode === 'create' ? (
          <>
            <div className="px-5 grid grid-cols-3 gap-2.5">
              {createActions.map((a) => (
                <Link key={a.href} href={a.href} onClick={onClose}
                  className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 text-center
                             transition-all active:scale-95 hover:border-gold/30 hover:bg-white/[0.07]">
                  <div className="mx-auto h-10 w-10 rounded-xl border border-gold/20 bg-gold/[0.07]
                                  flex items-center justify-center mb-2">
                    <a.icon size={17} strokeWidth={2} className="text-gold" />
                  </div>
                  <p className="text-[11px] font-medium text-foreground leading-tight">{a.label}</p>
                </Link>
              ))}
            </div>
            <div className="px-5 mt-3 pb-3 text-center">
              <p className="text-[10px] text-muted-foreground">Tap a card to open its create flow</p>
            </div>
          </>
        ) : (
          <PlanPanel onClose={onClose} initialPlanMode={initial?.planMode} initialIntent={initial?.intent} />
        )}
      </div>
    </div>
  )
}

// ============================================================
// Ask AI — say what you want to do, see where it belongs, confirm
// ============================================================
//
// The model only ever PROPOSES. Nothing is created until the user taps "Add",
// and that goes through the same /api/tasks, /api/habits or /api/challenges
// route a manual entry uses. The proposal card shows the kind, the exact
// numbers, and the model's reason, so what gets added is never a surprise.
function PlanPanel({ onClose, initialPlanMode, initialIntent }: {
  onClose: () => void
  initialPlanMode?: 'plan' | 'overcome'
  initialIntent?: string
}) {
  const router = useRouter()
  // Two jobs: plan something you want to do, or plan a way out of something
  // you have slipped into. The second lives in OvercomePanel.
  const [planMode, setPlanMode] = useState<'plan' | 'overcome'>(initialPlanMode ?? 'plan')
  const [intent, setIntent] = useState(initialPlanMode === 'overcome' ? '' : (initialIntent ?? ''))
  const [busy, setBusy] = useState<'plan' | 'add' | null>(null)
  const [proposal, setProposal] = useState<PlanProposal | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [added, setAdded] = useState<{ label: string; href: string; title: string } | null>(null)
  // One id per proposal, so retrying "Add" after a dropped connection cannot
  // create the item twice. Replaced whenever a new proposal arrives.
  const requestId = useRef('')

  const KIND_STYLE: Record<PlanProposal['kind'], string> = {
    task: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300',
    habit: 'border-blue-400/40 bg-blue-500/10 text-blue-300',
    challenge: 'border-orange-400/40 bg-orange-500/10 text-orange-300',
  }

  async function plan() {
    if (intent.trim().length < 3 || busy) return
    setBusy('plan'); setError(null); setProposal(null); setAdded(null)
    try {
      const res = await fetch('/api/ai/plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ intent: intent.trim() }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) { setError(data?.error || 'Could not plan that.'); return }
      setProposal(data.proposal)
      requestId.current = newRequestId()
    } catch {
      setError('Not sent — check your connection.')
    } finally {
      setBusy(null)
    }
  }

  async function add() {
    if (!proposal || busy) return
    setBusy('add'); setError(null)
    const req = proposalToCreateBody(proposal, requestId.current)
    try {
      const res = await fetch(req.path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req.body),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) { setError(data?.error || 'Could not add it.'); return }
      setAdded({ label: req.label, href: req.href, title: proposal.title })
      setProposal(null)
      router.refresh()
    } catch {
      // Keep the request id: retrying is the same intent, not a second one.
      setError('Not added — check your connection and try again.')
    } finally {
      setBusy(null)
    }
  }

  function reset() {
    setProposal(null); setAdded(null); setError(null)
  }

  return (
    <div className="px-5 pb-4 space-y-3">
      <div className="grid grid-cols-2 gap-1 rounded-xl border border-white/10 bg-white/5 p-1">
        {([['plan', 'Do something'], ['overcome', 'Get out of something']] as const).map(([m, label]) => (
          <button key={m} onClick={() => setPlanMode(m)} disabled={!!busy}
            className={cn('rounded-lg py-1.5 text-[11px] font-semibold transition-all',
              planMode === m ? 'bg-gold/15 text-gold' : 'text-muted-foreground hover:text-foreground')}>
            {label}
          </button>
        ))}
      </div>

      {planMode === 'overcome' ? (
        <OvercomePanel onClose={onClose} initialIntent={initialIntent} />
      ) : added ? (
        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4">
          <p className="text-sm font-semibold text-emerald-300 flex items-center gap-1.5">
            <Check size={14} /> Added as a {added.label}
          </p>
          <p className="mt-1 text-sm text-foreground">{added.title}</p>
          <div className="mt-3 flex gap-2">
            <Link href={added.href} onClick={onClose}
              className="flex-1 rounded-lg bg-primary py-2 text-center text-xs font-semibold text-white
                         hover:bg-teal-light transition-all active:scale-95">
              Open {added.label}s
            </Link>
            <button onClick={() => { reset(); setIntent('') }}
              className="flex-1 rounded-lg border border-white/10 py-2 text-xs font-semibold text-muted-foreground
                         hover:text-foreground transition-all active:scale-95">
              Plan another
            </button>
          </div>
        </div>
      ) : proposal ? (
        <div className="rounded-xl border border-white/10 bg-white/5 p-4">
          <div className="flex items-center gap-2 mb-2">
            <span className={cn('rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider',
              KIND_STYLE[proposal.kind])}>
              {proposal.kind}
            </span>
            <span className="text-[11px] text-muted-foreground">{describeProposal(proposal)}</span>
          </div>
          <p className="text-base font-semibold text-foreground">
            <span className="mr-1.5">{proposal.emoji}</span>{proposal.title}
          </p>
          <p className="mt-1.5 text-xs text-muted-foreground leading-relaxed">{proposal.reason}</p>
          {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
          <div className="mt-3 flex gap-2">
            <button onClick={add} disabled={!!busy}
              className="flex-[2] rounded-lg bg-primary py-2.5 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all active:scale-95 disabled:opacity-50
                         flex items-center justify-center gap-1.5">
              {busy === 'add' ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
              Add as {proposal.kind}
            </button>
            <button onClick={reset} disabled={!!busy}
              className="flex-1 rounded-lg border border-white/10 py-2.5 text-sm text-muted-foreground
                         hover:text-foreground transition-all active:scale-95 disabled:opacity-50">
              Not this
            </button>
          </div>
        </div>
      ) : (
        <>
          <textarea value={intent} onChange={(e) => setIntent(e.target.value)}
            rows={3} autoFocus
            placeholder="e.g. work 12 hours a day for the next 30 days"
            className="log-input w-full resize-none text-sm"
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); plan() } }} />
          <p className="text-[10px] text-muted-foreground -mt-1">
            Say it however you like. The AI works out whether it&apos;s a one-off task, a daily habit,
            or a fixed challenge — and you confirm before anything is added.
          </p>
          {error && <p className="text-xs text-red-400">{error}</p>}
          <button onClick={plan} disabled={intent.trim().length < 3 || !!busy}
            className="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white
                       hover:bg-teal-light transition-all active:scale-95 disabled:opacity-40
                       flex items-center justify-center gap-1.5">
            {busy === 'plan'
              ? <><Loader2 size={14} className="animate-spin" /> Thinking…</>
              : <><Sparkles size={14} /> Plan it <ChevronRight size={14} /></>}
          </button>
        </>
      )}
    </div>
  )
}
