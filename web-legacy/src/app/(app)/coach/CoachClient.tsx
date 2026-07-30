'use client'

import { useState, useRef, useEffect } from 'react'
import { Send, Zap, Mail, FileText } from 'lucide-react'
import { timeAgo } from '@/lib/utils'

interface Report {
  id: string
  type: string
  content_md: string
  generated_at: string
  week_start: string | null
}

interface Letter {
  id: string
  content: string
  written_at: string
  target_deliver_date: string
  delivered_at: string | null
  ai_reply_text: string | null
}

interface Message {
  role: 'user' | 'assistant'
  content: string
}

interface Props {
  userId: string
  reports: Report[]
  letters: Letter[]
  lastConversation: any
}

export default function CoachClient({ userId, reports, letters, lastConversation }: Props) {
  const [activeTab, setActiveTab] = useState<'pull' | 'tribunal' | 'chat' | 'letters'>('pull')
  const [messages, setMessages] = useState<Message[]>(
    lastConversation?.messages ?? []
  )
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [selectedReport, setSelectedReport] = useState<Report | null>(null)
  const chatEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const pullReports = reports.filter((r) => r.type === 'pull')
  const tribunalReports = reports.filter((r) => r.type === 'tribunal')
  const latestPull = pullReports[0]
  const latestTribunal = tribunalReports[0]

  async function sendMessage() {
    if (!input.trim() || sending) return
    const userMessage = input.trim()
    setInput('')
    setSending(true)

    const newMessages: Message[] = [...messages, { role: 'user', content: userMessage }]
    setMessages(newMessages)

    try {
      const res = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ messages: newMessages, userId }),
      })
      const data = await res.json().catch(() => ({} as any))

      if (res.ok && data.reply) {
        setMessages([...newMessages, { role: 'assistant', content: data.reply }])
      } else {
        // Surface server-side error so the user sees it instead of dead silence
        const errMsg =
          data?.error ??
          (res.status === 429 ? 'AI is rate-limited. Try again in a minute.'
            : `Request failed (${res.status})`)
        setMessages([...newMessages, {
          role: 'assistant',
          content: `⚠️ ${errMsg}${data?.hint ? `\n\n${data.hint}` : ''}`,
        }])
      }
    } catch (err: any) {
      setMessages([...newMessages, {
        role: 'assistant',
        content: `⚠️ Network error: ${err?.message ?? 'Could not reach the server.'}`,
      }])
    }
    setSending(false)
  }

  const TABS = [
    { key: 'pull', label: "Today's Pull", icon: Zap },
    { key: 'tribunal', label: 'Tribunal', icon: FileText },
    { key: 'chat', label: 'Ask NAFS', icon: Send },
    { key: 'letters', label: 'Letters', icon: Mail },
  ] as const

  return (
    <div className="mx-auto max-w-md px-4">
      <div className="pt-2 mb-4">
        <h1 className="text-2xl font-bold text-foreground">AI Coach</h1>
        <p className="mt-1 text-sm text-muted-foreground">Your data. Your verdict.</p>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-4 gap-1 rounded-2xl border border-white/10 bg-white/5 p-1 mb-6">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`flex flex-col items-center gap-1 rounded-xl py-2.5 text-xs font-medium transition-all
              ${activeTab === key
                ? 'bg-primary text-white shadow-sm'
                : 'text-muted-foreground hover:text-foreground'}`}
          >
            <Icon size={14} />
            {label.split(' ')[0]}
          </button>
        ))}
      </div>

      {/* Today's Pull */}
      {activeTab === 'pull' && (
        <div className="space-y-4">
          {latestPull ? (
            <div className="nafs-card p-5">
              <div className="flex items-center justify-between mb-3">
                <span className="text-sm font-semibold text-gold">⚡ Today&apos;s verdict</span>
                <span className="text-xs text-muted-foreground">{timeAgo(latestPull.generated_at)}</span>
              </div>
              <div className="text-foreground leading-relaxed whitespace-pre-wrap text-sm">
                {latestPull.content_md}
              </div>
            </div>
          ) : (
            <div className="text-center py-12">
              <p className="text-4xl">⚡</p>
              <p className="mt-3 font-semibold text-foreground">No verdict yet today</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Log your day to get your Today&apos;s Pull verdict.
              </p>
            </div>
          )}

          {pullReports.slice(1, 6).map((r) => (
            <div key={r.id} className="nafs-card p-4">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground">{timeAgo(r.generated_at)}</span>
              </div>
              <p className="text-sm text-muted-foreground line-clamp-3">{r.content_md}</p>
            </div>
          ))}
        </div>
      )}

      {/* Tribunal */}
      {activeTab === 'tribunal' && (
        <div className="space-y-4">
          {selectedReport ? (
            <div>
              <button
                onClick={() => setSelectedReport(null)}
                className="text-sm text-muted-foreground mb-4 flex items-center gap-1"
              >
                ← Back to tribunal archive
              </button>
              <div className="nafs-card p-5">
                <div className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                  {selectedReport.content_md}
                </div>
              </div>
            </div>
          ) : (
            <>
              {tribunalReports.length === 0 ? (
                <div className="text-center py-12">
                  <p className="text-4xl">⚖️</p>
                  <p className="mt-3 font-semibold text-foreground">No tribunal yet</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    The Weekly Tribunal runs every Sunday at 8 PM.
                  </p>
                </div>
              ) : (
                tribunalReports.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => setSelectedReport(r)}
                    className="w-full nafs-card p-5 text-left hover:border-gold/30 transition-all active:scale-98"
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-semibold text-gold">⚖️ Weekly Tribunal</span>
                      <span className="text-xs text-muted-foreground">{timeAgo(r.generated_at)}</span>
                    </div>
                    <p className="text-sm text-muted-foreground line-clamp-4 leading-relaxed">
                      {r.content_md.slice(0, 250)}…
                    </p>
                  </button>
                ))
              )}
            </>
          )}
        </div>
      )}

      {/* Ask NAFS Chat */}
      {activeTab === 'chat' && (
        <div className="flex flex-col" style={{ height: 'calc(100vh - 280px)' }}>
          <div className="flex-1 overflow-y-auto space-y-4 pb-4 scrollbar-hide">
            {messages.length === 0 && (
              <div className="text-center py-8">
                <p className="text-3xl">🤖</p>
                <p className="mt-3 font-semibold text-foreground">Ask NAFS anything</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  I have access to your last 90 days of data.
                </p>
                <div className="mt-4 space-y-2">
                  {[
                    'Why am I not improving?',
                    'What is my best day pattern?',
                    'How far am I from my dream?',
                  ].map((q) => (
                    <button
                      key={q}
                      onClick={() => setInput(q)}
                      className="block w-full rounded-xl border border-white/10 bg-white/5
                                 px-4 py-2.5 text-sm text-left text-foreground hover:bg-white/10 transition-all"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((m, i) => (
              <div key={i} className={`flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed
                  ${m.role === 'user'
                    ? 'bg-primary text-white rounded-br-sm'
                    : 'bg-white/10 border border-white/10 text-foreground rounded-bl-sm'}`}>
                  {m.content}
                </div>
              </div>
            ))}

            {sending && (
              <div className="flex justify-start">
                <div className="rounded-2xl rounded-bl-sm bg-white/10 border border-white/10 px-4 py-3">
                  <div className="flex gap-1">
                    {[0, 1, 2].map((i) => (
                      <div key={i} className="h-2 w-2 rounded-full bg-muted-foreground animate-bounce"
                        style={{ animationDelay: `${i * 150}ms` }} />
                    ))}
                  </div>
                </div>
              </div>
            )}
            <div ref={chatEndRef} />
          </div>

          <div className="flex gap-2 pb-4">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
              placeholder="Ask about your data…"
              className="log-input flex-1"
            />
            <button
              onClick={sendMessage}
              disabled={!input.trim() || sending}
              className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary
                         text-white disabled:opacity-40 transition-all active:scale-95"
            >
              <Send size={18} />
            </button>
          </div>
        </div>
      )}

      {/* Future Self Letters */}
      {activeTab === 'letters' && (
        <div className="space-y-4 pb-8">
          {letters.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-4xl">✉️</p>
              <p className="mt-3 font-semibold text-foreground">No letters yet</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Write a letter to your future self during onboarding.
              </p>
            </div>
          ) : (
            letters.map((letter) => (
              <div key={letter.id} className="nafs-card p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold text-gold">✉️ Letter from the past</p>
                  <p className="text-xs text-muted-foreground">{timeAgo(letter.written_at)}</p>
                </div>
                <p className="text-sm text-foreground leading-relaxed whitespace-pre-wrap">
                  {letter.content}
                </p>
                {letter.ai_reply_text && (
                  <div className="border-t border-white/10 pt-4">
                    <p className="text-xs text-gold font-semibold mb-2">🤖 NAFS reply — based on your current data</p>
                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {letter.ai_reply_text}
                    </p>
                  </div>
                )}
                {!letter.delivered_at && (
                  <p className="text-xs text-muted-foreground">
                    Delivers on {letter.target_deliver_date}
                  </p>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  )
}
