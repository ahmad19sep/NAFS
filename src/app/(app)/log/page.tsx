'use client'

export const dynamic = 'force-dynamic'

import { useState, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { computeWeightedHours, computeRequiredPerDay, computeTodaysPull, computeIdentityScore } from '@/lib/mapping-engine'
import { todayString, getMoodEmoji } from '@/lib/utils'
import { Camera, Mic, MicOff } from 'lucide-react'

const PRAYER_NAMES = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha']

interface PrayerEntry {
  completed: boolean
  onTime: boolean
}

export default function LogPage() {
  const router = useRouter()
  const supabase = createClient()
  const fileRef = useRef<HTMLInputElement>(null)

  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [recording, setRecording] = useState(false)

  // Prayer state
  const [prayers, setPrayers] = useState<Record<string, PrayerEntry>>(
    Object.fromEntries(PRAYER_NAMES.map((n) => [n, { completed: false, onTime: false }]))
  )

  // Numeric fields
  const [dreamHours, setDreamHours] = useState(0)
  const [studyHours, setStudyHours] = useState(0)
  const [screenMins, setScreenMins] = useState(120)
  const [sleepStart, setSleepStart] = useState('23:00')
  const [sleepEnd, setSleepEnd] = useState('06:30')
  const [mood, setMood] = useState(5)
  const [quranPages, setQuranPages] = useState(0)
  const [exercised, setExercised] = useState(false)

  // Activity breakdown for weighted hours
  const [activities, setActivities] = useState([
    { name: 'Shipping code / production', weight: 3.0, hours: 0 },
    { name: 'Portfolio / open source', weight: 2.5, hours: 0 },
    { name: 'Deep learning', weight: 1.5, hours: 0 },
    { name: 'Tutorials / videos', weight: 0.5, hours: 0 },
  ])

  // Free text
  const [reflection, setReflection] = useState('')
  const [photoUrls, setPhotoUrls] = useState<string[]>([])
  const [voiceNoteUrl, setVoiceNoteUrl] = useState<string | null>(null)

  function togglePrayer(name: string, field: 'completed' | 'onTime') {
    setPrayers((prev) => ({
      ...prev,
      [name]: { ...prev[name], [field]: !prev[name][field] },
    }))
  }

  async function handlePhotoUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (!files.length) return
    setUploading(true)

    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const uploaded: string[] = []
    for (const file of files) {
      const ext = file.name.split('.').pop()
      const path = `${user.id}/${todayString()}-${Date.now()}.${ext}`
      const { error } = await supabase.storage.from('log-photos').upload(path, file)
      if (!error) {
        const { data } = supabase.storage.from('log-photos').getPublicUrl(path)
        uploaded.push(data.publicUrl)
      }
    }
    setPhotoUrls((prev) => [...prev, ...uploaded])
    setUploading(false)
  }

  async function handleSave() {
    setSaving(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    // Load dream for computation
    const { data: profile } = await supabase
      .from('users')
      .select('dreams(*)')
      .eq('id', user.id)
      .single()

    const dream = profile?.dreams as any

    // Compute weighted hours
    const weightedHoursToday = computeWeightedHours(
      activities.map((a) => ({ weight: a.weight, hours: a.hours }))
    )

    // Compute required per day
    const totalRequired = dream ? dream.total_hours_required * 1.8 : 5400
    const requiredPerDay = dream
      ? computeRequiredPerDay(totalRequired, dream.dream_date)
      : 9.0

    const { daysPulled } = computeTodaysPull(weightedHoursToday, requiredPerDay)

    // Compute identity score
    const prayersOnTime = PRAYER_NAMES.filter((n) => prayers[n].onTime).length
    const totalPrayers = PRAYER_NAMES.filter((n) => prayers[n].completed).length
    const sleepOnTime = sleepStart <= '23:30'
    const screenWithinLimit = screenMins <= 120

    const identityScore = computeIdentityScore({
      prayersOnTime,
      totalPrayers: Math.max(totalPrayers, 1),
      quranPagesRead: quranPages,
      dhikrCompleted: false,
      weightedHoursToday,
      requiredPerDay,
      sleepOnTime,
      screenTimeWithinLimit: screenWithinLimit,
      streakIntact: true,
      exercised,
    })

    // Upsert today's log
    const today = todayString()
    await supabase.from('daily_logs').upsert({
      user_id: user.id,
      date: today,
      prayers: prayers as any,
      dream_work_hours: dreamHours,
      study_hours: studyHours,
      screen_time_mins: screenMins,
      sleep_start: sleepStart,
      sleep_end: sleepEnd,
      mood,
      reflection_text: reflection || null,
      voice_note_url: voiceNoteUrl,
      photo_urls: photoUrls,
      weighted_hours_today: weightedHoursToday,
      identity_score: identityScore,
      todays_pull_days: daysPulled,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'user_id,date' })

    // Log Quran if pages > 0
    if (quranPages > 0) {
      await supabase.from('quran_log').upsert({
        user_id: user.id,
        date: today,
        pages_read: quranPages,
      }, { onConflict: 'user_id,date' })
    }

    // Trigger AI pull narrator
    try {
      await fetch('/api/ai/pull-narrator', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          weighted_hours_today: weightedHoursToday,
          required_per_day: requiredPerDay,
          delta_days: daysPulled,
          biggest_drag: screenMins > 120 ? 'screen_time' : 'n/a',
          biggest_win: prayersOnTime >= 4 ? 'prayers_on_time' : 'n/a',
          tomorrow_required: requiredPerDay,
        }),
      })
    } catch {}

    router.push('/dashboard')
  }

  return (
    <div className="mx-auto max-w-md space-y-6 px-4">
      <div className="pt-2">
        <h1 className="text-2xl font-bold text-foreground">Daily Log</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
      </div>

      {/* Prayers */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">🕌 Prayers</p>
        <div className="space-y-3">
          {PRAYER_NAMES.map((name) => (
            <div key={name} className="flex items-center justify-between">
              <span className="font-medium text-foreground">{name}</span>
              <div className="flex items-center gap-3">
                <button
                  onClick={() => togglePrayer(name, 'onTime')}
                  disabled={!prayers[name].completed}
                  className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all
                    ${prayers[name].onTime
                      ? 'bg-gold/20 text-gold border border-gold/30'
                      : 'bg-white/5 text-muted-foreground border border-white/10 disabled:opacity-30'}`}
                >
                  On time
                </button>
                <button
                  onClick={() => {
                    togglePrayer(name, 'completed')
                    if (prayers[name].completed) togglePrayer(name, 'onTime')
                  }}
                  className={`h-7 w-7 rounded-full border-2 transition-all
                    ${prayers[name].completed
                      ? 'border-emerald-400 bg-emerald-400/20'
                      : 'border-white/20 bg-transparent'}`}
                >
                  {prayers[name].completed && <span className="text-emerald-400 text-sm">✓</span>}
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Dream work activities */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">🌠 Dream work hours</p>
        <div className="space-y-4">
          {activities.map((act, i) => (
            <div key={i}>
              <div className="flex items-center justify-between mb-1">
                <span className="text-sm text-foreground">{act.name}</span>
                <span className={`text-xs font-bold px-2 py-0.5 rounded-full
                  ${act.weight >= 2.5 ? 'bg-emerald-500/20 text-emerald-400'
                    : act.weight >= 1.5 ? 'bg-gold/20 text-gold'
                    : 'bg-orange-500/20 text-orange-400'}`}>
                  {act.weight}×
                </span>
              </div>
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={0}
                  max={12}
                  step={0.5}
                  value={act.hours}
                  onChange={(e) => {
                    const updated = [...activities]
                    updated[i] = { ...act, hours: Number(e.target.value) }
                    setActivities(updated)
                  }}
                  className="flex-1"
                />
                <span className="w-12 text-right font-bold tabular-nums text-gold text-sm">
                  {act.hours}h
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Weighted total preview */}
        <div className="mt-4 rounded-xl bg-primary/10 border border-primary/20 p-3">
          <p className="text-xs text-muted-foreground">Weighted total today</p>
          <p className="text-2xl font-bold text-gold tabular-nums">
            {computeWeightedHours(activities.map((a) => ({ weight: a.weight, hours: a.hours }))).toFixed(1)}
          </p>
          <p className="text-xs text-muted-foreground">weighted hours</p>
        </div>
      </section>

      {/* Study, sleep, screen time */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">📚 Study & lifestyle</p>
        <div className="space-y-5">
          <div>
            <label className="text-sm text-foreground mb-2 block">Study hours (non-dream work)</label>
            <div className="flex items-center gap-3">
              <input type="range" min={0} max={12} step={0.5} value={studyHours}
                onChange={(e) => setStudyHours(Number(e.target.value))} className="flex-1" />
              <span className="w-10 text-right font-bold text-gold text-sm tabular-nums">{studyHours}h</span>
            </div>
          </div>

          <div>
            <label className="text-sm text-foreground mb-2 block">Screen time (mins)</label>
            <div className="flex items-center gap-3">
              <input type="range" min={0} max={600} step={15} value={screenMins}
                onChange={(e) => setScreenMins(Number(e.target.value))} className="flex-1" />
              <span className={`w-14 text-right font-bold text-sm tabular-nums
                ${screenMins > 240 ? 'text-red-400' : screenMins > 120 ? 'text-orange-400' : 'text-emerald-400'}`}>
                {screenMins}m
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-foreground mb-2 block">Slept at</label>
              <input type="time" value={sleepStart} onChange={(e) => setSleepStart(e.target.value)}
                className="log-input" />
            </div>
            <div>
              <label className="text-sm text-foreground mb-2 block">Woke at</label>
              <input type="time" value={sleepEnd} onChange={(e) => setSleepEnd(e.target.value)}
                className="log-input" />
            </div>
          </div>
        </div>
      </section>

      {/* Quran + exercise */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">📖 Deen & body</p>
        <div className="space-y-4">
          <div>
            <label className="text-sm text-foreground mb-2 block">Quran pages read today</label>
            <div className="flex items-center gap-3">
              <button onClick={() => setQuranPages(Math.max(0, quranPages - 1))}
                className="h-9 w-9 rounded-xl border border-white/10 bg-white/5 text-lg font-bold text-foreground">−</button>
              <span className="flex-1 text-center text-2xl font-bold tabular-nums text-gold">{quranPages}</span>
              <button onClick={() => setQuranPages(quranPages + 1)}
                className="h-9 w-9 rounded-xl border border-white/10 bg-white/5 text-lg font-bold text-foreground">+</button>
            </div>
          </div>

          <button
            onClick={() => setExercised(!exercised)}
            className={`w-full rounded-xl border py-3 font-semibold transition-all
              ${exercised
                ? 'border-emerald-500/50 bg-emerald-500/10 text-emerald-400'
                : 'border-white/10 bg-white/5 text-muted-foreground'}`}
          >
            {exercised ? '✅ Exercised today' : '🏃 Mark exercise done'}
          </button>
        </div>
      </section>

      {/* Mood */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">😊 Mood</p>
        <div className="flex items-center gap-3">
          <span className="text-3xl">{getMoodEmoji(mood)}</span>
          <input type="range" min={1} max={10} value={mood}
            onChange={(e) => setMood(Number(e.target.value))} className="flex-1" />
          <span className="w-6 text-right font-bold text-gold tabular-nums">{mood}</span>
        </div>
      </section>

      {/* Photo proof */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">📸 Photo proof</p>
        <p className="mb-3 text-xs text-muted-foreground">
          Attach a photo of your work — code on screen, book page, prayer rug.
          The act of taking the photo forces honesty.
        </p>
        <div className="flex flex-wrap gap-2 mb-3">
          {photoUrls.map((url, i) => (
            <div key={i} className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={url} alt={`Proof ${i + 1}`} className="h-20 w-20 rounded-xl object-cover" />
              <button
                onClick={() => setPhotoUrls((prev) => prev.filter((_, j) => j !== i))}
                className="absolute -right-1 -top-1 h-5 w-5 rounded-full bg-red-500 text-xs text-white flex items-center justify-center"
              >✕</button>
            </div>
          ))}
        </div>
        <button
          onClick={() => fileRef.current?.click()}
          disabled={uploading}
          className="flex items-center gap-2 rounded-xl border border-dashed border-white/20
                     bg-white/5 px-4 py-3 text-sm text-muted-foreground
                     hover:border-primary/50 hover:bg-white/10 transition-all"
        >
          <Camera size={18} />
          {uploading ? 'Uploading…' : 'Add photo proof'}
        </button>
        <input ref={fileRef} type="file" accept="image/*" multiple onChange={handlePhotoUpload} className="hidden" />
      </section>

      {/* Reflection */}
      <section className="nafs-card p-5">
        <p className="section-header mb-4">✍️ Reflection</p>
        <textarea
          value={reflection}
          onChange={(e) => setReflection(e.target.value)}
          placeholder="What happened today? What would you tell your future self?"
          rows={4}
          className="log-input resize-none"
        />
      </section>

      {/* Save button */}
      <div className="pb-8">
        <button
          onClick={handleSave}
          disabled={saving}
          className="w-full rounded-2xl bg-gradient-to-r from-primary to-teal-light py-5
                     font-bold text-lg text-white shadow-lg glow-teal
                     transition-all hover:opacity-90 disabled:opacity-50 active:scale-95"
        >
          {saving ? 'Saving your day…' : 'Save log & get verdict'}
        </button>
      </div>
    </div>
  )
}
