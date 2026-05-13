'use client'

import { useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { createClient } from '@/lib/supabase/client'
import { cn, scoreColor } from '@/lib/utils'
import {
  Pencil, KeyRound, Share2, BarChart3, Download, Bell, Globe, Moon,
  Info, LogOut, ChevronRight, Check, X, Loader2, Mail, Camera, Trash2,
  UserCircle2, Briefcase, MapPin, Cake, Heart, Send, Award, Lock, AlertTriangle,
} from 'lucide-react'
import { BADGES, TIER_COLORS, badgesByFeature, type BadgeDef, type BadgeFeature } from '@/lib/badges'

interface DayScore { date: string; pct: number; earned: number; max: number }
interface Props {
  profile: any
  dailyScores: DayScore[]
  earnedBadges: Record<string, string>  // badge_id → earned_at ISO
}

export default function ProfileClient({ profile, dailyScores, earnedBadges }: Props) {
  const router = useRouter()
  const supabase = createClient()

  const fileRef = useRef<HTMLInputElement>(null)

  // Defensive name chain — treat known placeholders as empty so a real name wins
  const PLACEHOLDERS_LOWER = new Set(['', 'friend', 'no name', 'user'])
  const emailPrefix = (profile?.email as string | undefined)?.split('@')[0] ?? ''
  const rawProfileName: string = ((profile?.name as string | undefined) ?? '').trim()
  const resolvedName: string =
    PLACEHOLDERS_LOWER.has(rawProfileName.toLowerCase())
      ? (emailPrefix || 'Friend')
      : rawProfileName

  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(resolvedName)
  const [avatarUrl, setAvatarUrl] = useState<string | null>(profile?.avatar_url ?? null)
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState<{ type: 'ok' | 'err'; text: string } | null>(null)
  const [resetSending, setResetSending] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)

  // "About me" sheet state
  const initialAbout = (profile?.about_me ?? {}) as {
    bio?: string; occupation?: string; birth_date?: string; location?: string; interests?: string[]
  }
  const [aboutOpen, setAboutOpen] = useState(false)
  const [aboutBio, setAboutBio] = useState(initialAbout.bio ?? '')
  const [aboutOccupation, setAboutOccupation] = useState(initialAbout.occupation ?? '')
  const [aboutBirthDate, setAboutBirthDate] = useState(initialAbout.birth_date ?? '')
  const [aboutLocation, setAboutLocation] = useState(initialAbout.location ?? '')
  const [aboutInterests, setAboutInterests] = useState((initialAbout.interests ?? []).join(', '))
  const [aboutSaving, setAboutSaving] = useState(false)
  const [aboutMe, setAboutMe] = useState(initialAbout)
  const aboutFilled = !!(aboutMe.bio || aboutMe.occupation || aboutMe.birth_date || aboutMe.location || (aboutMe.interests?.length))

  // Email reports prefs
  const [emailDaily, setEmailDaily]   = useState<boolean>(!!profile?.notify_email_daily)
  const [emailWeekly, setEmailWeekly] = useState<boolean>(!!profile?.notify_email_weekly)
  const [savingPref, setSavingPref]   = useState<string | null>(null)
  const [sendingTest, setSendingTest] = useState<'daily' | 'weekly' | null>(null)

  // Delete account
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteText, setDeleteText] = useState('')
  const [deleting, setDeleting] = useState(false)

  // Badges
  const [badgeOpen, setBadgeOpen] = useState<BadgeDef | null>(null)
  const earnedCount = Object.keys(earnedBadges).length
  const totalCount  = BADGES.length

  async function shareBadge(b: BadgeDef) {
    const text =
      `${b.emoji} I just earned the "${b.name}" badge on NAFS!\n` +
      `${b.description}\n\n` +
      `Tracking my self-accountability journey, one day at a time.`
    try {
      if (typeof navigator !== 'undefined' && (navigator as any).share) {
        await (navigator as any).share({ title: `NAFS — ${b.name}`, text })
      } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(text)
        flash('ok', 'Copied to clipboard')
      }
    } catch (e: any) {
      if (e?.name !== 'AbortError') flash('err', 'Share failed')
    }
  }

  // Stats — computed from the same engagement-aware daily scores as the dashboard
  const validDays = dailyScores.filter((d) => d.max > 0)
  const avgScore   = validDays.length > 0
    ? Math.round(validDays.reduce((s, d) => s + d.pct, 0) / validDays.length) : 0
  const bestScore  = validDays.length > 0 ? Math.max(...validDays.map((d) => d.pct)) : 0
  const daysLogged = validDays.length

  function flash(type: 'ok' | 'err', text: string) {
    setToast({ type, text })
    setTimeout(() => setToast(null), 2500)
  }

  async function saveName() {
    const trimmed = name.trim()
    if (!trimmed) { flash('err', 'Name cannot be empty'); return }
    setSaving(true)
    try {
      const { data: { user }, error: authErr } = await supabase.auth.getUser()
      if (authErr || !user) { flash('err', 'Not signed in'); setSaving(false); return }

      const { data, error } = await supabase
        .from('users')
        .update({ name: trimmed })
        .eq('id', user.id)
        .select()
        .maybeSingle()

      if (error) { flash('err', error.message); setSaving(false); return }
      if (!data)  { flash('err', 'Update was blocked (RLS). Are you signed in?'); setSaving(false); return }

      setSaving(false)
      setEditing(false)
      flash('ok', 'Name updated')
      router.refresh()
    } catch (e: any) {
      setSaving(false)
      flash('err', e?.message || 'Save failed')
    }
  }

  async function onAvatarPicked(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''  // reset so picking the same file again still fires
    if (!file) return
    if (!file.type.startsWith('image/')) { flash('err', 'Pick an image file'); return }
    if (file.size > 5 * 1024 * 1024) { flash('err', 'Max 5MB'); return }

    setUploadingAvatar(true)
    try {
      const { data: { user }, error: authErr } = await supabase.auth.getUser()
      if (authErr || !user) { flash('err', 'Not signed in'); setUploadingAvatar(false); return }

      const ext = (file.name.split('.').pop() || 'jpg').toLowerCase()
      const path = `${user.id}.${ext}`

      const { error: upErr } = await supabase.storage
        .from('avatars')
        .upload(path, file, { upsert: true, contentType: file.type })
      if (upErr) { flash('err', upErr.message); setUploadingAvatar(false); return }

      const { data: pub } = supabase.storage.from('avatars').getPublicUrl(path)
      const finalUrl = `${pub.publicUrl}?v=${Date.now()}`

      const { error: dbErr } = await supabase
        .from('users')
        .update({ avatar_url: finalUrl })
        .eq('id', user.id)
      if (dbErr) { flash('err', dbErr.message); setUploadingAvatar(false); return }

      setAvatarUrl(finalUrl)
      setUploadingAvatar(false)
      flash('ok', 'Profile picture updated')
      router.refresh()
    } catch (e: any) {
      setUploadingAvatar(false)
      flash('err', e?.message || 'Upload failed')
    }
  }

  async function removeAvatar() {
    if (!avatarUrl) return
    if (!confirm('Remove profile picture?')) return
    setUploadingAvatar(true)
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) { setUploadingAvatar(false); return }

      // Best-effort delete from storage (ignore failure)
      try {
        const match = avatarUrl.match(/avatars\/([^?]+)/)
        if (match) await supabase.storage.from('avatars').remove([match[1]])
      } catch {}

      const { error } = await supabase.from('users').update({ avatar_url: null }).eq('id', user.id)
      if (error) { flash('err', error.message); setUploadingAvatar(false); return }
      setAvatarUrl(null)
      setUploadingAvatar(false)
      flash('ok', 'Profile picture removed')
      router.refresh()
    } catch (e: any) {
      setUploadingAvatar(false)
      flash('err', e?.message || 'Remove failed')
    }
  }

  async function resetPassword() {
    if (!profile?.email) return
    setResetSending(true)
    const next = encodeURIComponent('/auth?reset=1')
    const { error } = await supabase.auth.resetPasswordForEmail(profile.email, {
      redirectTo: `${window.location.origin}/auth/callback?next=${next}`,
    })
    setResetSending(false)
    if (error) { flash('err', error.message); return }
    flash('ok', `Reset link sent to ${profile.email}`)
  }

  async function shareProgress() {
    setSharing(true)
    const text =
      `🌙 NAFS — my self-accountability journey\n\n` +
      `Avg score · ${avgScore}%\n` +
      `Best day · ${bestScore}%\n` +
      `Days logged · ${daysLogged}\n\n` +
      `Building consistency, one day at a time.`

    try {
      if (typeof navigator !== 'undefined' && (navigator as any).share) {
        await (navigator as any).share({ title: 'My NAFS progress', text })
        flash('ok', 'Shared')
      } else if (typeof navigator !== 'undefined' && navigator.clipboard) {
        await navigator.clipboard.writeText(text)
        flash('ok', 'Copied to clipboard')
      } else {
        flash('err', 'Sharing not supported on this device')
      }
    } catch (e: any) {
      if (e?.name !== 'AbortError') flash('err', 'Share failed')
    }
    setSharing(false)
  }

  async function exportData() {
    const data = {
      profile: { name: profile.name, email: profile.email },
      daily_scores: dailyScores,
      exported_at: new Date().toISOString(),
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `nafs-export-${new Date().toISOString().split('T')[0]}.json`
    a.click()
    URL.revokeObjectURL(url)
    flash('ok', 'Export downloaded')
  }

  async function signOut() {
    setSigningOut(true)
    await supabase.auth.signOut()
    router.push('/auth')
  }

  async function deleteAccount() {
    if (deleteText !== 'DELETE') return
    setDeleting(true)
    try {
      const res = await fetch('/api/user/delete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ confirm: 'DELETE' }),
      })
      if (!res.ok) {
        const b = await res.json().catch(() => ({}))
        flash('err', b?.error || 'Delete failed')
        setDeleting(false)
        return
      }
      router.push('/auth')
    } catch (e: any) {
      flash('err', e?.message || 'Network error')
      setDeleting(false)
    }
  }

  async function toggleEmailPref(key: 'notify_email_daily' | 'notify_email_weekly', value: boolean) {
    setSavingPref(key)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setSavingPref(null); flash('err', 'Not signed in'); return }
    const { error } = await supabase.from('users').update({ [key]: value }).eq('id', user.id)
    setSavingPref(null)
    if (error) { flash('err', error.message); return }
    if (key === 'notify_email_daily')  setEmailDaily(value)
    if (key === 'notify_email_weekly') setEmailWeekly(value)
    flash('ok', value ? 'Email reports enabled' : 'Email reports disabled')
  }

  async function sendTestReport(which: 'daily' | 'weekly') {
    setSendingTest(which)
    try {
      const res = await fetch(`/api/cron/${which === 'daily' ? 'daily-report' : 'weekly-report'}?test=1`)
      const data = await res.json().catch(() => ({}))
      if (!res.ok) { flash('err', data?.error || 'Failed to send'); return }
      flash('ok', `${which === 'daily' ? 'Daily' : 'Weekly'} test sent to ${profile?.email}`)
    } catch (e: any) {
      flash('err', e?.message || 'Network error')
    }
    setSendingTest(null)
  }

  async function saveAbout() {
    setAboutSaving(true)
    const interests = aboutInterests
      .split(',').map((s) => s.trim()).filter(Boolean).slice(0, 12)
    const payload: Record<string, any> = {
      bio: aboutBio.trim() || undefined,
      occupation: aboutOccupation.trim() || undefined,
      birth_date: aboutBirthDate || undefined,
      location: aboutLocation.trim() || undefined,
      interests: interests.length ? interests : undefined,
    }
    // Strip undefined keys
    const clean = Object.fromEntries(Object.entries(payload).filter(([, v]) => v !== undefined))
    try {
      const { data: { user } } = await supabase.auth.getUser()
      if (!user) { setAboutSaving(false); flash('err', 'Not signed in'); return }
      const { error } = await supabase.from('users')
        .update({ about_me: clean }).eq('id', user.id)
      setAboutSaving(false)
      if (error) { flash('err', error.message); return }
      setAboutMe(clean)
      setAboutOpen(false)
      flash('ok', 'About you updated')
    } catch (e: any) {
      setAboutSaving(false)
      flash('err', e?.message || 'Save failed')
    }
  }

  return (
    <div className="mx-auto max-w-md space-y-5 px-4 pb-8">
      {/* Header */}
      <div className="pt-3">
        <p className="text-xs text-muted-foreground">
          {new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })}
        </p>
        <h1 className="text-2xl font-bold text-foreground">Settings</h1>
      </div>

      {/* Profile hero card */}
      <div className="relative overflow-hidden rounded-3xl border border-white/10
                      bg-gradient-to-br from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                      p-5">
        <div className="pointer-events-none absolute -top-10 -right-10 h-44 w-44 rounded-full bg-gold/10 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-10 -left-10 h-32 w-32 rounded-full bg-primary/20 blur-3xl" />

        <div className="relative flex items-center gap-4">
          {/* Avatar with upload */}
          <div className="relative flex-shrink-0">
            <input ref={fileRef} type="file" accept="image/*"
              className="hidden" onChange={onAvatarPicked} />
            <button
              onClick={() => !uploadingAvatar && fileRef.current?.click()}
              disabled={uploadingAvatar}
              aria-label="Change profile picture"
              className="relative h-16 w-16 rounded-2xl overflow-hidden
                         bg-gradient-to-br from-primary to-teal-light
                         border border-gold/30 flex items-center justify-center
                         text-2xl font-bold text-gold shadow-lg
                         transition-all hover:scale-[1.02] active:scale-95 disabled:opacity-60">
              {avatarUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={avatarUrl} alt="avatar" className="h-full w-full object-cover" />
              ) : (
                <span>{((name || emailPrefix || 'N').trim()[0] ?? 'N').toUpperCase()}</span>
              )}
              {uploadingAvatar && (
                <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
                  <Loader2 size={18} className="animate-spin text-white" />
                </div>
              )}
            </button>
            {/* Camera badge */}
            <div className="absolute -bottom-1 -right-1 h-6 w-6 rounded-full
                            bg-gold border-2 border-[#0f2235] flex items-center justify-center
                            pointer-events-none shadow-md">
              <Camera size={11} className="text-[#0b1a2b] stroke-[2.5]" />
            </div>
          </div>
          <div className="flex-1 min-w-0">
            {editing ? (
              <div className="flex gap-2">
                <input value={name} onChange={(e) => setName(e.target.value)}
                  className="log-input text-base font-bold flex-1"
                  autoFocus
                  onKeyDown={(e) => e.key === 'Enter' && saveName()} />
                <button onClick={saveName} disabled={saving || !name.trim()}
                  className="rounded-xl bg-primary px-3 text-sm font-semibold text-white disabled:opacity-40">
                  {saving ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
                </button>
                <button onClick={() => { setEditing(false); setName(profile?.name ?? '') }}
                  className="rounded-xl border border-white/10 px-2.5 text-muted-foreground">
                  <X size={14} />
                </button>
              </div>
            ) : (
              <>
                <p className="text-lg font-bold text-foreground truncate">{name || emailPrefix || 'Friend'}</p>
                <p className="text-xs text-muted-foreground truncate">{profile?.email}</p>
                {(() => {
                  const raw = profile?.created_at
                  if (!raw) return null
                  const d = new Date(raw)
                  if (isNaN(d.getTime())) return null
                  return (
                    <p className="text-[11px] text-muted-foreground/70 mt-0.5">
                      Member since {d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
                    </p>
                  )
                })()}
              </>
            )}
          </div>
          {!editing && (
            <button onClick={() => setEditing(true)}
              className="h-9 w-9 rounded-xl border border-white/10 bg-white/5 flex items-center justify-center
                         text-muted-foreground hover:bg-white/10 transition-colors">
              <Pencil size={14} />
            </button>
          )}
        </div>

        {/* Quick stats row */}
        <div className="relative grid grid-cols-3 gap-2 mt-4 pt-4 border-t border-white/8">
          <Stat label="Avg" value={`${avgScore}%`} valClass={scoreColor(avgScore)} />
          <Stat label="Best" value={`${bestScore}%`} valClass={scoreColor(bestScore)} />
          <Stat label="Days" value={String(daysLogged)} valClass="text-foreground" />
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <div className={cn(
          'rounded-xl border px-4 py-2.5 text-sm font-medium animate-slide-up',
          toast.type === 'ok' ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                              : 'border-red-500/30 bg-red-500/10 text-red-300'
        )}>
          {toast.text}
        </div>
      )}

      {/* ACCOUNT */}
      <Section title="Account">
        <Row icon={<Camera size={15} />} label="Profile picture"
          subLabel={avatarUrl ? 'Tap to change' : 'Tap to upload a photo'}
          onClick={() => fileRef.current?.click()}
          loading={uploadingAvatar} />
        {avatarUrl && (
          <Row icon={<Trash2 size={15} />} label="Remove picture"
            onClick={removeAvatar} />
        )}
        <Row icon={<Pencil size={15} />} label="Edit name"
          value={name || '—'}
          onClick={() => setEditing(true)} />
        <Row icon={<Mail size={15} />} label="Email"
          value={profile?.email}
          rightDim />
        <Row icon={<KeyRound size={15} />} label="Reset password"
          subLabel="Email yourself a reset link"
          onClick={resetPassword}
          loading={resetSending} />
      </Section>

      {/* ABOUT YOU */}
      <Section title="About you">
        <Row icon={<UserCircle2 size={15} />} label="Tell us more about yourself"
          subLabel={aboutFilled ? 'Bio, occupation, location, interests' : 'Helps the AI personalize your insights'}
          value={aboutFilled ? 'Edit' : undefined}
          onClick={() => setAboutOpen(true)} />
      </Section>

      {/* BADGES */}
      <div>
        <div className="flex items-baseline justify-between mb-2 px-1">
          <p className="section-header flex items-center gap-1.5">
            <Award size={11} className="text-gold" /> Badges
          </p>
          <span className="text-xs tabular-nums text-muted-foreground">
            {earnedCount} / {totalCount} earned
          </span>
        </div>
        <BadgesGrid earned={earnedBadges} onSelect={setBadgeOpen} />
      </div>

      {/* PERFORMANCE */}
      <Section title="Performance & sharing">
        <Row icon={<Share2 size={15} />} label="Share my progress"
          subLabel="Avg, best, and streak summary"
          onClick={shareProgress} loading={sharing} />
        <RowLink icon={<BarChart3 size={15} />} label="View full history"
          subLabel="30-day breakdown by feature"
          href="/history" />
        <Row icon={<Download size={15} />} label="Export my data"
          subLabel="Download a JSON copy of your logs"
          onClick={exportData} />
      </Section>

      {/* NOTIFICATIONS */}
      <Section title="Notifications">
        <ToggleRow icon={<Mail size={15} />} label="Daily email report"
          subLabel="Score + AI verdict + tomorrow's focus, every night"
          value={emailDaily}
          saving={savingPref === 'notify_email_daily'}
          onToggle={(v) => toggleEmailPref('notify_email_daily', v)} />
        {emailDaily && (
          <Row icon={<Send size={15} />} label="Send a test daily report now"
            subLabel={`Goes to ${profile?.email}`}
            onClick={() => sendTestReport('daily')}
            loading={sendingTest === 'daily'} />
        )}
        <ToggleRow icon={<Mail size={15} />} label="Weekly tribunal email"
          subLabel="Full weekly verdict + goal alignment, Sunday 9pm"
          value={emailWeekly}
          saving={savingPref === 'notify_email_weekly'}
          onToggle={(v) => toggleEmailPref('notify_email_weekly', v)} />
        {emailWeekly && (
          <Row icon={<Send size={15} />} label="Send a test weekly report now"
            subLabel={`Goes to ${profile?.email}`}
            onClick={() => sendTestReport('weekly')}
            loading={sendingTest === 'weekly'} />
        )}
        <Row icon={<Bell size={15} />} label="Push notifications"
          value="Coming soon" rightDim />
      </Section>

      {/* PREFERENCES */}
      <Section title="Preferences">
        <Row icon={<Moon size={15} />} label="Theme"     value="Dark"            rightDim />
        <Row icon={<Globe size={15} />} label="Timezone" value={profile?.timezone ?? 'Auto'} rightDim />
      </Section>

      {/* ABOUT */}
      <Section title="About">
        <Row icon={<Info size={15} />} label="Version" value="0.1.0" rightDim />
      </Section>

      {/* Sign out + Delete account */}
      <div className="space-y-2">
        <button onClick={signOut} disabled={signingOut}
          className="w-full rounded-2xl border border-red-500/25 bg-red-500/5 px-4 py-3.5
                     text-sm font-semibold text-red-400 hover:bg-red-500/10 transition-all
                     flex items-center justify-center gap-2 disabled:opacity-50 active:scale-[0.98]">
          <LogOut size={16} />
          {signingOut ? 'Signing out…' : 'Sign out'}
        </button>

        <button onClick={() => { setDeleteOpen(true); setDeleteText('') }}
          className="w-full rounded-2xl border border-red-500/40 bg-red-500/10 px-4 py-3
                     text-xs font-semibold text-red-400 hover:bg-red-500/20 transition-all
                     flex items-center justify-center gap-2 active:scale-[0.98]">
          <Trash2 size={14} />
          Delete account permanently
        </button>
      </div>

      <p className="text-center text-[10px] text-muted-foreground/60 pt-2">
        NAFS · v0.1.0 · built for self-accountability
      </p>

      {/* Delete account confirmation */}
      {deleteOpen && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-5"
          onClick={() => !deleting && setDeleteOpen(false)}>
          <div className="w-full max-w-sm bg-[#0f2235] border border-red-500/40 rounded-3xl p-6"
            onClick={(e) => e.stopPropagation()}>
            <div className="text-center mb-4">
              <div className="mx-auto h-14 w-14 rounded-2xl bg-red-500/15 border border-red-500/40 flex items-center justify-center text-2xl mb-3">
                <AlertTriangle size={26} className="text-red-400" />
              </div>
              <h2 className="text-lg font-bold text-foreground">Delete account?</h2>
              <p className="text-xs text-muted-foreground mt-2 leading-relaxed">
                This permanently deletes your profile, habits, prayers, tasks, challenges, goals, health logs and all history. <span className="text-red-400 font-semibold">This cannot be undone.</span>
              </p>
            </div>

            <label className="text-[10px] uppercase tracking-wider text-muted-foreground mb-1 block">
              Type <span className="font-bold text-red-400">DELETE</span> to confirm
            </label>
            <input type="text" value={deleteText}
              onChange={(e) => setDeleteText(e.target.value)}
              placeholder="DELETE"
              className="log-input text-center font-bold tracking-widest"
              autoFocus />

            <div className="flex gap-2 mt-4">
              <button onClick={() => setDeleteOpen(false)} disabled={deleting}
                className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
                Cancel
              </button>
              <button onClick={deleteAccount} disabled={deleting || deleteText !== 'DELETE'}
                className="flex-1 rounded-xl bg-red-500 py-3 text-sm font-semibold text-white
                           hover:bg-red-600 transition-all disabled:opacity-30 disabled:cursor-not-allowed active:scale-95">
                {deleting ? 'Deleting…' : 'Delete forever'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Badge detail sheet */}
      {badgeOpen && (() => {
        const tone = TIER_COLORS[badgeOpen.tier]
        const earnedAt = earnedBadges[badgeOpen.id]
        const isEarned = !!earnedAt
        return (
          <div className="fixed inset-0 z-50 bg-black/70 flex items-end sm:items-center justify-center"
            onClick={() => setBadgeOpen(null)}>
            <div className="w-full max-w-sm bg-[#0f2235] border-t sm:border border-white/10
                            rounded-t-3xl sm:rounded-3xl p-6 text-center"
              onClick={(e) => e.stopPropagation()}>
              <div className={cn('mx-auto h-24 w-24 rounded-3xl flex items-center justify-center text-5xl ring-2',
                tone.bg, tone.ring, !isEarned && 'opacity-30 grayscale'
              )}>
                {badgeOpen.emoji}
              </div>
              <p className={cn('mt-3 text-[10px] uppercase tracking-widest font-semibold', tone.text)}>
                {tone.label}
              </p>
              <h3 className="mt-1 text-lg font-bold text-foreground">{badgeOpen.name}</h3>
              <p className="mt-1 text-sm text-muted-foreground leading-relaxed">{badgeOpen.description}</p>
              {isEarned ? (
                <p className="mt-3 text-[11px] text-emerald-400">
                  ✓ Earned {new Date(earnedAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
                </p>
              ) : (
                <p className="mt-3 text-[11px] text-muted-foreground/70 flex items-center justify-center gap-1">
                  <Lock size={10} /> Not yet earned
                </p>
              )}
              <div className="flex gap-2 mt-5">
                <button onClick={() => setBadgeOpen(null)}
                  className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
                  Close
                </button>
                {isEarned && (
                  <button onClick={() => shareBadge(badgeOpen)}
                    className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                               hover:bg-teal-light transition-all flex items-center justify-center gap-2">
                    <Share2 size={14} /> Share
                  </button>
                )}
              </div>
            </div>
          </div>
        )
      })()}

      {/* About-you sheet */}
      {aboutOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 flex items-end sm:items-center justify-center"
          onClick={() => setAboutOpen(false)}>
          <div className="w-full max-w-md bg-[#0f2235] border-t sm:border border-white/10
                          rounded-t-3xl sm:rounded-3xl max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}>
            <div className="sticky top-0 bg-[#0f2235] border-b border-white/10 px-5 py-4 flex items-center justify-between">
              <div>
                <p className="font-bold text-foreground">About you</p>
                <p className="text-[11px] text-muted-foreground">Helps the AI personalize what it says</p>
              </div>
              <button onClick={() => setAboutOpen(false)}
                className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center">
                <X size={16} className="text-muted-foreground" />
              </button>
            </div>
            <div className="p-5 space-y-4">
              {/* Bio */}
              <div>
                <label className="section-header mb-1.5 block flex items-center gap-1.5">
                  <UserCircle2 size={11} /> Short bio
                </label>
                <textarea value={aboutBio} onChange={(e) => setAboutBio(e.target.value)}
                  placeholder="Who you are, what you're working on, what matters."
                  rows={3} className="log-input resize-none text-sm" maxLength={500} />
                <p className="text-[10px] text-muted-foreground mt-1 text-right">{aboutBio.length} / 500</p>
              </div>
              {/* Occupation */}
              <div>
                <label className="section-header mb-1.5 block flex items-center gap-1.5">
                  <Briefcase size={11} /> Occupation / role
                </label>
                <input value={aboutOccupation} onChange={(e) => setAboutOccupation(e.target.value)}
                  placeholder="e.g. CS student, software engineer" className="log-input text-sm" />
              </div>
              {/* Birth date */}
              <div>
                <label className="section-header mb-1.5 block flex items-center gap-1.5">
                  <Cake size={11} /> Birth date
                </label>
                <input type="date" value={aboutBirthDate}
                  onChange={(e) => setAboutBirthDate(e.target.value)}
                  max={new Date().toISOString().split('T')[0]}
                  className="log-input text-sm" />
              </div>
              {/* Location */}
              <div>
                <label className="section-header mb-1.5 block flex items-center gap-1.5">
                  <MapPin size={11} /> Location
                </label>
                <input value={aboutLocation} onChange={(e) => setAboutLocation(e.target.value)}
                  placeholder="e.g. Karachi, Pakistan" className="log-input text-sm" />
              </div>
              {/* Interests */}
              <div>
                <label className="section-header mb-1.5 block flex items-center gap-1.5">
                  <Heart size={11} /> Interests
                </label>
                <input value={aboutInterests} onChange={(e) => setAboutInterests(e.target.value)}
                  placeholder="e.g. coding, fitness, calligraphy"
                  className="log-input text-sm" />
                <p className="text-[10px] text-muted-foreground mt-1">Comma-separated. Max 12.</p>
              </div>

              <div className="flex gap-2 pt-2">
                <button onClick={() => setAboutOpen(false)}
                  className="flex-1 rounded-xl border border-white/10 py-3 text-sm text-muted-foreground hover:bg-white/5">
                  Cancel
                </button>
                <button onClick={saveAbout} disabled={aboutSaving}
                  className="flex-[2] rounded-xl bg-primary py-3 text-sm font-semibold text-white
                             hover:bg-teal-light transition-all disabled:opacity-40 active:scale-95">
                  {aboutSaving ? 'Saving…' : 'Save'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ============================================================
// Building blocks
// ============================================================
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="section-header mb-2">{title}</p>
      <div className="nafs-card divide-y divide-white/5 overflow-hidden">
        {children}
      </div>
    </div>
  )
}

function Stat({ label, value, valClass }: { label: string; value: string; valClass: string }) {
  return (
    <div className="text-center">
      <p className={cn('text-lg font-bold tabular-nums', valClass)}>{value}</p>
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{label}</p>
    </div>
  )
}

function RowBase({
  icon, label, subLabel, right, onClick, dim, asLink,
}: {
  icon: React.ReactNode
  label: string
  subLabel?: string
  right?: React.ReactNode
  onClick?: () => void
  dim?: boolean
  asLink?: boolean
}) {
  return (
    <div
      onClick={onClick}
      className={cn(
        'flex items-center gap-3 px-4 py-3.5 transition-colors',
        onClick && !dim ? 'hover:bg-white/5 active:bg-white/10 cursor-pointer' : '',
      )}
    >
      <div className="h-8 w-8 rounded-xl bg-white/5 border border-white/8 flex items-center justify-center text-muted-foreground flex-shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground leading-tight">{label}</p>
        {subLabel && <p className="text-[11px] text-muted-foreground/80 mt-0.5">{subLabel}</p>}
      </div>
      <div className="flex items-center gap-2 flex-shrink-0">
        {right}
        {!dim && (onClick || asLink) && <ChevronRight size={14} className="text-muted-foreground/60" />}
      </div>
    </div>
  )
}

function Row({
  icon, label, subLabel, value, onClick, rightDim, loading,
}: {
  icon: React.ReactNode
  label: string
  subLabel?: string
  value?: string
  onClick?: () => void
  rightDim?: boolean
  loading?: boolean
}) {
  return (
    <RowBase
      icon={icon}
      label={label}
      subLabel={subLabel}
      onClick={onClick}
      dim={rightDim}
      right={
        loading ? <Loader2 size={14} className="animate-spin text-muted-foreground" />
        : value ? <span className="text-xs text-muted-foreground tabular-nums truncate max-w-[180px]">{value}</span>
        : null
      }
    />
  )
}

function RowLink({
  icon, label, subLabel, value, href,
}: {
  icon: React.ReactNode
  label: string
  subLabel?: string
  value?: string
  href: string
}) {
  return (
    <Link href={href} className="block">
      <RowBase icon={icon} label={label} subLabel={subLabel}
        right={value ? <span className="text-xs text-muted-foreground">{value}</span> : null}
        asLink />
    </Link>
  )
}

function BadgesGrid({ earned, onSelect }: {
  earned: Record<string, string>
  onSelect: (b: BadgeDef) => void
}) {
  const groups = badgesByFeature()
  const FEATURE_LABEL: Record<BadgeFeature, { emoji: string; label: string }> = {
    overall:    { emoji: '✨', label: 'Overall' },
    deen:       { emoji: '🕌', label: 'Deen' },
    habits:     { emoji: '🔄', label: 'Habits' },
    tasks:      { emoji: '✅', label: 'Tasks' },
    challenges: { emoji: '🎯', label: 'Challenges' },
    health:     { emoji: '❤️', label: 'Health' },
    goals:      { emoji: '🏆', label: 'Goals' },
  }
  const order: BadgeFeature[] = ['overall', 'deen', 'habits', 'tasks', 'challenges', 'health', 'goals']

  return (
    <div className="space-y-3">
      {order.map((f) => {
        const list = groups[f]
        if (list.length === 0) return null
        const earnedInGroup = list.filter((b) => earned[b.id]).length
        return (
          <div key={f} className="nafs-card p-3">
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-foreground">
                {FEATURE_LABEL[f].emoji} {FEATURE_LABEL[f].label}
              </p>
              <span className="text-[10px] tabular-nums text-muted-foreground">
                {earnedInGroup} / {list.length}
              </span>
            </div>
            <div className="grid grid-cols-4 gap-2">
              {list.map((b) => {
                const isEarned = !!earned[b.id]
                const tone = TIER_COLORS[b.tier]
                return (
                  <button key={b.id} onClick={() => onSelect(b)}
                    className={cn(
                      'aspect-square rounded-xl border flex flex-col items-center justify-center gap-0.5 transition-all active:scale-90',
                      isEarned
                        ? `${tone.bg} ${tone.ring} ring-2 hover:brightness-125`
                        : 'border-white/10 bg-white/3 opacity-30 grayscale hover:opacity-50'
                    )}
                    title={`${b.name}${isEarned ? '' : ' (locked)'}`}>
                    <span className="text-2xl leading-none">{b.emoji}</span>
                    <span className={cn('text-[8px] uppercase tracking-wider font-bold leading-none',
                      isEarned ? tone.text : 'text-muted-foreground'
                    )}>{tone.label}</span>
                  </button>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}

function ToggleRow({
  icon, label, subLabel, value, saving, onToggle,
}: {
  icon: React.ReactNode
  label: string
  subLabel?: string
  value: boolean
  saving: boolean
  onToggle: (next: boolean) => void
}) {
  return (
    <button onClick={() => !saving && onToggle(!value)} disabled={saving}
      className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-white/5 transition-colors text-left disabled:opacity-60">
      <div className="h-8 w-8 rounded-xl bg-white/5 border border-white/8 flex items-center justify-center text-muted-foreground flex-shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-foreground leading-tight">{label}</p>
        {subLabel && <p className="text-[11px] text-muted-foreground/80 mt-0.5">{subLabel}</p>}
      </div>
      <div className={cn('relative h-5 w-9 rounded-full transition-colors flex-shrink-0',
        value ? 'bg-gold' : 'bg-white/15'
      )}>
        {saving ? (
          <Loader2 size={11} className="animate-spin absolute inset-0 m-auto text-white" />
        ) : (
          <div className={cn('h-5 w-5 rounded-full bg-white transition-transform shadow',
            value && 'translate-x-4'
          )} />
        )}
      </div>
    </button>
  )
}
