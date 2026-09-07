'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Sparkles, X, Send, Loader2, ExternalLink } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useBodyScrollLock } from '@/hooks/useBodyScrollLock'

/**
 * The coach, reachable from anywhere.
 *
 * A floating button that opens a chat sheet over whatever page you are on,
 * the way a website's support bubble does. It talks to the same /api/ai/chat
 * the Coach page uses — same context, same rules, same sharpened prompt — so
 * an answer here and an answer there cannot disagree.
 *
 * It holds its own short conversation for the session. The full history and
 * the reports live on /coach, which the sheet links to; the bubble hides on
 * that page, where it would only be a second copy.
 *
 * On failure the user's message is never lost: it comes back into the input
 * so it can be sent again.
 */

interface Msg { role: 'user' | 'assistant'; content: string }

export default function CoachBubble() {
  const pathname = usePathname()
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<Msg[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  useBodyScrollLock(open)

  // Keep the newest message in view.
  useEffect(() => {
    if (open) listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages, open, busy])

  useEffect(() => {
    if (open) setTimeout(() => inputRef.current?.focus(), 50)
  }, [open])

  // Redundant on the Coach page itself.
  if (pathname.startsWith('/coach')) return null

  async function send() {
    const text = input.trim()
    if (!text || busy) return

    const next: Msg[] = [...messages, { role: 'user', content: text }]
    setMessages(next)
    setInput('')
    setError(null)
    setBusy(true)

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        // The route rebuilds the data context itself and reads the last
        // message as the question; it keeps only the recent turns.
        body: JSON.stringify({ messages: next.slice(-8) }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || typeof data?.reply !== 'string') {
        throw new Error(data?.error || 'The coach could not answer right now.')
      }
      setMessages([...next, { role: 'assistant', content: data.reply }])
    } catch (e: any) {
      // Give the message back rather than dropping it.
      setMessages(messages)
      setInput(text)
      setError(e?.message || 'Not sent — check your connection.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {/* The bubble. Sits above the floating dock, clear of the centre + button. */}
      {!open && (
        <button
          aria-label="Ask the coach"
          onClick={() => setOpen(true)}
          className="fixed right-4 z-40 flex h-12 w-12 items-center justify-center rounded-full
                     bg-gradient-to-b from-[#1A6B7E] to-[#0F4C5C] text-white
                     ring-2 ring-[#0a1622]
                     shadow-[0_10px_24px_rgba(15,76,92,0.45)]
                     transition-all hover:scale-105 active:scale-95"
          style={{ bottom: 'calc(env(safe-area-inset-bottom, 0px) + 88px)' }}
        >
          <Sparkles size={20} />
        </button>
      )}

      {/* The sheet. Above the dock so the input is never hidden behind it. */}
      {open && (
        <div className="modal-overlay items-end backdrop-blur-sm animate-in fade-in z-[60]"
          onClick={() => setOpen(false)}>
          <div
            className="flex w-full max-w-md flex-col rounded-t-3xl border-t border-white/10
                       bg-gradient-to-b from-[#16314a] via-[#0f2235] to-[#0b1a2b]
                       shadow-[0_-12px_40px_rgba(0,0,0,0.4)] animate-slide-up"
            style={{
              height: 'min(78vh, 640px)',
              paddingBottom: 'max(env(safe-area-inset-bottom), 0.75rem)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex justify-center pt-2.5 pb-1">
              <div className="h-1 w-12 rounded-full bg-white/20" />
            </div>

            {/* Header */}
            <div className="flex items-center justify-between px-5 pt-1 pb-3 flex-shrink-0">
              <div className="flex items-center gap-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gold/10 border border-gold/20">
                  <Sparkles size={15} className="text-gold" />
                </div>
                <div>
                  <p className="text-sm font-bold text-foreground leading-tight">Coach</p>
                  <p className="text-[10px] text-muted-foreground">Answers from your last 30 days</p>
                </div>
              </div>
              <div className="flex items-center gap-1">
                <Link href="/coach" onClick={() => setOpen(false)} aria-label="Open full coach"
                  className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors text-muted-foreground">
                  <ExternalLink size={15} />
                </Link>
                <button onClick={() => setOpen(false)} aria-label="Close"
                  className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center transition-colors">
                  <X size={16} className="text-muted-foreground" />
                </button>
              </div>
            </div>

            {/* Messages */}
            <div ref={listRef} className="flex-1 overflow-y-auto px-4 space-y-2.5 pb-2">
              {messages.length === 0 && !busy && (
                <div className="py-8 text-center">
                  <p className="text-sm font-semibold text-foreground">Ask anything</p>
                  <p className="mt-1 text-xs text-muted-foreground max-w-[260px] mx-auto leading-relaxed">
                    Why did I miss Fajr this week? What should I fix first? Where am I actually improving?
                  </p>
                </div>
              )}
              {messages.map((m, i) => (
                <div key={i} className={cn('flex', m.role === 'user' ? 'justify-end' : 'justify-start')}>
                  <div className={cn(
                    'max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed whitespace-pre-wrap',
                    m.role === 'user'
                      ? 'bg-primary text-white rounded-br-md'
                      : 'bg-white/[0.06] border border-white/10 text-foreground rounded-bl-md',
                  )}>
                    {m.content}
                  </div>
                </div>
              ))}
              {busy && (
                <div className="flex justify-start">
                  <div className="rounded-2xl rounded-bl-md bg-white/[0.06] border border-white/10 px-3.5 py-2.5">
                    <Loader2 size={14} className="animate-spin text-gold" />
                  </div>
                </div>
              )}
              {error && <p className="text-xs text-red-400 px-1">{error}</p>}
            </div>

            {/* Input */}
            <div className="px-4 pt-2 flex-shrink-0">
              <div className="flex items-end gap-2 rounded-2xl border border-white/10 bg-white/5 p-1.5">
                <textarea
                  ref={inputRef}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
                  }}
                  rows={1}
                  placeholder="Message the coach…"
                  className="flex-1 resize-none bg-transparent px-2.5 py-2 text-sm text-foreground
                             placeholder:text-muted-foreground outline-none max-h-28"
                  style={{ minHeight: 38 }}
                />
                <button onClick={send} disabled={!input.trim() || busy} aria-label="Send"
                  className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-xl
                             bg-gold text-[#0b1a2b] transition-all active:scale-95 disabled:opacity-40">
                  {busy ? <Loader2 size={15} className="animate-spin" /> : <Send size={15} />}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
