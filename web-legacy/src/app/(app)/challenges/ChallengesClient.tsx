'use client'

import { useState, useRef, useMemo, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Plus, Camera, Trash2, Sparkles, Quote, RefreshCw, X } from 'lucide-react'
import { cn, todayString } from '@/lib/utils'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import { computeChallengesHistory } from '@/lib/history'
import {
  type ChallengeFrequency, FREQUENCY,
  totalUnits, unitsElapsed, hasCheckedThisPeriod,
  periodStreak,
} from '@/lib/challenges'

interface ChallengeAiStarter {
  why_this_works: string
  hardest_obstacle: string
  daily_anchor: string
  supporting_habits: Array<{
    name: string; emoji: string
    type: 'simple' | 'counter' | 'duration'
    target_value?: number; unit?: string; time_target_mins?: number
  }>
  generated_at: string
}

interface Challenge {
  id: string
  title: string
  emoji: string
  description: string                  // = "reason / why"
  duration_days: number
  start_date: string
  frequency: ChallengeFrequency
  requires_photo: boolean
  sadqa_amount: number | null
  sadqa_currency: string
  current_streak: number
  longest_streak: number
  restart_count: number
  last_restart_at: string | null
  status: 'active' | 'completed' | 'failed'
  ai_starter_pack: ChallengeAiStarter | null
  challenge_checkins: {
    date: string
    completed: boolean
    photo_url: string | null
    sadqa_paid: boolean
  }[]
}

interface Props {
  challenges: Challenge[]
  userId: string
}

const FREQ_KEYS: ChallengeFrequency[] = ['daily', 'weekly', 'monthly', 'yearly']
const FREQ_TONE: Record<ChallengeFrequency, { card: string; chip: string; ring: string }> = {
  daily:   { card: 'from-emerald-500/10 via-white/3 to-transparent border-emerald-400/20', chip: 'bg-emerald-500/15 text-emerald-300 border-emerald-400/30', ring: 'ring-emerald-400/30' },
  weekly:  { card: 'from-cyan-500/10 via-white/3 to-transparent border-cyan-400/20',       chip: 'bg-cyan-500/15 text-cyan-300 border-cyan-400/30',         ring: 'ring-cyan-400/30' },
  monthly: { card: 'from-violet-500/10 via-white/3 to-transparent border-violet-400/20',   chip: 'bg-violet-500/15 text-violet-300 border-violet-400/30',   ring: 'ring-violet-400/30' },
  yearly:  { card: 'from-gold/10 via-white/3 to-transparent border-gold/25',                chip: 'bg-gold/15 text-gold border-gold/30',                       ring: 'ring-gold/30' },
}

// ============================================================
// Page
// ============================================================
export default function ChallengesClient({ challenges, userId }: Props) {
  const router = useRouter()
  const supabase = createClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const today = todayString()

  // Create form state
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('🎯')
  const [reason, setReason] = useState('')
  const [frequency, setFrequency] = useState<ChallengeFrequency>('daily')
  const [duration, setDuration] = useState(30)
  const [requiresPhoto, setRequiresPhoto] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [checkingIn, setCheckingIn] = useState<string | null>(null)
  const [photoForChallenge, setPhotoForChallenge] = useState<string | null>(null)
  const [uploadingPhoto, setUploadingPhoto] = useState(false)

  const active = challenges.filter((c) => c.status === 'active')
  const finished = challenges.filter((c) => c.status === 'completed')

  // ---- auto-complete challenges past their deadline ----
  useEffect(() => {
    async function autoComplete() {
      let touched = false
      for (const c of active) {
        const elapsed = unitsElapsed(c.frequency, c.start_date, today)
        const total = totalUnits(c.frequency, c.duration_days)
        if (elapsed >= total) {
          await supabase.from('challenges').update({ status: 'completed' }).eq('id', c.id)
          touched = true
        }
      }
      if (touched) router.refresh()
    }
    if (active.length > 0) autoComplete()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ---- history teaser ----
  const history = useMemo(() => {
    const all = challenges.flatMap((c) =>
      (c.challenge_checkins ?? []).map((ci: any) => ({
        challenge_id: c.id, date: ci.date, completed: ci.completed,
      }))
    )
    return computeChallengesHistory(challenges as any, all, today)
  }, [challenges, today])

  // Default duration when frequency changes
  function selectFrequency(f: ChallengeFrequency) {
    setFrequency(f)
    if (f === 'daily')   setDuration(30)
    if (f === 'weekly')  setDuration(84)   // 12 weeks
    if (f === 'monthly') setDuration(180)  // 6 months
    if (f === 'yearly')  setDuration(365)  // 1 year
  }

  async function createChallenge() {
    if (!title.trim()) { setCreateError('Title required'); return }
    if (!reason.trim()) { setCreateError('Please write your reason'); return }
    setCreating(true); setCreateError(null)

    const { data: created, error } = await supabase.from('challenges').insert({
      user_id: userId,
      title: title.trim(),
      emoji,
      description: reason.trim(),
      duration_days: duration,
      start_date: today,
      frequency,
      requires_photo: requiresPhoto,
      sadqa_amount: null,
      sadqa_currency: 'PKR',
      current_streak: 0,
      longest_streak: 0,
      restart_count: 0,
      status: 'active',
    }).select().single()
    setCreating(false)
    if (error) { setCreateError(error.message); return }

    // Fire AI starter pack in background
    if (created?.id) {
      fetch('/api/ai/challenge-starter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeId: created.id }),
      }).then(() => router.refresh()).catch(() => {})
    }

    setShowCreate(false)
    setTitle(''); setReason(''); setEmoji('🎯'); setRequiresPhoto(false)
    setFrequency('daily'); setDuration(30)
    router.refresh()
  }

  async function dismissChallengeStarter(id: string) {
    await supabase.from('challenges').update({ ai_starter_pack: null }).eq('id', id)
    router.refresh()
  }

  const [generatingAiFor, setGeneratingAiFor] = useState<string | null>(null)
  async function generateChallengeStarter(id: string) {
    setGeneratingAiFor(id)
    try {
      const res = await fetch('/api/ai/challenge-starter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeId: id }),
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        alert(b?.error || 'AI tips failed — try again')
      } else {
        router.refresh()
      }
    } catch (e: any) {
      alert(e?.message || 'Network error')
    }
    setGeneratingAiFor(null)
  }

  async function addSupportingHabit(h: any) {
    await fetch('/api/habits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId,
        name: h.name,
        emoji: h.emoji || '⭐',
        type: h.type,
        target_value: h.target_value ?? 1,
        unit: h.unit ?? '',
        time_target_mins: h.time_target_mins ?? 0,
        category: 'custom',
        score_weight: 2,
        schedule_kind: 'daily',
      }),
    })
    router.refresh()
  }

  async function handlePhotoUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file || !checkingIn) return
    setUploadingPhoto(true)
    const ext = file.name.split('.').pop()
    const path = `${userId}/challenges/${checkingIn}-${today}.${ext}`
    const { error } = await supabase.storage.from('challenge-photos').upload(path, file, { upsert: true })
    if (!error) {
      const { data } = supabase.storage.from('challenge-photos').getPublicUrl(path)
      setPhotoForChallenge(data.publicUrl)
    }
    setUploadingPhoto(false)
  }

  async function checkIn(challenge: Challenge, completed: boolean) {
    if (completed && challenge.requires_photo && !photoForChallenge) {
      setCheckingIn(challenge.id)
      fileRef.current?.click()
      return
    }

    // Insert/upsert today's checkin (success OR miss)
    await supabase.from('challenge_checkins').upsert({
      challenge_id: challenge.id,
      date: today,
      completed,
      photo_url: completed ? photoForChallenge : null,
    }, { onConflict: 'challenge_id,date' })

    // Recompute streak from history. Missed today → streak resets to 0
    // (because periodStreak walks back from today and stops at first non-completed).
    // Challenge itself keeps running until its deadline.
    const updatedCheckins = [
      ...(challenge.challenge_checkins ?? []).filter((c) => c.date !== today),
      { date: today, completed, photo_url: completed ? photoForChallenge : null, sadqa_paid: false },
    ]
    const newStreak = periodStreak(challenge.frequency, challenge.start_date, updatedCheckins, today)

    // Auto-complete if we've reached the deadline
    const elapsed = unitsElapsed(challenge.frequency, challenge.start_date, today)
    const totalU = totalUnits(challenge.frequency, challenge.duration_days)
    const isComplete = elapsed >= totalU

    await supabase.from('challenges').update({
      current_streak: newStreak,
      longest_streak: Math.max(challenge.longest_streak, newStreak),
      ...(isComplete ? { status: 'completed' } : {}),
    }).eq('id', challenge.id)

    setPhotoForChallenge(null)
    setCheckingIn(null)
    router.refresh()
  }

  async function deleteChallenge(id: string) {
    if (!confirm('Delete this challenge?')) return
    await fetch(`/api/challenges/${id}`, { method: 'DELETE' })
    router.refresh()
  }

  // Today's progress (for header)
  const todayDone = active.filter((c) => hasCheckedThisPeriod(c.frequency, c.challenge_checkins ?? [], today)).length
  const pct = active.length > 0 ? Math.round((todayDone / active.length) * 100) : 0

  return (
    <div className="mx-auto max-w-md px-4 space-y-4 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <div className="flex items-baseline justify-between mt-0.5">
          <h1 className="text-2xl font-bold text-foreground">Challenges</h1>
          <button onClick={() => setShowCreate(!showCreate)}
            className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3.5 py-2 text-sm font-semibold
                       text-foreground hover:bg-white/10 hover:border-gold/40 transition-all active:scale-95">
            <Plus size={14} className="text-gold" /> New
          </button>
        </div>
        <p className="text-sm text-muted-foreground mt-0.5">
          {active.length} active · {todayDone} on track this period
        </p>
      </div>

      {/* Aggregate progress */}
      <div className="nafs-card p-4">
        <div className="flex items-center justify-between mb-2">
          <p className="text-sm font-semibold text-foreground">Today's check-in progress</p>
          <span className={cn('text-xl font-bold tabular-nums',
            pct === 100 && active.length > 0 ? 'text-emerald-400' : pct >= 50 ? 'text-gold' : 'text-muted-foreground'
          )}>{pct}%</span>
        </div>
        <div className="h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
          <div className={cn('h-full rounded-full transition-all',
            pct === 100 && active.length > 0 ? 'bg-emerald-400' : 'bg-gold'
          )} style={{ width: `${pct}%` }} />
        </div>
      </div>

      {/* History teaser */}
      {challenges.length > 0 && (
        <HistoryTeaserCard
          days={history}
          title="Challenges history"
          href="/history?tab=challenges"
          emoji="🎯"
          accent="pink"
        />
      )}

      {/* Hidden file input */}
      <input ref={fileRef} type="file" accept="image/*" capture="environment"
        onChange={handlePhotoUpload} className="hidden" />

      {/* Create form */}
      {showCreate && (
        <div className="nafs-card p-5 space-y-4 animate-slide-up">
          <p className="font-semibold text-foreground">New challenge</p>

          {/* Emoji + Title */}
          <div className="flex gap-3">
            <input value={emoji} onChange={(e) => setEmoji(e.target.value)}
              className="log-input w-16 text-center text-2xl" maxLength={2} />
            <input value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Pray Tahajjud, No social media…"
              className="log-input flex-1" />
          </div>

          {/* Reason — REQUIRED, prominent */}
          <div>
            <label className="section-header mb-1.5 block flex items-center gap-1">
              <Sparkles size={11} className="text-gold" /> Why this challenge? (required)
            </label>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder="Tell yourself why. You'll see this on every check-in."
              rows={3} className="log-input resize-none text-sm" />
            <p className="text-[10px] text-muted-foreground mt-1">A clear reason makes you 3× more likely to follow through.</p>
          </div>

          {/* Frequency */}
          <div>
            <label className="section-header mb-2 block">Frequency</label>
            <div className="grid grid-cols-4 gap-2">
              {FREQ_KEYS.map((f) => (
                <button key={f} onClick={() => selectFrequency(f)}
                  className={cn('rounded-xl border py-2.5 text-xs font-semibold transition-all flex flex-col items-center gap-0.5',
                    frequency === f
                      ? 'border-gold/50 bg-gold/10 text-gold'
                      : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                  )}>
                  <span className="text-base">{FREQUENCY[f].emoji}</span>
                  <span>{FREQUENCY[f].label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Duration */}
          <div>
            <label className="section-header mb-2 block">
              Duration: <span className="text-foreground tabular-nums">{totalUnits(frequency, duration)} {FREQUENCY[frequency].unitLabel}{totalUnits(frequency, duration) !== 1 ? 's' : ''}</span>
            </label>
            <input type="range"
              min={frequency === 'yearly' ? 365 : frequency === 'monthly' ? 30 : frequency === 'weekly' ? 7 : 7}
              max={frequency === 'yearly' ? 365 * 5 : frequency === 'monthly' ? 365 : frequency === 'weekly' ? 365 : 365}
              step={frequency === 'yearly' ? 365 : frequency === 'monthly' ? 30 : frequency === 'weekly' ? 7 : 1}
              value={duration}
              onChange={(e) => setDuration(Number(e.target.value))} className="w-full" />
          </div>

          {/* Photo proof */}
          <button onClick={() => setRequiresPhoto(!requiresPhoto)}
            className={cn('w-full rounded-xl border p-3 text-left flex items-center gap-3 transition-all',
              requiresPhoto ? 'border-gold/50 bg-gold/10 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground'
            )}>
            <Camera size={18} />
            <div className="flex-1">
              <p className="font-semibold text-sm">Photo proof each time</p>
              <p className="text-[10px] opacity-70">Forces honesty. You can't fake a check-in.</p>
            </div>
            <div className={cn('h-5 w-9 rounded-full transition-all',
              requiresPhoto ? 'bg-gold' : 'bg-white/15'
            )}>
              <div className={cn('h-5 w-5 rounded-full bg-white transition-transform',
                requiresPhoto && 'translate-x-4'
              )} />
            </div>
          </button>

          {createError && (
            <p className="text-xs text-red-400">{createError}</p>
          )}

          <div className="flex gap-2">
            <button onClick={() => { setShowCreate(false); setCreateError(null) }}
              className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={createChallenge} disabled={!title.trim() || !reason.trim() || creating}
              className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
              {creating ? 'Starting…' : 'Start challenge'}
            </button>
          </div>
        </div>
      )}

      {/* Empty state */}
      {active.length === 0 && !showCreate && (
        <div className="text-center py-14">
          <p className="text-5xl">🎯</p>
          <p className="mt-3 font-semibold text-foreground">No active challenges</p>
          <p className="mt-1 text-sm text-muted-foreground">Pick something hard. Skip a period → it auto-restarts.</p>
        </div>
      )}

      {/* Active cards */}
      <div className="space-y-3">
        {active.map((c) => (
          <ChallengeCard key={c.id} challenge={c} today={today}
            checkingIn={checkingIn === c.id}
            photoForChallenge={photoForChallenge}
            uploadingPhoto={uploadingPhoto}
            onCheckIn={(done) => checkIn(c, done)}
            onDelete={() => deleteChallenge(c.id)}
            onCapturePhoto={() => { setCheckingIn(c.id); fileRef.current?.click() }}
            onDismissAi={() => dismissChallengeStarter(c.id)}
            onGenerateAi={() => generateChallengeStarter(c.id)}
            generatingAi={generatingAiFor === c.id}
            onAddSupportingHabit={addSupportingHabit}
          />
        ))}
      </div>

      {/* Completed */}
      {finished.length > 0 && (
        <div className="space-y-2 pb-4">
          <p className="section-header">🏆 Completed</p>
          {finished.map((c) => (
            <div key={c.id} className="nafs-card p-4 flex items-center gap-3 opacity-80">
              <span className="text-2xl">{c.emoji}</span>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">{c.title}</p>
                <p className="text-xs text-muted-foreground">
                  {totalUnits(c.frequency, c.duration_days)} {FREQUENCY[c.frequency].unitLabel}s ·
                  best streak {c.longest_streak}
                </p>
              </div>
              <span className="text-xl">🏆</span>
              <button onClick={() => deleteChallenge(c.id)}
                className="h-8 w-8 rounded-xl flex items-center justify-center text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-colors">
                <Trash2 size={12} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ============================================================
// Challenge card
// ============================================================
function ChallengeCard({
  challenge: c, today, checkingIn, photoForChallenge, uploadingPhoto,
  onCheckIn, onDelete, onCapturePhoto, onDismissAi, onGenerateAi, generatingAi, onAddSupportingHabit,
}: {
  challenge: Challenge
  today: string
  checkingIn: boolean
  photoForChallenge: string | null
  uploadingPhoto: boolean
  onCheckIn: (done: boolean) => void
  onDelete: () => void
  onCapturePhoto: () => void
  onDismissAi: () => void
  onGenerateAi: () => void
  generatingAi: boolean
  onAddSupportingHabit: (h: any) => void
}) {
  const tone = FREQ_TONE[c.frequency]
  const meta = FREQUENCY[c.frequency]
  const elapsed = unitsElapsed(c.frequency, c.start_date, today)
  const total = totalUnits(c.frequency, c.duration_days)
  const pct = Math.min(100, Math.round((elapsed / total) * 100))
  const periodChecked = hasCheckedThisPeriod(c.frequency, c.challenge_checkins ?? [], today)
  const completedDays = (c.challenge_checkins ?? []).filter((ck) => ck.completed).length
  const completionRate = total > 0 ? Math.round((completedDays / total) * 100) : 0

  return (
    <div className={cn(
      'relative overflow-hidden rounded-2xl border bg-gradient-to-br p-5 space-y-4',
      tone.card
    )}>
      <div className="pointer-events-none absolute -right-6 -top-6 h-24 w-24 rounded-full bg-white/3 blur-2xl" />

      {/* Header */}
      <div className="relative flex items-start gap-3">
        <div className={cn('h-11 w-11 rounded-xl flex items-center justify-center text-2xl flex-shrink-0 ring-1', tone.chip, tone.ring)}>
          {c.emoji}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider', tone.chip)}>
              {meta.emoji} {meta.label}
            </span>
          </div>
          <p className="font-bold text-foreground leading-tight">{c.title}</p>
        </div>
        <div className="flex flex-col items-end gap-1 flex-shrink-0">
          {c.current_streak > 0 ? (
            <p className="text-base font-bold text-orange-400">🔥 {c.current_streak}</p>
          ) : null}
          <button onClick={onDelete}
            className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors">
            <Trash2 size={11} />
          </button>
        </div>
      </div>

      {/* Reason quote */}
      {c.description && (
        <div className="relative flex gap-2 text-xs text-muted-foreground italic border-l-2 border-gold/40 pl-3 py-0.5">
          <Quote size={11} className="text-gold/60 flex-shrink-0 mt-0.5" />
          <p className="leading-snug">{c.description}</p>
        </div>
      )}

      {/* Manual AI tips trigger when none yet */}
      {!c.ai_starter_pack && (
        <button onClick={(e) => { e.stopPropagation(); onGenerateAi() }} disabled={generatingAi}
          className="relative w-full flex items-center justify-center gap-1.5 rounded-lg border border-cyan-400/30
                     bg-cyan-500/8 py-1.5 text-[11px] font-semibold text-cyan-300
                     hover:bg-cyan-500/15 transition-all disabled:opacity-50">
          {generatingAi ? <RefreshCw size={11} className="animate-spin" /> : <Sparkles size={11} />}
          {generatingAi ? 'Asking AI…' : 'Get AI tips'}
        </button>
      )}

      {/* AI starter pack */}
      {c.ai_starter_pack && (
        <div className="relative rounded-xl border border-cyan-400/30 bg-gradient-to-br from-cyan-500/10 via-blue-700/5 to-transparent p-3 space-y-2">
          <div className="flex items-start gap-2">
            <Sparkles size={12} className="text-cyan-400 mt-0.5 flex-shrink-0" />
            <div className="flex-1 min-w-0 space-y-1.5">
              {c.ai_starter_pack.why_this_works && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-emerald-300 font-semibold">✓ </span>
                  {c.ai_starter_pack.why_this_works}
                </p>
              )}
              {c.ai_starter_pack.hardest_obstacle && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-orange-300 font-semibold">⚠ </span>
                  {c.ai_starter_pack.hardest_obstacle}
                </p>
              )}
              {c.ai_starter_pack.daily_anchor && (
                <p className="text-[11px] text-foreground leading-snug">
                  <span className="text-gold font-semibold">⚓ </span>
                  Anchor: {c.ai_starter_pack.daily_anchor}
                </p>
              )}
            </div>
            <button onClick={(e) => { e.stopPropagation(); onDismissAi() }}
              className="h-9 w-9 rounded-lg border border-white/10 bg-white/5 hover:bg-red-500/10 hover:text-red-400 flex items-center justify-center text-muted-foreground flex-shrink-0"
              aria-label="Close AI tips">
              <X size={16} />
            </button>
          </div>

          {(c.ai_starter_pack.supporting_habits?.length ?? 0) > 0 && (
            <div className="space-y-1 pt-1 border-t border-white/5">
              <p className="text-[10px] uppercase tracking-wider text-cyan-300">Supporting habits</p>
              {c.ai_starter_pack.supporting_habits!.map((h, i) => (
                <div key={i} className="rounded-lg border border-white/10 bg-white/5 p-2 flex items-center gap-2">
                  <span className="text-base">{h.emoji || '⭐'}</span>
                  <div className="flex-1 min-w-0">
                    <p className="text-[11px] font-semibold text-foreground truncate">{h.name}</p>
                    <p className="text-[9px] text-muted-foreground">
                      {h.type === 'simple' ? 'Yes/No · daily'
                        : h.type === 'counter' ? `${h.target_value ?? '?'} ${h.unit ?? ''} · daily`
                        : `${h.time_target_mins ?? '?'} min · daily`}
                    </p>
                  </div>
                  <button onClick={(e) => { e.stopPropagation(); onAddSupportingHabit(h) }}
                    className="rounded-md bg-cyan-500 px-2 py-1 text-[9px] font-semibold text-white hover:bg-cyan-400">
                    + Add
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Progress */}
      <div className="relative">
        <div className="flex justify-between text-[11px] text-muted-foreground mb-1.5">
          <span className="font-medium">
            {meta.label.replace('ly', '')} {elapsed} <span className="text-muted-foreground/50">of</span> {total}
          </span>
          <span className="font-semibold tabular-nums">{pct}%</span>
        </div>
        <div className="h-2 w-full rounded-full bg-white/10">
          <div className="h-full rounded-full bg-gradient-to-r from-primary via-teal-light to-gold transition-all"
            style={{ width: `${pct}%` }} />
        </div>
        <div className="flex justify-between text-[10px] text-muted-foreground/60 mt-1">
          <span>Started {new Date(c.start_date + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</span>
          <span>{Math.max(0, total - elapsed)} {meta.unitLabel}{total - elapsed === 1 ? '' : 's'} left</span>
        </div>
        {completedDays > 0 && (
          <p className="text-[10px] text-muted-foreground mt-1">
            ✓ {completedDays} of {total} {meta.unitLabel}s done so far ({completionRate}% completion)
          </p>
        )}
      </div>

      {/* Photo proof badge */}
      {c.requires_photo && (
        <div className="relative flex items-center gap-2 text-[11px] text-gold">
          <Camera size={11} /> Photo proof required each {meta.unitLabel}
        </div>
      )}

      {/* Photo preview */}
      {checkingIn && photoForChallenge && (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={photoForChallenge} alt="proof" className="relative w-full h-32 object-cover rounded-xl" />
      )}

      {/* Check-in actions */}
      <div className="relative">
        {periodChecked ? (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 py-3 text-center text-sm font-semibold text-emerald-300">
            ✓ Done {meta.unitLabel === 'day' ? 'today' : `this ${meta.unitLabel}`} · {c.current_streak} {meta.unitLabel}{c.current_streak === 1 ? '' : 's'} streak 🔥
          </div>
        ) : checkingIn && !photoForChallenge && c.requires_photo ? (
          <div className="text-center text-sm text-gold py-3">
            📸 {uploadingPhoto ? 'Uploading…' : 'Take your proof photo'}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={() => {
                if (c.requires_photo && !photoForChallenge) onCapturePhoto()
                else onCheckIn(true)
              }}
              className="rounded-xl bg-emerald-500/20 border border-emerald-500/30 py-3.5
                         text-sm font-semibold text-emerald-400 active:scale-95 transition-all">
              ✅ Done {meta.unitLabel === 'day' ? 'today' : `this ${meta.unitLabel}`}
            </button>
            <button
              onClick={() => {
                if (confirm(`Mark this ${meta.unitLabel} as missed? Your streak resets, but the challenge keeps running until ${new Date(new Date(c.start_date + 'T12:00:00').getTime() + c.duration_days * 86400000).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}.`))
                  onCheckIn(false)
              }}
              className="rounded-xl bg-red-500/10 border border-red-500/20 py-3.5
                         text-sm font-semibold text-red-400 active:scale-95 transition-all">
              ✗ Missed
            </button>
          </div>
        )}
        {checkingIn && photoForChallenge && (
          <button onClick={() => onCheckIn(true)}
            className="mt-2 w-full rounded-xl bg-emerald-500 py-3 text-sm font-semibold text-white active:scale-95">
            ✅ Submit with photo
          </button>
        )}
      </div>
    </div>
  )
}
