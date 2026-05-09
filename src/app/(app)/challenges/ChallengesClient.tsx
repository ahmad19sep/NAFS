'use client'

import { useState, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Plus, Camera, Flame } from 'lucide-react'
import { cn, todayString } from '@/lib/utils'

interface Challenge {
  id: string
  title: string
  emoji: string
  description: string
  duration_days: number
  start_date: string
  requires_photo: boolean
  sadqa_amount: number | null
  sadqa_currency: string
  current_streak: number
  longest_streak: number
  status: 'active' | 'completed' | 'failed'
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

export default function ChallengesClient({ challenges, userId }: Props) {
  const router = useRouter()
  const supabase = createClient()
  const fileRef = useRef<HTMLInputElement>(null)

  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [emoji, setEmoji] = useState('🎯')
  const [description, setDescription] = useState('')
  const [duration, setDuration] = useState(21)
  const [requiresPhoto, setRequiresPhoto] = useState(false)
  const [creating, setCreating] = useState(false)

  const [checkingIn, setCheckingIn] = useState<string | null>(null)
  const [photoForChallenge, setPhotoForChallenge] = useState<string | null>(null)
  const [uploadingPhoto, setUploadingPhoto] = useState(false)

  const today = todayString()
  const active = challenges.filter((c) => c.status === 'active')
  const finished = challenges.filter((c) => c.status !== 'active')

  async function createChallenge() {
    if (!title.trim()) return
    setCreating(true)
    await supabase.from('challenges').insert({
      user_id: userId,
      title,
      emoji,
      description,
      duration_days: duration,
      start_date: today,
      requires_photo: requiresPhoto,
      sadqa_amount: null,
      sadqa_currency: 'PKR',
      current_streak: 0,
      longest_streak: 0,
      status: 'active',
    })
    setCreating(false)
    setShowCreate(false)
    setTitle('')
    setDescription('')
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
    const alreadyDone = challenge.challenge_checkins.some((c) => c.date === today)
    if (alreadyDone) return

    if (completed && challenge.requires_photo && !photoForChallenge) {
      setCheckingIn(challenge.id)
      fileRef.current?.click()
      return
    }

    const newStreak = completed ? challenge.current_streak + 1 : 0

    await supabase.from('challenge_checkins').insert({
      challenge_id: challenge.id,
      date: today,
      completed,
      photo_url: photoForChallenge,
    })

    await supabase.from('challenges').update({
      current_streak: newStreak,
      longest_streak: Math.max(challenge.longest_streak, newStreak),
      // If missed → challenge fails (strict mode)
      status: !completed ? 'failed' : challenge.status,
    }).eq('id', challenge.id)

    setPhotoForChallenge(null)
    setCheckingIn(null)
    router.refresh()
  }

  const _UNUSED = [
    { name: 'Edhi Foundation', url: 'https://edhi.org' },
    { name: 'Saylani Welfare', url: 'https://saylani.org' },
    { name: 'Al-Khidmat', url: 'https://alkhidmat.org' },
  ]

  return (
    <div className="mx-auto max-w-md px-4 space-y-6">
      <div className="flex items-center justify-between pt-3">
        <div>
          <p className="text-xs text-muted-foreground">
            {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
          </p>
          <h1 className="text-2xl font-bold text-foreground">Challenges</h1>
          <p className="text-sm text-muted-foreground mt-0.5">{active.length} active</p>
        </div>
        <button onClick={() => setShowCreate(!showCreate)}
          className="flex items-center gap-1.5 rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white
                     transition-all hover:bg-teal-light active:scale-95">
          <Plus size={16} /> New
        </button>
      </div>

      {/* Hidden file input */}
      <input ref={fileRef} type="file" accept="image/*" capture="environment"
        onChange={handlePhotoUpload} className="hidden" />

      {/* Create form */}
      {showCreate && (
        <div className="nafs-card p-5 space-y-4 animate-slide-up">
          <p className="font-semibold text-foreground">New challenge</p>

          <div className="flex gap-3">
            <input type="text" value={emoji} onChange={(e) => setEmoji(e.target.value)}
              className="log-input w-16 text-center text-2xl" maxLength={2} />
            <input type="text" value={title} onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. No YouTube, Brush teeth daily…"
              className="log-input flex-1" />
          </div>

          <input type="text" value={description} onChange={(e) => setDescription(e.target.value)}
            placeholder="Why this challenge? (optional)"
            className="log-input" />

          <div>
            <label className="section-header mb-2 block">Duration: {duration} days</label>
            <input type="range" min={7} max={365} step={1} value={duration}
              onChange={(e) => setDuration(Number(e.target.value))} className="w-full" />
            <div className="flex justify-between text-xs text-muted-foreground mt-1">
              <span>7d</span><span>30d</span><span>90d</span><span>365d</span>
            </div>
          </div>

          {/* Photo required toggle */}
          <button onClick={() => setRequiresPhoto(!requiresPhoto)}
            className={cn('w-full rounded-xl border p-3 text-sm font-medium text-left flex items-center gap-3 transition-all',
              requiresPhoto ? 'border-gold/50 bg-gold/10 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground'
            )}>
            <Camera size={18} />
            <div>
              <p className="font-semibold">Require photo proof daily</p>
              <p className="text-xs opacity-70">No photo = challenge ends. Forces honesty.</p>
            </div>
          </button>


          <div className="flex gap-2">
            <button onClick={() => setShowCreate(false)}
              className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
              Cancel
            </button>
            <button onClick={createChallenge} disabled={!title.trim() || creating}
              className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                         hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
              {creating ? 'Starting…' : 'Start challenge'}
            </button>
          </div>
        </div>
      )}

      {/* Active challenges */}
      {active.length === 0 && !showCreate && (
        <div className="text-center py-14">
          <p className="text-5xl">🔥</p>
          <p className="mt-3 font-semibold text-foreground">No active challenges</p>
          <p className="mt-1 text-sm text-muted-foreground">Create a challenge and commit. Miss a day — it ends.</p>
        </div>
      )}

      {active.map((c) => {
        const daysIn = Math.max(1, Math.ceil((Date.now() - new Date(c.start_date).getTime()) / 86400000))
        const pct = Math.min(100, Math.round((daysIn / c.duration_days) * 100))
        const todayCheckin = c.challenge_checkins.find((ci) => ci.date === today)
        const waitingForPhoto = checkingIn === c.id && c.requires_photo && !photoForChallenge

        return (
          <div key={c.id} className="nafs-card p-5 space-y-4">
            {/* Header */}
            <div className="flex items-start gap-3">
              <span className="text-3xl">{c.emoji}</span>
              <div className="flex-1">
                <p className="font-bold text-foreground">{c.title}</p>
                {c.description && <p className="text-xs text-muted-foreground mt-0.5">{c.description}</p>}
              </div>
              <div className="flex items-start gap-2">
                <button onClick={() => deleteChallenge(c.id)}
                  className="h-7 w-7 rounded-lg flex items-center justify-center text-xs
                             text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-all">
                  🗑️
                </button>
              <div className="text-right">
                <p className="text-lg font-bold text-orange-400">🔥 {c.current_streak}</p>
                <p className="text-[10px] text-muted-foreground">day streak</p>
              </div>
              </div>
            </div>

            {/* Progress */}
            <div>
              <div className="flex justify-between text-xs text-muted-foreground mb-1">
                <span>
                  Day {daysIn}/{c.duration_days} · Started {new Date(c.start_date + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                </span>
                <span className="font-semibold">{pct}%</span>
              </div>
              <div className="h-2 w-full rounded-full bg-white/10">
                <div className="h-full rounded-full bg-gradient-to-r from-primary to-gold transition-all" style={{ width: `${pct}%` }} />
              </div>
              <div className="flex justify-between text-[10px] text-muted-foreground mt-1">
                <span>Ends: {new Date(new Date(c.start_date).getTime() + c.duration_days * 86400000).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}</span>
                <span>{c.duration_days - daysIn} days left</span>
              </div>
            </div>

            {/* Photo required badge */}
            {c.requires_photo && (
              <div className="flex items-center gap-2 text-xs text-gold">
                <Camera size={12} /> Photo proof required daily
              </div>
            )}

            {/* Photo preview if uploaded */}
            {waitingForPhoto && photoForChallenge && (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={photoForChallenge} alt="Proof" className="w-full h-32 object-cover rounded-xl" />
            )}

            {/* Check-in */}
            {!todayCheckin ? (
              <div className="space-y-2">
                {waitingForPhoto && !photoForChallenge && (
                  <p className="text-sm text-gold text-center">
                    📸 {uploadingPhoto ? 'Uploading photo…' : 'Tap below to take your proof photo'}
                  </p>
                )}
                <div className="grid grid-cols-2 gap-3">
                  <button
                    onClick={() => {
                      if (c.requires_photo && !photoForChallenge) {
                        setCheckingIn(c.id)
                        fileRef.current?.click()
                      } else {
                        checkIn(c, true)
                      }
                    }}
                    className="rounded-xl bg-emerald-500/20 border border-emerald-500/30 py-3.5
                               text-sm font-semibold text-emerald-400 active:scale-95 transition-all">
                    ✅ Done today
                  </button>
                  <button
                    onClick={() => checkIn(c, false)}
                    className="rounded-xl bg-red-500/10 border border-red-500/20 py-3.5
                               text-sm font-semibold text-red-400 active:scale-95 transition-all">
                    ❌ Missed
                  </button>
                </div>
                {waitingForPhoto && photoForChallenge && (
                  <button onClick={() => checkIn(c, true)}
                    className="w-full rounded-xl bg-emerald-500 py-3 text-sm font-semibold text-white active:scale-95">
                    ✅ Submit with photo
                  </button>
                )}
              </div>
            ) : (
              <div className={cn('rounded-xl border py-3 text-center text-sm font-semibold',
                todayCheckin.completed
                  ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-400'
                  : 'border-red-500/20 bg-red-500/10 text-red-400'
              )}>
                {todayCheckin.completed
                  ? `✅ Done — ${c.current_streak} day streak 🔥`
                  : '❌ Missed today — challenge failed'}
              </div>
            )}

          </div>
        )
      })}

      {/* Completed / Failed */}
      {finished.length > 0 && (
        <div className="space-y-2 pb-8">
          <p className="section-header">Past challenges</p>
          {finished.map((c) => (
            <div key={c.id} className={cn('nafs-card p-4 flex items-center gap-3 opacity-70',
              c.status === 'completed' ? '' : 'border-red-500/20'
            )}>
              <span className="text-2xl">{c.emoji}</span>
              <div className="flex-1">
                <p className="text-sm font-medium text-foreground">{c.title}</p>
                <p className="text-xs text-muted-foreground">{c.duration_days} days • Best streak: {c.longest_streak}</p>
              </div>
              <span className="text-xl">{c.status === 'completed' ? '🏆' : '💔'}</span>
              <button onClick={() => deleteChallenge(c.id)}
                className="h-8 w-8 rounded-xl flex items-center justify-center text-muted-foreground hover:text-red-400 hover:bg-red-500/10 transition-all">
                🗑️
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )

  async function deleteChallenge(id: string) {
    if (!confirm('Delete this challenge?')) return
    await fetch(`/api/challenges/${id}`, { method: 'DELETE' })
    router.refresh()
  }
}
