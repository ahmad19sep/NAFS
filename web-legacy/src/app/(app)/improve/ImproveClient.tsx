'use client'

import { useMemo, useState } from 'react'
import Link from 'next/link'
import {
  ChevronLeft, Bug, Lightbulb, Plus, Check, Trash2, Copy, Loader2,
  Undo2, ClipboardCheck,
} from 'lucide-react'
import { createClient } from '@/lib/supabase/client'
import { cn } from '@/lib/utils'
import {
  formatNotesForHandoff, ageLabel, KIND_LABEL,
  type AppNote, type NoteKind, type NoteStatus,
} from '@/lib/app-notes'

/**
 * Bugs and ideas about the app itself.
 *
 * Written in the moment it happens, read when the next update is planned.
 * The list is optimistic on every change — a capture tool that makes you wait
 * is a capture tool you stop using — and rolls back if the write fails, so it
 * never shows something that was not saved.
 */

interface Props {
  userId: string
  initialNotes: AppNote[]
}

const KIND_STYLE: Record<NoteKind, string> = {
  bug: 'border-red-400/40 bg-red-500/10 text-red-300',
  idea: 'border-gold/40 bg-gold/10 text-gold',
}

export default function ImproveClient({ userId, initialNotes }: Props) {
  const supabase = createClient()
  const [notes, setNotes] = useState<AppNote[]>(initialNotes)
  const [tab, setTab] = useState<'open' | 'done'>('open')

  const [kind, setKind] = useState<NoteKind>('bug')
  const [title, setTitle] = useState('')
  const [detail, setDetail] = useState('')
  const [where, setWhere] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const open = useMemo(() => notes.filter((n) => n.status === 'open'), [notes])
  const closed = useMemo(() => notes.filter((n) => n.status !== 'open'), [notes])
  const shown = tab === 'open' ? open : closed
  const bugCount = open.filter((n) => n.kind === 'bug').length

  async function add() {
    const t = title.trim()
    if (!t || saving) return
    setSaving(true); setError(null)
    try {
      const { data, error: err } = await supabase
        .from('app_notes')
        .insert({
          user_id: userId,
          kind,
          title: t.slice(0, 200),
          detail: detail.trim() ? detail.trim().slice(0, 2000) : null,
          where_seen: where.trim() ? where.trim().slice(0, 80) : null,
        })
        .select('id, kind, title, detail, where_seen, status, created_at, resolved_at')
        .single()
      if (err) throw new Error(err.message)
      setNotes((p) => [data as AppNote, ...p])
      setTitle(''); setDetail(''); setWhere('')
      setTab('open')
    } catch (e: any) {
      setError(
        /relation .* does not exist/i.test(e?.message ?? '')
          ? 'The notes table is missing — run supabase/app_notes.sql once.'
          : (e?.message || 'Could not save that.')
      )
    } finally {
      setSaving(false)
    }
  }

  async function setStatus(note: AppNote, status: NoteStatus) {
    const before = notes
    const resolved_at = status === 'open' ? null : new Date().toISOString()
    setNotes((p) => p.map((n) => (n.id === note.id ? { ...n, status, resolved_at } : n)))
    const { error: err } = await supabase
      .from('app_notes').update({ status, resolved_at }).eq('id', note.id)
    if (err) { setNotes(before); setError('Could not update that.') }
  }

  async function remove(note: AppNote) {
    const before = notes
    setNotes((p) => p.filter((n) => n.id !== note.id))
    const { error: err } = await supabase.from('app_notes').delete().eq('id', note.id)
    if (err) { setNotes(before); setError('Could not delete that.') }
  }

  async function copyAll() {
    const text = formatNotesForHandoff(open)
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2500)
    } catch {
      setError('Could not reach the clipboard. Select the text and copy it by hand.')
    }
  }

  return (
    <div className="mx-auto max-w-md space-y-4 px-4 pb-32">
      {/* Header */}
      <div className="flex items-center gap-3 pt-3">
        <Link href="/profile"
          className="flex h-9 w-9 items-center justify-center rounded-xl border border-white/10 bg-white/5 transition-colors hover:bg-white/10">
          <ChevronLeft size={16} className="text-muted-foreground" />
        </Link>
        <div className="flex-1">
          <p className="text-xs text-muted-foreground">Make the app better</p>
          <h1 className="text-2xl font-bold text-foreground">Bugs &amp; ideas</h1>
        </div>
        <Lightbulb size={20} className="text-gold" />
      </div>

      {/* Add */}
      <div className="nafs-card p-4">
        <div className="grid grid-cols-2 gap-1 rounded-xl border border-white/10 bg-white/5 p-1">
          {(['bug', 'idea'] as NoteKind[]).map((k) => (
            <button key={k} onClick={() => setKind(k)}
              className={cn('flex items-center justify-center gap-1.5 rounded-lg py-2 text-xs font-semibold transition-all',
                kind === k ? 'bg-gold/15 text-gold' : 'text-muted-foreground hover:text-foreground')}>
              {k === 'bug' ? <Bug size={13} /> : <Lightbulb size={13} />}
              {k === 'bug' ? 'Something broke' : 'An idea'}
            </button>
          ))}
        </div>

        <input value={title} onChange={(e) => setTitle(e.target.value)}
          placeholder={kind === 'bug' ? 'What went wrong?' : 'What would make it better?'}
          className="log-input mt-3 w-full text-sm"
          onKeyDown={(e) => { if (e.key === 'Enter') add() }} />

        <input value={where} onChange={(e) => setWhere(e.target.value)}
          placeholder="Where? Home, Health, Coach… (optional)"
          className="log-input mt-2 w-full text-sm" />

        <textarea value={detail} onChange={(e) => setDetail(e.target.value)} rows={2}
          placeholder={kind === 'bug'
            ? 'What were you doing when it happened? (optional)'
            : 'Why would it help? (optional)'}
          className="log-input mt-2 w-full resize-none text-sm" />

        {error && <p className="mt-2 text-xs text-red-400">{error}</p>}

        <button onClick={add} disabled={!title.trim() || saving}
          className="btn-gold mt-3 flex w-full items-center justify-center gap-2 py-2.5 text-sm disabled:opacity-40">
          {saving ? <Loader2 size={15} className="animate-spin" /> : <Plus size={15} />}
          Add to the list
        </button>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-2 gap-2">
        {([['open', `Open (${open.length})`], ['done', `Closed (${closed.length})`]] as const).map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={cn('rounded-xl border py-2 text-sm font-semibold transition-all',
              tab === k ? 'border-gold/50 bg-gold/10 text-gold' : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20')}>
            {label}
          </button>
        ))}
      </div>

      {/* Hand-off */}
      {tab === 'open' && open.length > 0 && (
        <button onClick={copyAll}
          className="flex w-full items-center justify-center gap-2 rounded-xl border border-white/10 bg-white/5
                     py-2.5 text-xs font-semibold text-foreground transition-colors hover:bg-white/10">
          {copied ? <><ClipboardCheck size={14} className="text-emerald-400" /> Copied — paste it into the chat</>
            : <><Copy size={14} /> Copy all {open.length} for the next update</>}
        </button>
      )}

      {/* List */}
      {shown.length === 0 ? (
        <div className="nafs-card p-8 text-center">
          <p className="mb-3 text-4xl">{tab === 'open' ? '🐛' : '✅'}</p>
          <p className="font-semibold text-foreground">
            {tab === 'open' ? 'Nothing on the list' : 'Nothing closed yet'}
          </p>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            {tab === 'open'
              ? 'When something breaks, or an idea arrives while you are using the app, write it here before you forget it.'
              : 'Items you tick off or dismiss end up here.'}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {shown.map((n) => (
            <div key={n.id}
              className={cn('rounded-2xl border border-white/10 bg-white/[0.03] p-3',
                n.status !== 'open' && 'opacity-60')}>
              <div className="flex items-start gap-2">
                <span className={cn('mt-0.5 flex-shrink-0 rounded-full border px-2 py-0.5 text-[9px] font-bold uppercase tracking-wider',
                  KIND_STYLE[n.kind])}>
                  {KIND_LABEL[n.kind]}
                </span>
                <div className="min-w-0 flex-1">
                  <p className={cn('text-sm font-semibold text-foreground',
                    n.status === 'done' && 'line-through decoration-white/30')}>
                    {n.title}
                  </p>
                  {n.detail && (
                    <p className="mt-1 whitespace-pre-wrap text-xs leading-relaxed text-muted-foreground">{n.detail}</p>
                  )}
                  <p className="mt-1 text-[10px] text-muted-foreground">
                    {n.where_seen ? `${n.where_seen} · ` : ''}added {ageLabel(n.created_at)}
                    {n.status === 'dismissed' ? ' · dismissed' : ''}
                  </p>
                </div>
              </div>

              <div className="mt-2 flex items-center gap-2 border-t border-white/5 pt-2">
                {n.status === 'open' ? (
                  <>
                    <button onClick={() => setStatus(n, 'done')}
                      className="flex items-center gap-1 rounded-lg bg-emerald-500/15 px-2.5 py-1 text-[11px] font-semibold text-emerald-300 transition-all active:scale-95">
                      <Check size={11} /> Done
                    </button>
                    <button onClick={() => setStatus(n, 'dismissed')}
                      className="rounded-lg px-2.5 py-1 text-[11px] font-semibold text-muted-foreground transition-colors hover:text-foreground">
                      Not doing it
                    </button>
                  </>
                ) : (
                  <button onClick={() => setStatus(n, 'open')}
                    className="flex items-center gap-1 rounded-lg px-2.5 py-1 text-[11px] font-semibold text-muted-foreground transition-colors hover:text-foreground">
                    <Undo2 size={11} /> Reopen
                  </button>
                )}
                <button onClick={() => remove(n)} aria-label="Delete"
                  className="ml-auto rounded-lg p-1.5 text-muted-foreground/60 transition-colors hover:text-red-400">
                  <Trash2 size={13} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'open' && open.length > 0 && (
        <p className="text-center text-[10px] leading-relaxed text-muted-foreground">
          {bugCount > 0
            ? `${bugCount} bug${bugCount === 1 ? '' : 's'} on the list. `
            : ''}
          When the next update comes round, copy the list and paste it into the chat.
        </p>
      )}
    </div>
  )
}
