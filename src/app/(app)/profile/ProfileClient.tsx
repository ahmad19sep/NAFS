'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { scoreColor, daysUntil } from '@/lib/utils'
import { LogOut, Download, Save, Edit2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface Props {
  profile: any
  logs: { date: string; identity_score: number; weighted_hours_today: number; todays_pull_days: number }[]
}

export default function ProfileClient({ profile, logs }: Props) {
  const router = useRouter()
  const supabase = createClient()

  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(profile?.name ?? '')
  const [saving, setSaving] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const [saved, setSaved] = useState(false)

  const dream = profile?.dreams

  const avgScore = logs.length > 0
    ? Math.round(logs.reduce((s, l) => s + l.identity_score, 0) / logs.length)
    : 0
  const totalWeightedHours = logs.reduce((s, l) => s + (l.weighted_hours_today ?? 0), 0)
  const daysLogged = logs.length
  const bestScore = logs.reduce((max, l) => Math.max(max, l.identity_score), 0)
  const last7 = logs.slice(0, 7)
  const trend7 = last7.length > 0
    ? Math.round(last7.reduce((s, l) => s + l.identity_score, 0) / last7.length)
    : 0

  async function saveProfile() {
    if (!name.trim()) return
    setSaving(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    await supabase.from('users').update({ name: name.trim() }).eq('id', user.id)

    setSaving(false)
    setEditing(false)
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
    router.refresh()
  }

  async function signOut() {
    setSigningOut(true)
    await supabase.auth.signOut()
    router.push('/auth')
  }

  async function exportData() {
    const data = {
      profile: { name: profile.name, email: profile.email },
      dream: dream ? { statement: dream.statement, deadline: dream.dream_date } : null,
      logs,
      exported_at: new Date().toISOString(),
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `nafs-export-${new Date().toISOString().split('T')[0]}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="mx-auto max-w-md space-y-5 px-4">
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <h1 className="text-2xl font-bold text-foreground">Profile</h1>
      </div>

      {/* Profile card */}
      <div className="nafs-card p-5">
        <div className="flex items-center gap-4">
          {/* Avatar */}
          <div className="h-16 w-16 rounded-full bg-primary/30 border-2 border-primary flex-shrink-0 flex items-center justify-center text-2xl font-bold text-gold">
            {name[0]?.toUpperCase() ?? 'A'}
          </div>

          {/* Name + email */}
          <div className="flex-1 min-w-0">
            {editing ? (
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && saveProfile()}
                className="log-input text-lg font-bold w-full"
                autoFocus
              />
            ) : (
              <p className="text-xl font-bold text-foreground truncate">{name}</p>
            )}
            <p className="text-sm text-muted-foreground mt-0.5 truncate">{profile?.email}</p>
            <p className="text-xs text-muted-foreground mt-0.5">
              Member since {new Date(profile?.created_at).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
            </p>
          </div>

          {/* Edit / Save button */}
          {editing ? (
            <button onClick={saveProfile} disabled={saving || !name.trim()}
              className="flex items-center gap-1.5 rounded-xl bg-primary px-3 py-2 text-sm font-semibold text-white
                         disabled:opacity-40 hover:bg-teal-light transition-all active:scale-95">
              <Save size={14} />
              {saving ? 'Saving…' : 'Save'}
            </button>
          ) : (
            <button onClick={() => setEditing(true)}
              className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3 py-2
                         text-sm text-muted-foreground hover:bg-white/10 transition-all active:scale-95">
              <Edit2 size={14} />
              Edit
            </button>
          )}
        </div>

        {saved && (
          <p className="mt-3 text-center text-sm text-emerald-400">✓ Profile updated</p>
        )}
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 gap-3">
        <div className="nafs-card p-4 text-center">
          <p className={`text-3xl font-bold tabular-nums ${scoreColor(avgScore)}`}>{avgScore}%</p>
          <p className="text-xs text-muted-foreground mt-1">Avg score</p>
        </div>
        <div className="nafs-card p-4 text-center">
          <p className="text-3xl font-bold tabular-nums text-foreground">{daysLogged}</p>
          <p className="text-xs text-muted-foreground mt-1">Days logged</p>
        </div>
        <div className="nafs-card p-4 text-center">
          <p className="text-3xl font-bold tabular-nums text-gold">{Math.round(totalWeightedHours)}</p>
          <p className="text-xs text-muted-foreground mt-1">Weighted hrs</p>
        </div>
        <div className="nafs-card p-4 text-center">
          <p className={`text-3xl font-bold tabular-nums ${scoreColor(bestScore)}`}>{bestScore}%</p>
          <p className="text-xs text-muted-foreground mt-1">Best day</p>
        </div>
      </div>

      {/* 7-day trend */}
      <div className="nafs-card p-4 flex items-center justify-between">
        <div>
          <p className="text-sm font-semibold text-foreground">7-day trend</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            {trend7 >= avgScore ? '↑ Above all-time average' : '↓ Below all-time average'}
          </p>
        </div>
        <p className={`text-3xl font-bold tabular-nums ${scoreColor(trend7)}`}>{trend7}%</p>
      </div>

      {/* Goal summary */}
      {dream && (
        <div className="nafs-card p-4 space-y-1">
          <p className="section-header mb-2">Active dream</p>
          <p className="text-sm text-foreground leading-relaxed">{dream.statement}</p>
          {dream.dream_date && (
            <p className="text-xs text-muted-foreground">
              📅 Deadline: {new Date(dream.dream_date + 'T12:00:00').toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
              {' '}· {daysUntil(dream.dream_date)} days left
            </p>
          )}
        </div>
      )}

      {/* Actions */}
      <div className="space-y-3 pb-8">
        <button onClick={exportData}
          className="flex w-full items-center gap-3 rounded-xl border border-white/10 bg-white/5
                     px-4 py-4 text-sm font-medium text-foreground hover:bg-white/10 transition-all active:scale-95">
          <Download size={18} />
          Export my data
        </button>
        <button onClick={signOut} disabled={signingOut}
          className="flex w-full items-center gap-3 rounded-xl border border-red-500/20 bg-red-500/5
                     px-4 py-4 text-sm font-medium text-red-400 hover:bg-red-500/10
                     transition-all disabled:opacity-50 active:scale-95">
          <LogOut size={18} />
          {signingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </div>
  )
}
