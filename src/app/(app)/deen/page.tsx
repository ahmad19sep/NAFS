'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, useMemo } from 'react'
import { createClient } from '@/lib/supabase/client'
import { cn, todayString } from '@/lib/utils'
import { Plus, X, Flame } from 'lucide-react'
import { PRAYERS, type PrayerStatus } from '@/lib/scoring'
import HistoryTeaserCard from '@/components/HistoryTeaserCard'
import { computeDeenHistory } from '@/lib/history'

const PRAYER_EMOJIS: Record<string, string> = {
  Fajr: '🌙', Dhuhr: '☀️', Asr: '🌤️', Maghrib: '🌅', Isha: '⭐'
}

type ItemType = 'count' | 'boolean'

interface DeenItem {
  name: string
  type: ItemType
  emoji: string
  score_weight: number
  value?: number
  target?: number
  unit?: string
  done?: boolean
}

const SUGGESTED: { name: string; type: ItemType; emoji: string; score_weight: number; target?: number; unit?: string }[] = [
  { name: 'Tahajjud', type: 'boolean', emoji: '🌃', score_weight: 3 },
  { name: 'Witr', type: 'boolean', emoji: '🤲', score_weight: 2 },
  { name: 'Sunnah (Rawatib)', type: 'boolean', emoji: '✨', score_weight: 2 },
  { name: 'Duha', type: 'boolean', emoji: '🌅', score_weight: 2 },
  { name: 'Tarawih', type: 'boolean', emoji: '🕌', score_weight: 3 },

  { name: 'Quran', type: 'count', emoji: '📖', score_weight: 3, target: 5, unit: 'pages' },
  { name: 'Dhikr', type: 'count', emoji: '📿', score_weight: 2, target: 100, unit: 'count' },
  { name: 'Hadith', type: 'count', emoji: '📜', score_weight: 1, target: 1, unit: 'hadith' },
  { name: 'Islamic Learning', type: 'count', emoji: '🎓', score_weight: 2, target: 15, unit: 'mins' },
  { name: 'Listen to Quran', type: 'count', emoji: '🎧', score_weight: 1, target: 10, unit: 'mins' },

  { name: 'Sadaqah', type: 'boolean', emoji: '💝', score_weight: 2 },
  { name: 'Sunnah Fast', type: 'boolean', emoji: '🌙', score_weight: 3 },
  { name: 'Helped someone', type: 'boolean', emoji: '🤝', score_weight: 1 },
  { name: 'Istighfar (100x)', type: 'boolean', emoji: '🤲', score_weight: 1 },
]

interface PastLog {
  date: string
  fajr: number
  dhuhr: number
  asr: number
  maghrib: number
  isha: number
  extra_prayers: DeenItem[]
}

export default function DeenPage() {
  const supabase = createClient()
  const today = todayString()

  const [loading, setLoading] = useState(true)
  const [prayers, setPrayers] = useState<Record<string, PrayerStatus>>(
    PRAYERS.reduce((acc, p) => ({ ...acc, [p]: 0 }), {})
  )
  const [items, setItems] = useState<DeenItem[]>([])
  const [pastLogs, setPastLogs] = useState<PastLog[]>([])
  const [showAdd, setShowAdd] = useState(false)
  const [customName, setCustomName] = useState('')
  const [customEmoji, setCustomEmoji] = useState('✨')
  const [customType, setCustomType] = useState<ItemType>('boolean')
  const [customScore, setCustomScore] = useState(1)
  const [customTarget, setCustomTarget] = useState(1)
  const [customUnit, setCustomUnit] = useState('')
  const [hijriDate, setHijriDate] = useState('')

  useEffect(() => {
    async function load() {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) return

      // Today
      const { data } = await supabase.from('prayer_logs')
        .select('*').eq('user_id', user.id).eq('date', today).single()
      if (data) {
        setPrayers({
          Fajr: (data.fajr ?? 0) as PrayerStatus,
          Dhuhr: (data.dhuhr ?? 0) as PrayerStatus,
          Asr: (data.asr ?? 0) as PrayerStatus,
          Maghrib: (data.maghrib ?? 0) as PrayerStatus,
          Isha: (data.isha ?? 0) as PrayerStatus,
        })
        setItems(data.extra_prayers ?? [])
      }

      // Past 90 days for heatmap + streak
      const ninetyAgo = new Date()
      ninetyAgo.setDate(ninetyAgo.getDate() - 89)
      const { data: past } = await supabase.from('prayer_logs')
        .select('date, fajr, dhuhr, asr, maghrib, isha, extra_prayers')
        .eq('user_id', user.id)
        .gte('date', ninetyAgo.toISOString().split('T')[0])
        .order('date', { ascending: true })
      setPastLogs(past ?? [])

      // Hijri date (external API — abort after 5s so it never blocks the page)
      try {
        const now = new Date()
        const ctrl = new AbortController()
        const timer = setTimeout(() => ctrl.abort(), 5000)
        const res = await fetch(
          `https://api.aladhan.com/v1/gToH/${now.getDate()}-${now.getMonth() + 1}-${now.getFullYear()}`,
          { signal: ctrl.signal }
        )
        clearTimeout(timer)
        const json = await res.json()
        if (json.data) setHijriDate(`${json.data.hijri.day} ${json.data.hijri.month.en} ${json.data.hijri.year} AH`)
      } catch {
        // Non-fatal — Hijri date is decorative
      }

      setLoading(false)
    }
    load()
  }, [])

  async function persist(newPrayers: Record<string, PrayerStatus>, newItems: DeenItem[]) {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    await supabase.from('prayer_logs').upsert({
      user_id: user.id, date: today,
      fajr: newPrayers.Fajr, dhuhr: newPrayers.Dhuhr, asr: newPrayers.Asr,
      maghrib: newPrayers.Maghrib, isha: newPrayers.Isha,
      extra_prayers: newItems,
    }, { onConflict: 'user_id,date' })

    // Update local copy of today's log in pastLogs so streak/heatmap updates live
    setPastLogs((prev) => {
      const filtered = prev.filter((l) => l.date !== today)
      return [...filtered, {
        date: today,
        fajr: newPrayers.Fajr, dhuhr: newPrayers.Dhuhr, asr: newPrayers.Asr,
        maghrib: newPrayers.Maghrib, isha: newPrayers.Isha,
        extra_prayers: newItems,
      }].sort((a, b) => a.date.localeCompare(b.date))
    })
  }

  function cyclePrayer(name: string) {
    const next = ((prayers[name] + 1) % 3) as PrayerStatus
    const updated = { ...prayers, [name]: next }
    setPrayers(updated)
    persist(updated, items)
  }

  function updateItem(i: number, patch: Partial<DeenItem>) {
    const updated = [...items]
    updated[i] = { ...updated[i], ...patch }
    setItems(updated)
    persist(prayers, updated)
  }

  function removeItem(i: number) {
    const updated = items.filter((_, j) => j !== i)
    setItems(updated)
    persist(prayers, updated)
  }

  function addSuggested(s: typeof SUGGESTED[0]) {
    if (items.find((x) => x.name === s.name)) return
    const newItem: DeenItem = { ...s,
      ...(s.type === 'count' ? { value: 0 } : { done: false }),
    }
    const updated = [...items, newItem]
    setItems(updated)
    persist(prayers, updated)
  }

  function addCustom() {
    if (!customName.trim()) return
    const newItem: DeenItem = {
      name: customName.trim(), emoji: customEmoji, type: customType,
      score_weight: customScore,
      ...(customType === 'count' ? { value: 0, target: customTarget, unit: customUnit } : { done: false }),
    }
    const updated = [...items, newItem]
    setItems(updated)
    persist(prayers, updated)
    setCustomName(''); setCustomEmoji('✨'); setCustomType('boolean'); setCustomScore(1); setCustomTarget(1); setCustomUnit('')
    setShowAdd(false)
  }

  // --- Compute scores ---
  function computeDayScore(log: PastLog | null, todayItems?: DeenItem[]) {
    if (!log) return { earned: 0, max: 0, perfect: false, acceptable: false, pct: 0 }
    const prayerEarned = (log.fajr ?? 0) + (log.dhuhr ?? 0) + (log.asr ?? 0) + (log.maghrib ?? 0) + (log.isha ?? 0)
    const prayerMax = 10
    const extras = log.extra_prayers ?? []
    const itemsForCalc = todayItems ?? extras
    const itemEarned = extras.reduce((s, x) => {
      if (x.type === 'count') return s + ((x.value ?? 0) >= (x.target ?? 1) ? (x.score_weight ?? 1) : 0)
      if (x.type === 'boolean') return s + (x.done ? (x.score_weight ?? 1) : 0)
      return s
    }, 0)
    const itemMax = itemsForCalc.reduce((s, x) => s + (x.score_weight ?? 1), 0)
    const earned = prayerEarned + itemEarned
    const max = prayerMax + itemMax
    const pct = max > 0 ? Math.round((earned / max) * 100) : 0

    // Acceptable day = all 5 prayers prayed (≥1 each) + all custom items done
    const allPrayersPrayed =
      (log.fajr ?? 0) >= 1 && (log.dhuhr ?? 0) >= 1 && (log.asr ?? 0) >= 1 &&
      (log.maghrib ?? 0) >= 1 && (log.isha ?? 0) >= 1
    const allItemsDone = extras.every((x) =>
      x.type === 'count' ? (x.value ?? 0) >= (x.target ?? 1) : x.done === true
    )
    const acceptable = allPrayersPrayed && allItemsDone

    return { earned, max, perfect: max > 0 && earned === max, acceptable, pct }
  }

  // Today's score (using current items)
  const todayLog: PastLog = {
    date: today,
    fajr: prayers.Fajr, dhuhr: prayers.Dhuhr, asr: prayers.Asr,
    maghrib: prayers.Maghrib, isha: prayers.Isha,
    extra_prayers: items,
  }
  const todayScore = computeDayScore(todayLog, items)

  // 30-day Deen history (for teaser sparkline)
  const deenHistory = useMemo(
    () => computeDeenHistory(pastLogs as any, today),
    [pastLogs, today]
  )

  // Build heatmap of last 90 days
  const heatmapDays = (() => {
    const days: { date: string; pct: number; acceptable: boolean; perfect: boolean; isToday: boolean }[] = []
    const todayObj = new Date(today)
    for (let i = 89; i >= 0; i--) {
      const d = new Date(todayObj)
      d.setDate(todayObj.getDate() - i)
      const dateStr = d.toISOString().split('T')[0]
      const log = pastLogs.find((p) => p.date === dateStr) ?? null
      const score = computeDayScore(log, items)
      days.push({
        date: dateStr, pct: score.pct,
        acceptable: score.acceptable, perfect: score.perfect,
        isToday: dateStr === today,
      })
    }
    return days
  })()

  // Streak = consecutive acceptable days (all prayers prayed + all items done; jamat is bonus)
  const currentStreak = (() => {
    let streak = 0
    for (let i = heatmapDays.length - 1; i >= 0; i--) {
      const d = heatmapDays[i]
      if (d.isToday && !d.acceptable) continue  // don't penalize for today not yet done
      if (d.acceptable) streak++
      else break
    }
    return streak
  })()

  const longestStreak = (() => {
    let longest = 0, cur = 0
    for (const d of heatmapDays) {
      if (d.acceptable) { cur++; longest = Math.max(longest, cur) }
      else cur = 0
    }
    return longest
  })()

  function heatmapColor(d: typeof heatmapDays[0]) {
    if (d.perfect) return 'bg-emerald-400'         // all prayers in jamat + items
    if (d.acceptable) return 'bg-emerald-500/60'   // all prayers prayed + items
    if (d.pct >= 50) return 'bg-gold/40'
    if (d.pct >= 25) return 'bg-orange-500/30'
    if (d.pct > 0) return 'bg-red-500/20'
    return 'bg-white/8'
  }

  if (loading) return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="h-8 w-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
    </div>
  )

  return (
    <div className="mx-auto max-w-md px-4 space-y-5 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-gold/70">{hijriDate}</p>
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold text-foreground">Deen</h1>
          <span className={cn('text-2xl font-bold tabular-nums',
            todayScore.pct === 100 ? 'text-emerald-400'
            : todayScore.pct >= 70 ? 'text-gold'
            : todayScore.pct >= 40 ? 'text-orange-400' : 'text-muted-foreground'
          )}>
            {todayScore.pct}%
          </span>
        </div>
        <p className="text-sm text-muted-foreground mt-0.5">
          {todayScore.earned}/{todayScore.max} points today
        </p>
        {/* Daily progress bar */}
        <div className="mt-2 h-1.5 w-full rounded-full bg-white/10">
          <div className={cn('h-full rounded-full transition-all',
            todayScore.pct === 100 ? 'bg-emerald-400'
            : todayScore.pct >= 70 ? 'bg-gold'
            : 'bg-orange-400'
          )} style={{ width: `${todayScore.pct}%` }} />
        </div>
      </div>

      {/* Streak card */}
      <div className="nafs-card p-5 space-y-3">
        <div className="flex items-center justify-between">
          <div>
            <p className="section-header mb-1">Deen streak</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-bold tabular-nums text-orange-400">🔥 {currentStreak}</span>
              <span className="text-xs text-muted-foreground">days</span>
            </div>
          </div>
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Longest</p>
            <p className="text-lg font-bold tabular-nums text-gold">{longestStreak} 🏆</p>
          </div>
        </div>
        <p className="text-[10px] text-muted-foreground -mt-1 leading-relaxed">
          Pray all 5 (alone is OK) + complete every deen item → streak continues.
          Jamat is a bonus 🌟. Miss a prayer or task → streak resets.
        </p>
      </div>

      {/* History teaser */}
      <HistoryTeaserCard
        days={deenHistory}
        title="Deen history"
        href="/history?tab=deen"
        emoji="🕌"
        accent="gold"
      />

      {/* 5 Daily Prayers */}
      <div className="nafs-card p-4 space-y-2">
        <div className="flex items-center justify-between mb-2">
          <p className="section-header">🕌 5 Daily Prayers</p>
          <span className="text-xs text-gold font-bold tabular-nums">
            {PRAYERS.reduce((s, p) => s + prayers[p], 0)}/10
          </span>
        </div>
        <p className="text-[10px] text-muted-foreground -mt-1 mb-1">1 pt alone · 2 pts in jamat</p>
        {PRAYERS.map((p) => (
          <PrayerRow key={p} name={p} status={prayers[p]} emoji={PRAYER_EMOJIS[p]} onTap={() => cyclePrayer(p)} />
        ))}
      </div>

      {/* Custom deen items */}
      <div className="nafs-card p-4 space-y-2">
        <div className="flex items-center justify-between mb-2">
          <p className="section-header">⭐ My Deen items</p>
          <button onClick={() => setShowAdd(!showAdd)}
            className="text-xs text-gold flex items-center gap-1 hover:text-gold-light">
            <Plus size={12} /> Add
          </button>
        </div>

        {/* Add panel */}
        {showAdd && (
          <div className="space-y-3 p-3 rounded-xl border border-white/10 bg-white/5">
            <p className="text-xs font-semibold text-foreground">Quick add</p>
            <div className="flex flex-wrap gap-1.5 max-h-44 overflow-y-auto scrollbar-hide">
              {SUGGESTED.filter((s) => !items.find((x) => x.name === s.name)).map((s) => (
                <button key={s.name} onClick={() => addSuggested(s)}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-foreground
                             hover:border-gold/40 hover:bg-gold/10 transition-all flex items-center gap-1.5">
                  <span>{s.emoji}</span>
                  <span>{s.name}</span>
                  <span className="text-[9px] text-gold/80">{s.score_weight}pt</span>
                </button>
              ))}
            </div>

            <div className="border-t border-white/10 pt-3 space-y-2">
              <p className="text-xs font-semibold text-foreground">Or create custom</p>
              <div className="flex gap-2">
                <input type="text" value={customEmoji} onChange={(e) => setCustomEmoji(e.target.value)}
                  className="log-input w-12 text-center text-lg" maxLength={2} />
                <input type="text" value={customName} onChange={(e) => setCustomName(e.target.value)}
                  placeholder="Item name…" className="log-input flex-1 text-sm" />
              </div>

              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Type</label>
                <div className="grid grid-cols-2 gap-1.5">
                  {(['boolean', 'count'] as const).map((t) => (
                    <button key={t} onClick={() => setCustomType(t)}
                      className={cn('rounded-lg border py-2 text-xs font-semibold',
                        customType === t ? 'border-primary bg-primary/20 text-primary' : 'border-white/10 bg-white/5 text-muted-foreground'
                      )}>
                      {t === 'boolean' ? 'Yes / No' : 'Counter'}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">
                  Score weight (how many pts when done)
                </label>
                <div className="flex gap-1.5">
                  {[1, 2, 3, 4, 5].map((w) => (
                    <button key={w} onClick={() => setCustomScore(w)}
                      className={cn('flex-1 rounded-lg border py-2 text-sm font-bold',
                        customScore === w ? 'border-gold bg-gold/20 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground'
                      )}>
                      {w}
                    </button>
                  ))}
                </div>
              </div>

              {customType === 'count' && (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Target</label>
                    <input type="number" value={customTarget} min={1}
                      onChange={(e) => setCustomTarget(Number(e.target.value))}
                      className="log-input text-sm" />
                  </div>
                  <div>
                    <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">Unit</label>
                    <input type="text" value={customUnit}
                      onChange={(e) => setCustomUnit(e.target.value)}
                      placeholder="pages, mins…" className="log-input text-sm" />
                  </div>
                </div>
              )}

              <div className="flex gap-2 pt-1">
                <button onClick={() => setShowAdd(false)}
                  className="flex-1 rounded-lg border border-white/10 py-2 text-xs text-muted-foreground">Cancel</button>
                <button onClick={addCustom} disabled={!customName.trim()}
                  className="flex-1 rounded-lg bg-primary py-2 text-xs font-semibold text-white disabled:opacity-40">
                  Add ({customScore}pt)
                </button>
              </div>
            </div>
          </div>
        )}

        {items.length === 0 && !showAdd && (
          <p className="text-xs text-muted-foreground text-center py-3">
            No items yet. Tap +Add to track Quran, Dhikr, Tahajjud, Sadaqah, etc.
          </p>
        )}

        {items.map((item, i) => (
          <DeenItemRow key={i} item={item}
            onUpdate={(patch) => updateItem(i, patch)}
            onRemove={() => removeItem(i)} />
        ))}
      </div>
    </div>
  )
}

function PrayerRow({ name, status, emoji, onTap }: {
  name: string; status: PrayerStatus; emoji: string; onTap: () => void
}) {
  const labels = ['Tap to mark', 'Prayed alone', 'In jamat ⭐']
  const colors = [
    'border-white/10 bg-white/5',
    'border-emerald-500/30 bg-emerald-500/10',
    'border-gold/40 bg-gold/15',
  ]
  return (
    <button onClick={onTap}
      className={cn('w-full flex items-center gap-3 rounded-xl border p-3 transition-all active:scale-95', colors[status])}>
      <span className="text-lg">{emoji}</span>
      <div className="flex-1 text-left">
        <p className="font-semibold text-sm text-foreground">{name}</p>
        <p className="text-[10px] text-muted-foreground mt-0.5">{labels[status]}</p>
      </div>
      <div className="flex gap-0.5">
        <div className={cn('h-2.5 w-2.5 rounded-full', status >= 1 ? 'bg-emerald-400' : 'bg-white/15')} />
        <div className={cn('h-2.5 w-2.5 rounded-full', status >= 2 ? 'bg-gold' : 'bg-white/15')} />
      </div>
      <span className="text-xs font-bold tabular-nums w-7 text-right text-foreground">{status}pt</span>
    </button>
  )
}

function DeenItemRow({ item, onUpdate, onRemove }: {
  item: DeenItem; onUpdate: (p: Partial<DeenItem>) => void; onRemove: () => void
}) {
  // COUNT
  if (item.type === 'count') {
    const value = item.value ?? 0
    const target = item.target ?? 1
    const reached = value >= target
    return (
      <div className={cn('rounded-xl border p-3 transition-all',
        reached ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
      )}>
        <div className="flex items-center gap-3">
          <span className="text-lg">{item.emoji}</span>
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-sm text-foreground truncate">{item.name}</p>
            <p className="text-[10px] text-muted-foreground">
              {value}/{target} {item.unit} · {item.score_weight}pt {reached && '✓'}
            </p>
          </div>
          <button onClick={() => onUpdate({ value: Math.max(0, value - 1) })}
            className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 text-foreground font-bold">−</button>
          <span className="font-bold tabular-nums text-gold w-8 text-center">{value}</span>
          <button onClick={() => onUpdate({ value: value + 1 })}
            className="h-8 w-8 rounded-lg border border-white/10 bg-white/5 text-foreground font-bold">+</button>
          <button onClick={onRemove} className="h-8 w-8 rounded-lg text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10">
            <X size={12} className="mx-auto" />
          </button>
        </div>
        {target > 1 && (
          <div className="mt-2 h-1 w-full rounded-full bg-white/10">
            <div className={cn('h-full rounded-full transition-all', reached ? 'bg-emerald-400' : 'bg-gold')}
              style={{ width: `${Math.min(100, (value / target) * 100)}%` }} />
          </div>
        )}
      </div>
    )
  }

  // BOOLEAN
  const done = item.done ?? false
  return (
    <div className="flex items-center gap-1">
      <button onClick={() => onUpdate({ done: !done })}
        className={cn('flex-1 flex items-center gap-3 rounded-xl border p-3 transition-all active:scale-95',
          done ? 'border-emerald-500/30 bg-emerald-500/10' : 'border-white/10 bg-white/5'
        )}>
        <span className="text-lg">{item.emoji}</span>
        <div className="flex-1 text-left">
          <p className={cn('font-semibold text-sm', done ? 'text-emerald-300' : 'text-foreground')}>
            {item.name}
          </p>
          <p className="text-[10px] text-muted-foreground mt-0.5">
            {done ? `✓ Done · ${item.score_weight} pt` : `Tap when done · ${item.score_weight} pt`}
          </p>
        </div>
        <div className={cn('h-6 w-6 rounded-full border-2 flex items-center justify-center',
          done ? 'border-emerald-400 bg-emerald-400' : 'border-white/20'
        )}>
          {done && <span className="text-white text-xs">✓</span>}
        </div>
      </button>
      <button onClick={onRemove} className="h-9 w-9 rounded-lg text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10">
        <X size={14} className="mx-auto" />
      </button>
    </div>
  )
}
