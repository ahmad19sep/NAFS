'use client'

export const dynamic = 'force-dynamic'

import { useState, useRef, useEffect } from 'react'
import { createClient } from '@/lib/supabase/client'
import { Upload, TrendingUp, TrendingDown } from 'lucide-react'
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

    // 2. Try AI vision silently — if it fails, save without data
    let aiData: { total_mins: number; apps: AppUsage[]; summary: string } | null = null
    try {
      const base64 = await toBase64(file)
      const res = await fetch('/api/screentime/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ base64, mimeType: file.type || 'image/jpeg' }),
      })
      if (res.ok) {
        const r = await res.json()
        if (!r.error && r.apps?.length > 0) aiData = r
      }
    } catch {}

    // 3. Save to DB (always — even without AI data)
    await supabase.from('screentime_logs').upsert({
      user_id: user.id,
      date: today,
      total_mins: aiData?.total_mins ?? 0,
      apps: aiData?.apps ?? [],
      screenshot_url: screenshotUrl,
      ai_summary: aiData?.summary ?? null,
    }, { onConflict: 'user_id,date' })

    await load()
    setSaving(false)
  }

  function toBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const r = new FileReader()
      r.onload = () => resolve((r.result as string).split(',')[1])
      r.onerror = reject
      r.readAsDataURL(file)
    })
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

      {/* Upload */}
      <button onClick={() => fileRef.current?.click()} disabled={saving}
        className="flex w-full items-center gap-4 rounded-2xl border-2 border-dashed
                   border-primary/40 bg-primary/5 p-5 transition-all
                   hover:border-primary hover:bg-primary/10 active:scale-95 disabled:opacity-60">
        <div className="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-xl bg-primary/20">
          {saving
            ? <div className="h-5 w-5 rounded-full border-2 border-primary border-t-transparent animate-spin" />
            : <Upload size={22} className="text-primary" />}
        </div>
        <div className="text-left">
          <p className="font-semibold text-foreground">
            {saving ? 'Saving…' : todayLog ? "Update today's screenshot" : "Upload today's screenshot"}
          </p>
          <p className="text-xs text-muted-foreground mt-0.5">iPhone: Settings → Screen Time → screenshot</p>
          <p className="text-xs text-muted-foreground">Android: Digital Wellbeing → screenshot</p>
        </div>
      </button>
      <input ref={fileRef} type="file" accept="image/*" onChange={handleUpload} className="hidden" />

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
