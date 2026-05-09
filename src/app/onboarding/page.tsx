'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'

// No prayers here — prayers live in Daily Tasks on the home screen
const STARTER_HABITS = [
  { name: 'Exercise', emoji: '💪', type: 'boolean', target_value: 1, time_target_mins: 30, unit: '', category: 'health', score_weight: 3 },
  { name: 'Reading', emoji: '📚', type: 'duration', target_value: 30, time_target_mins: 30, unit: 'mins', category: 'health', score_weight: 2 },
  { name: 'Deep Work / Coding', emoji: '💻', type: 'duration', target_value: 120, time_target_mins: 120, unit: 'mins', category: 'work', score_weight: 5 },
  { name: 'No Social Media', emoji: '📵', type: 'boolean', target_value: 1, time_target_mins: 0, unit: '', category: 'discipline', score_weight: 3 },
  { name: 'Sleep by 11 PM', emoji: '😴', type: 'boolean', target_value: 1, time_target_mins: 0, unit: '', category: 'health', score_weight: 2 },
  { name: 'Drink Water (8 glasses)', emoji: '💧', type: 'count', target_value: 8, time_target_mins: 0, unit: 'glasses', category: 'health', score_weight: 1 },
  { name: 'Journaling', emoji: '✍️', type: 'duration', target_value: 15, time_target_mins: 15, unit: 'mins', category: 'mindset', score_weight: 2 },
  { name: 'Cold Shower', emoji: '🚿', type: 'boolean', target_value: 1, time_target_mins: 0, unit: '', category: 'discipline', score_weight: 1 },
  { name: 'Quran Reading', emoji: '📖', type: 'count', target_value: 5, time_target_mins: 20, unit: 'pages', category: 'islamic', score_weight: 3 },
  { name: 'Dhikr', emoji: '📿', type: 'count', target_value: 100, time_target_mins: 10, unit: 'count', category: 'islamic', score_weight: 2 },
]

export default function OnboardingPage() {
  const router = useRouter()
  const supabase = createClient()

  const [step, setStep] = useState(1)
  const [userName, setUserName] = useState('')
  const [selectedHabits, setSelectedHabits] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    supabase.auth.getUser().then(({ data: { user } }) => {
      if (!user) { router.push('/auth'); return }
      // Extract name from email (before @) or Google display name
      const displayName = user.user_metadata?.full_name
        || user.user_metadata?.name
        || user.email?.split('@')[0]
        || 'Friend'
      setUserName(displayName)
      setLoading(false)
    })
  }, [])

  function toggleHabit(name: string) {
    setSelectedHabits((prev) =>
      prev.includes(name) ? prev.filter((n) => n !== name) : [...prev, name]
    )
  }

  async function finish() {
    setSaving(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    await supabase.from('users').upsert({
      id: user.id,
      name: userName,
      email: user.email!,
      onboarding_complete: true,
    })

    const habitsToAdd = STARTER_HABITS
      .filter((h) => selectedHabits.includes(h.name))
      .map((h, i) => ({
        user_id: user.id,
        name: h.name,
        emoji: h.emoji,
        type: h.type,
        target_value: h.target_value,
        time_target_mins: h.time_target_mins,
        unit: h.unit,
        category: h.category,
        score_weight: h.score_weight,
        current_streak: 0,
        longest_streak: 0,
        is_active: true,
        sort_order: i,
      }))

    if (habitsToAdd.length > 0) {
      await supabase.from('habits').insert(habitsToAdd)
    }

    router.push('/dashboard')
  }

  if (loading) return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="h-8 w-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
    </div>
  )

  const firstName = userName.split(' ')[0]

  return (
    <div className="flex min-h-screen flex-col bg-background">
      {/* Progress */}
      <div className="h-1 w-full bg-white/10">
        <div className="h-full bg-gradient-to-r from-primary to-gold transition-all duration-500"
          style={{ width: `${(step / 2) * 100}%` }} />
      </div>
      <div className="flex items-center justify-between px-6 py-4">
        <button onClick={() => step > 1 && setStep(1)}
          className={cn('text-sm text-muted-foreground', step === 1 && 'opacity-0 pointer-events-none')}>
          ← Back
        </button>
        <span className="text-xs text-muted-foreground">{step} / 2</span>
        <div className="w-10" />
      </div>

      <div className="flex-1 overflow-y-auto px-6 pb-10">

        {/* Step 1: Welcome */}
        {step === 1 && (
          <div className="space-y-8 pt-4 animate-slide-up">
            <div>
              <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-primary/80 glow-teal mb-6">
                <span className="arabic text-4xl text-gold">ن</span>
              </div>
              <h2 className="text-2xl font-bold text-foreground">
                Bismillah, {firstName} 👋
              </h2>
              <p className="mt-2 text-muted-foreground leading-relaxed">
                NAFS tracks your prayers, habits, challenges and goals — all in one place.
                AI analyzes your day every evening and shows where you&apos;re falling behind.
              </p>
            </div>

            <div className="space-y-3">
              {[
                { emoji: '🕌', title: 'Daily tasks + 5 Prayers', desc: 'Tap each prayer daily. Miss one — your score drops.' },
                { emoji: '💪', title: 'Habits with time tracking', desc: 'Set a time target per habit, log actual time done.' },
                { emoji: '🔥', title: 'Challenges — strict mode', desc: 'Photo proof required. Miss one day — challenge ends.' },
                { emoji: '⭐', title: 'Goals with AI plan', desc: 'Set a goal, AI builds your step-by-step action plan.' },
                { emoji: '📱', title: 'Screen time tracking', desc: 'Upload screenshot — AI reads which apps you used most.' },
              ].map((f) => (
                <div key={f.title} className="nafs-card p-4 flex items-center gap-3">
                  <span className="text-2xl flex-shrink-0">{f.emoji}</span>
                  <div>
                    <p className="font-semibold text-sm text-foreground">{f.title}</p>
                    <p className="text-xs text-muted-foreground">{f.desc}</p>
                  </div>
                </div>
              ))}
            </div>

            <button onClick={() => setStep(2)}
              className="w-full rounded-xl bg-primary py-4 font-semibold text-white
                         hover:bg-teal-light transition-all active:scale-95">
              Get started →
            </button>
          </div>
        )}

        {/* Step 2: Habits */}
        {step === 2 && (
          <div className="space-y-4 pt-4 animate-slide-up">
            <div>
              <h2 className="text-2xl font-bold text-foreground">Pick your habits</h2>
              <p className="mt-1.5 text-sm text-muted-foreground">
                These are things you want to do regularly — not daily tasks like prayers.
                Each habit has a score weight that adds to your daily total.
              </p>
            </div>

            <div className="space-y-2">
              {STARTER_HABITS.map((h) => {
                const isSelected = selectedHabits.includes(h.name)
                return (
                  <button key={h.name} onClick={() => toggleHabit(h.name)}
                    className={cn(
                      'flex items-center gap-3 w-full rounded-2xl border p-4 text-left transition-all active:scale-[0.98]',
                      isSelected
                        ? 'border-primary/60 bg-primary/15'
                        : 'border-white/10 bg-white/5 hover:border-white/20'
                    )}>
                    <span className="text-2xl flex-shrink-0">{h.emoji}</span>
                    <div className="flex-1 min-w-0">
                      <p className="font-medium text-sm text-foreground">{h.name}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {h.time_target_mins > 0 ? `${h.time_target_mins} min/day · ` : ''}
                        {h.score_weight} pt{h.score_weight !== 1 ? 's' : ''}
                      </p>
                    </div>
                    <div className={cn(
                      'h-6 w-6 rounded-full border-2 flex-shrink-0 flex items-center justify-center transition-all',
                      isSelected ? 'border-primary bg-primary' : 'border-white/20'
                    )}>
                      {isSelected && <Check size={14} className="text-white" />}
                    </div>
                  </button>
                )
              })}
            </div>

            <p className="text-xs text-muted-foreground text-center pb-2">
              You can skip this and add habits later from the Habits tab.
            </p>

            <div className="sticky bottom-0 bg-background pt-3 pb-2 space-y-2">
              <div className="flex justify-between text-xs text-muted-foreground px-1">
                <span>{selectedHabits.length} selected</span>
                <span>
                  +{STARTER_HABITS.filter((h) => selectedHabits.includes(h.name)).reduce((s, h) => s + h.score_weight, 0)} pts to daily score
                </span>
              </div>
              <button onClick={finish} disabled={saving}
                className="w-full rounded-xl bg-gradient-to-r from-primary to-teal-light py-4
                           font-bold text-white shadow-lg transition-all hover:opacity-90
                           disabled:opacity-50 active:scale-95">
                {saving ? 'Setting up NAFS…' : "Let's go — بسم الله"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
