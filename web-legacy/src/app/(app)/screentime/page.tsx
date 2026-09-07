'use client'

export const dynamic = 'force-dynamic'

import { useState, useRef, useEffect } from 'react'
import { createClient } from '@/lib/supabase/client'
import { Upload, TrendingUp, TrendingDown, Pencil, Plus, X } from 'lucide-react'
import { cn } from '@/lib/utils'

interface AppUsage { app: string; minutes: number; category: string }
interface Log { id: string; date: string; total_mins: number; apps: AppUsage[]; screenshot_url: string | null; ai_summary: string | null }

const CAT_COLORS: Record<string, string> = {
  social: 'bg-red-400', entertainment: 'bg-orange-400',
  productivity: 'bg-emerald-400', communication: 'bg-purple-400',
  learning: 'bg-blue-400', other: 'bg-gray-400',
}

function fmtTime(mins: number) {
  if (!mins) return '—'
  return mins < 60 ? `${mins}m` : `${Math.floor(mins / 60)}h ${mins % 60}m`
}

export default function ScreentimePage() {
  const supabase = createClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [logs, setLogs] = useState<Log[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [selectedLog, setSelectedLog] = useState<Log | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // Manual entry — the fallback whenever the screenshot can't be read
  const [showManual, setShowManual] = useState(false)
  const [manualHours, setManualHours] = useState('')
  const [manualMins, setManualMins] = useState('')
  const [manualApps, setManualApps] = useState<{ app: string; minutes: string }[]>([
    { app: '', minutes: '' },
  ])

  const today = new Date().toISOString().split('T')[0]

  useEffect(() => { load() }, [])

  async function load() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    const { data } = await supabase.from('screentime_logs').select('*')
      .eq('user_id', user.id).order('date', { ascending: false }).limit(30)
    const list = data ?? []
    setLogs(list)
    if (list.length > 0) setSelectedLog(list[0])
    setLoading(false)
  }

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    e.target.value = ''
    setSaving(true)

    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setSaving(false); return }

    // 1. Save screenshot to storage
    let screenshotUrl: string | null = null
    try {
      const ext = file.name.split('.').pop() ?? 'jpg'
      const { error } = await supabase.storage.from('screentime-shots')
        .upload(`${user.id}/${today}.${ext}`, file, { upsert: true })
      if (!error) {
        const { data } = supabase.storage.from('screentime-shots').getPublicUrl(`${user.id}/${today}.${ext}`)
        screenshotUrl = data.publicUrl
      }
    } catch {}

    // 2. Keep the screenshot as the visual record, preserving any numbers
    //    already entered for today. Reading the image automatically was tried
    //    and dropped — small vision models misread the durations, and a
    //    confident wrong number is worse than none. You type the numbers.
    await supabase.from('screentime_logs').upsert({
      user_id: user.id,
      date: today,
      total_mins: todayLog?.total_mins ?? 0,
      apps: todayLog?.apps ?? [],
      screenshot_url: screenshotUrl,
      ai_summary: todayLog?.ai_summary ?? null,
    }, { onConflict: 'user_id,date' })

    await load()
    setSaving(false)

    if (!todayLog?.total_mins) {
      setNotice('Screenshot saved. Now add the numbers below and the coach will read them.')
      setShowManual(true)
    }
  }

  async function saveManual() {
    const totalMins = (Number(manualHours) || 0) * 60 + (Number(manualMins) || 0)
    const apps = manualApps
      .filter((a) => a.app.trim() && Number(a.minutes) > 0)
      .map((a) => ({ app: a.app.trim(), minutes: Number(a.minutes) }))
    if (!totalMins && apps.length === 0) return

    setSaving(true)
    setNotice(null)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setSaving(false); return }

    // The model turns the numbers into a verdict; a failure here must not lose
    // the data the user just typed, so the summary is simply left empty.
    let summary: string | null = null
    try {
      const res = await fetch('/api/screentime/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          manualData: { total: fmtTime(totalMins), totalMins, apps },
        }),
      })
      const r = await res.json().catch(() => ({}))
      if (res.ok && r.summary) summary = r.summary
    } catch {}

    await supabase.from('screentime_logs').upsert({
      user_id: user.id,
      date: today,
      total_mins: totalMins,
      apps: apps.map((a) => ({ ...a, category: 'other' })),
      screenshot_url: todayLog?.screenshot_url ?? null,
      ai_summary: summary,
    }, { onConflict: 'user_id,date' })

    await load()
    setShowManual(false)
    setManualHours(''); setManualMins('')
    setManualApps([{ app: '', minutes: '' }])
    setSaving(false)
  }

  const todayLog = logs.find((l) => l.date === today)
  const displayLog = selectedLog ?? todayLog

  if (loading) return (
    <div className="flex items-center justify-center min-h-[60vh]">
      <div className="h-8 w-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
    </div>
  )

  return (
    <div className="mx-auto max-w-md px-4 space-y-5">
      <div className="pt-3">
        <h1 className="text-2xl font-bold text-foreground">Screen Time</h1>
        <p className="text-sm text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
      </div>

      {/* Attaching a screenshot is optional — the numbers below are the data */}
      <button onClick={() => fileRef.current?.click()} disabled={saving}
        className="flex w-full items-center gap-3 rounded-xl border border-white/10 bg-white/5 px-4 py-3
                   text-left transition-all hover:bg-white/10 active:scale-95 disabled:opacity-60">
        {saving
          ? <div className="h-4 w-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
          : <Upload size={16} className="text-muted-foreground flex-shrink-0" />}
        <div className="min-w-0">
          <p className="text-sm font-medium text-foreground">
            {saving ? 'Saving…' : todayLog?.screenshot_url ? 'Replace screenshot' : 'Attach screenshot'}
          </p>
          <p className="text-[11px] text-muted-foreground">optional · kept as your visual record</p>
        </div>
      </button>
      {/*
        Explicit MIME types rather than image/* — the Android WebView in the
        Capacitor shell jumps straight to the camera for the wildcard, which
        makes picking an existing screenshot impossible. Listing the types
        sends it to the gallery/files picker instead.
      */}
      <input ref={fileRef} type="file"
        accept="image/png,image/jpeg,image/jpg,image/webp,image/heic"
        onChange={handleUpload} className="hidden" />

      {notice && (
        <div className="rounded-xl border border-gold/30 bg-gold/10 p-3 text-xs text-gold">
          {notice}
        </div>
      )}

      {/* Manual entry */}
      {!showManual ? (
        <button onClick={() => setShowManual(true)}
          className="flex w-full items-center gap-4 rounded-2xl border-2 border-dashed
                     border-primary/40 bg-primary/5 p-5 transition-all
                     hover:border-primary hover:bg-primary/10 active:scale-95">
          <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl bg-primary/20">
            <Pencil size={20} className="text-primary" />
          </div>
          <div className="text-left">
            <p className="font-semibold text-foreground">
              {todayLog?.total_mins ? "Update today's screen time" : "Add today's screen time"}
            </p>
            <p className="text-xs text-muted-foreground mt-0.5">
              Total plus your top apps — the coach gives a verdict
            </p>
          </div>
        </button>
      ) : (
        <div className="nafs-card p-4 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm font-semibold text-foreground">Today&apos;s screen time</p>
            <button onClick={() => { setShowManual(false); setNotice(null) }}
              className="text-muted-foreground/60 hover:text-foreground">
              <X size={14} />
            </button>
          </div>

          <div>
            <label className="section-header mb-1.5 block">Total time</label>
            <div className="flex items-center gap-2">
              <input type="number" inputMode="numeric" min="0" max="24"
                value={manualHours} onChange={(e) => setManualHours(e.target.value)}
                placeholder="4" className="log-input flex-1 text-center text-lg font-semibold" />
              <span className="text-xs text-muted-foreground w-8">hrs</span>
              <input type="number" inputMode="numeric" min="0" max="59"
                value={manualMins} onChange={(e) => setManualMins(e.target.value)}
                placeholder="30" className="log-input flex-1 text-center text-lg font-semibold" />
              <span className="text-xs text-muted-foreground w-8">min</span>
            </div>
          </div>

          <div>
            <label className="section-header mb-1.5 block">Top apps</label>
            <div className="space-y-2">
              {manualApps.map((row, i) => (
                <div key={i} className="flex items-center gap-2">
                  <input value={row.app}
                    onChange={(e) => setManualApps((prev) =>
                      prev.map((r, j) => (j === i ? { ...r, app: e.target.value } : r)))}
                    placeholder="WhatsApp" className="log-input flex-[2] text-sm" />
                  <input type="number" inputMode="numeric" min="0" value={row.minutes}
                    onChange={(e) => setManualApps((prev) =>
                      prev.map((r, j) => (j === i ? { ...r, minutes: e.target.value } : r)))}
                    placeholder="45" className="log-input w-20 text-center text-sm" />
                  <span className="text-xs text-muted-foreground w-6">m</span>
                  {manualApps.length > 1 && (
                    <button onClick={() => setManualApps((prev) => prev.filter((_, j) => j !== i))}
                      aria-label="Remove app"
                      className="h-7 w-7 rounded-lg flex items-center justify-center text-muted-foreground/40 hover:text-red-400 hover:bg-red-500/10 transition-colors">
                      <X size={11} />
                    </button>
                  )}
                </div>
              ))}
            </div>
            <button onClick={() => setManualApps((prev) => [...prev, { app: '', minutes: '' }])}
              className="mt-2 flex w-full items-center justify-center gap-1 rounded-lg border border-dashed
                         border-white/15 py-2 text-[11px] font-semibold text-muted-foreground
                         hover:text-foreground hover:border-white/30 transition-all active:scale-95">
              <Plus size={11} /> Add app
            </button>
          </div>

          <button onClick={saveManual} disabled={saving}
            className="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white
                       hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
            {saving ? 'Saving…' : 'Save screen time'}
          </button>
        </div>
      )}

      {/* Today's screenshot */}
      {todayLog?.screenshot_url && (
        <div className="nafs-card overflow-hidden">
          <div className="px-4 pt-4 pb-2 flex items-center justify-between">
            <p className="font-semibold text-foreground text-sm">Today&apos;s upload</p>
            {todayLog.total_mins > 0 && (
              <p className="text-lg font-bold text-foreground tabular-nums">{fmtTime(todayLog.total_mins)}</p>
            )}
          </div>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={todayLog.screenshot_url} alt="Screen time" className="w-full object-contain max-h-96" />
          {todayLog.ai_summary && (
            <div className="p-4 border-t border-white/10">
              <p className="text-xs font-semibold text-gold mb-1">⚡ AI verdict</p>
              <p className="text-sm text-foreground leading-relaxed">{todayLog.ai_summary}</p>
            </div>
          )}
          {todayLog.apps.length > 0 && (
            <div className="p-4 border-t border-white/10 space-y-2">
              {[...todayLog.apps].sort((a, b) => b.minutes - a.minutes).slice(0, 6).map((app, i) => (
                <div key={i} className="flex items-center gap-2">
                  <span className="text-xs text-muted-foreground w-4">{i + 1}</span>
                  <div className="flex-1">
                    <div className="flex justify-between text-xs mb-0.5">
                      <span className="text-foreground font-medium">{app.app}</span>
                      <span className="text-muted-foreground">{fmtTime(app.minutes)}</span>
                    </div>
                    <div className="h-1.5 w-full rounded-full bg-white/10">
                      <div className={cn('h-full rounded-full', CAT_COLORS[app.category] ?? 'bg-white/30')}
                        style={{ width: `${Math.round((app.minutes / todayLog.total_mins) * 100)}%` }} />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* No data yet */}
      {!todayLog && (
        <div className="text-center py-10">
          <p className="text-5xl">📱</p>
          <p className="mt-3 font-semibold text-foreground">No upload yet today</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Upload your screen time screenshot.<br />
            It saves by date — one per day.
          </p>
        </div>
      )}

      {/* History */}
      {logs.length > 0 && (
        <div className="space-y-2 pb-8">
          <p className="section-header">History</p>
          {logs.map((log, i) => {
            const prev = logs[i + 1]
            const diff = prev?.total_mins ? log.total_mins - prev.total_mins : 0
            const isSelected = selectedLog?.date === log.date
            return (
              <button key={log.id} onClick={() => setSelectedLog(isSelected ? null : log)}
                className={cn('w-full nafs-card p-4 flex items-center gap-3 text-left transition-all',
                  isSelected && 'border-primary/40'
                )}>
                {log.screenshot_url && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={log.screenshot_url} alt="" className="h-12 w-9 rounded-lg object-cover flex-shrink-0" />
                )}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-foreground">
                    {new Date(log.date + 'T12:00:00').toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
                    {log.date === today && <span className="ml-2 text-xs text-gold">Today</span>}
                  </p>
                  {log.apps.length > 0 && (
                    <p className="text-xs text-muted-foreground truncate">
                      {[...log.apps].sort((a, b) => b.minutes - a.minutes).slice(0, 3).map((a) => a.app).join(' · ')}
                    </p>
                  )}
                </div>
                <div className="text-right flex-shrink-0">
                  {log.total_mins > 0 && <p className="font-bold tabular-nums text-foreground">{fmtTime(log.total_mins)}</p>}
                  {diff !== 0 && log.total_mins > 0 && (
                    <p className={cn('text-xs flex items-center justify-end gap-0.5 mt-0.5',
                      diff > 0 ? 'text-red-400' : 'text-emerald-400'
                    )}>
                      {diff > 0 ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
                      {Math.abs(diff)}m
                    </p>
                  )}
                </div>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
