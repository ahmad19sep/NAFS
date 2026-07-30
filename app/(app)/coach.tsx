import { useState, useEffect, useCallback, useRef } from 'react'
import {
  View, Text, ScrollView, TouchableOpacity, TextInput, FlatList,
  KeyboardAvoidingView, Platform, ActivityIndicator, RefreshControl,
} from 'react-native'
import { useSafeAreaInsets } from 'react-native-safe-area-context'
import { Send, Sparkles, RefreshCw } from 'lucide-react-native'
import { supabase } from '@/lib/supabase'
import { ai } from '@/lib/api'
import { timeAgo } from '@/lib/utils'
import type { AiReportRow, AiConversationRow } from '@/types/database'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === 'user'
  return (
    <View className={`mb-3 ${isUser ? 'items-end' : 'items-start'}`}>
      <View className={`max-w-[85%] rounded-2xl px-4 py-3 ${
        isUser ? 'bg-teal rounded-tr-sm' : 'bg-white/10 rounded-tl-sm'
      }`}>
        {!isUser && (
          <View className="flex-row items-center gap-x-1.5 mb-1">
            <Sparkles size={12} color="#C9A227" />
            <Text className="text-xs font-semibold text-gold">NAFS</Text>
          </View>
        )}
        <Text className="text-sm text-white leading-5">{msg.content}</Text>
      </View>
    </View>
  )
}

function ReportCard({ report }: { report: AiReportRow }) {
  const [expanded, setExpanded] = useState(false)
  const typeLabels = {
    tribunal: '⚖️ Weekly Tribunal',
    pull: '🧲 Pull Report',
    gap: '📊 Gap Analysis',
    letter_reply: '✉️ Future Self Reply',
  }

  return (
    <TouchableOpacity
      onPress={() => setExpanded(!expanded)}
      className="rounded-2xl border border-white/10 bg-white/5 p-4 mb-3"
    >
      <View className="flex-row items-center justify-between">
        <Text className="text-sm font-semibold text-white">
          {typeLabels[report.type] ?? report.type}
        </Text>
        <Text className="text-xs text-muted-fg">{timeAgo(report.generated_at)}</Text>
      </View>
      {expanded && (
        <Text className="text-sm text-muted-fg mt-3 leading-5">{report.content_md}</Text>
      )}
    </TouchableOpacity>
  )
}

export default function CoachScreen() {
  const insets = useSafeAreaInsets()
  const scrollRef = useRef<ScrollView>(null)
  const [tab, setTab] = useState<'chat' | 'reports'>('chat')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [convId, setConvId] = useState<string | null>(null)
  const [reports, setReports] = useState<AiReportRow[]>([])
  const [loadingReports, setLoadingReports] = useState(false)

  // Load or create conversation
  useEffect(() => {
    loadConversation()
  }, [])

  async function loadConversation() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return

    const { data } = await supabase
      .from('ai_conversations')
      .select('*')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle()

    if (data) {
      setConvId(data.id)
      setMessages((data.messages as ChatMessage[]) ?? [])
    } else {
      // Create a new conversation
      const { data: newConv } = await supabase
        .from('ai_conversations')
        .insert({ user_id: user.id, messages: [] })
        .select()
        .single()
      if (newConv) setConvId(newConv.id)
    }
  }

  async function loadReports() {
    setLoadingReports(true)
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) { setLoadingReports(false); return }

    const { data } = await supabase
      .from('ai_reports')
      .select('*')
      .eq('user_id', user.id)
      .order('generated_at', { ascending: false })
      .limit(20)

    setReports(data ?? [])
    setLoadingReports(false)
  }

  useEffect(() => {
    if (tab === 'reports') loadReports()
  }, [tab])

  async function sendMessage() {
    if (!input.trim() || sending) return
    const userMsg: ChatMessage = { role: 'user', content: input.trim() }
    const newMessages = [...messages, userMsg]
    setMessages(newMessages)
    setInput('')
    setSending(true)

    setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100)

    try {
      const { reply } = await ai.chat(newMessages, {})
      const assistantMsg: ChatMessage = { role: 'assistant', content: reply }
      const finalMessages = [...newMessages, assistantMsg]
      setMessages(finalMessages)

      // Persist conversation
      if (convId) {
        await supabase.from('ai_conversations')
          .update({ messages: finalMessages })
          .eq('id', convId)
      }
    } catch (err) {
      const errMsg: ChatMessage = {
        role: 'assistant',
        content: "I'm having trouble connecting right now. Please check your connection and try again.",
      }
      setMessages(prev => [...prev, errMsg])
    } finally {
      setSending(false)
      setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100)
    }
  }

  async function clearConversation() {
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return
    setMessages([])
    if (convId) {
      await supabase.from('ai_conversations').update({ messages: [] }).eq('id', convId)
    }
  }

  const STARTER_PROMPTS = [
    "How am I doing this week?",
    "Help me stay consistent with my habits",
    "What should I focus on today?",
    "Give me a reality check on my progress",
  ]

  return (
    <View className="flex-1 bg-navy" style={{ paddingTop: insets.top }}>
      {/* Header */}
      <View className="px-4 pt-4 pb-2 flex-row items-center justify-between">
        <View className="flex-row items-center gap-x-2">
          <Sparkles size={20} color="#C9A227" />
          <Text className="text-xl font-bold text-white">NAFS Coach</Text>
        </View>
        {tab === 'chat' && messages.length > 0 && (
          <TouchableOpacity onPress={clearConversation} className="p-1">
            <RefreshCw size={18} color="#6B8CA8" />
          </TouchableOpacity>
        )}
      </View>

      {/* Tabs */}
      <View className="flex-row mx-4 rounded-xl border border-white/10 bg-white/5 p-1 mb-2">
        {(['chat', 'reports'] as const).map(t => (
          <TouchableOpacity
            key={t}
            onPress={() => setTab(t)}
            className={`flex-1 rounded-lg py-2 items-center ${tab === t ? 'bg-teal' : ''}`}
          >
            <Text className={`text-sm font-semibold capitalize ${tab === t ? 'text-white' : 'text-muted-fg'}`}>
              {t === 'chat' ? '💬 Chat' : '📋 Reports'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'chat' ? (
        <KeyboardAvoidingView
          className="flex-1"
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          keyboardVerticalOffset={0}
        >
          <ScrollView
            ref={scrollRef}
            className="flex-1 px-4"
            contentContainerStyle={{ paddingVertical: 12 }}
            onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: false })}
          >
            {/* Welcome */}
            {messages.length === 0 && (
              <View className="items-center py-8 mb-4">
                <View className="h-16 w-16 rounded-full bg-teal/30 items-center justify-center mb-3">
                  <Sparkles size={28} color="#C9A227" />
                </View>
                <Text className="text-base font-bold text-white text-center">
                  Ask NAFS anything
                </Text>
                <Text className="text-sm text-muted-fg text-center mt-2 leading-5">
                  Your AI coach knows your habits, goals, and progress.
                </Text>
                <View className="gap-y-2 mt-6 w-full">
                  {STARTER_PROMPTS.map(p => (
                    <TouchableOpacity
                      key={p}
                      onPress={() => setInput(p)}
                      className="rounded-xl border border-white/10 bg-white/5 px-4 py-3"
                    >
                      <Text className="text-sm text-white">{p}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}

            {messages.map((msg, i) => <MessageBubble key={i} msg={msg} />)}

            {sending && (
              <View className="items-start mb-3">
                <View className="rounded-2xl rounded-tl-sm bg-white/10 px-4 py-3">
                  <View className="flex-row items-center gap-x-1.5">
                    <Sparkles size={12} color="#C9A227" />
                    <Text className="text-xs text-gold">Thinking…</Text>
                  </View>
                  <ActivityIndicator size="small" color="#6B8CA8" style={{ marginTop: 4 }} />
                </View>
              </View>
            )}
          </ScrollView>

          {/* Input */}
          <View
            className="px-4 pb-4 pt-2 border-t border-white/10 bg-card"
            style={{ paddingBottom: insets.bottom + 16 }}
          >
            <View className="flex-row items-end gap-x-3">
              <TextInput
                className="flex-1 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                style={{ maxHeight: 100 }}
                placeholder="Message NAFS…"
                placeholderTextColor="#6B8CA8"
                value={input}
                onChangeText={setInput}
                multiline
                returnKeyType="send"
                onSubmitEditing={sendMessage}
              />
              <TouchableOpacity
                onPress={sendMessage}
                disabled={sending || !input.trim()}
                className="h-11 w-11 rounded-full bg-teal items-center justify-center"
                style={{ opacity: sending || !input.trim() ? 0.4 : 1 }}
              >
                <Send size={18} color="#fff" />
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      ) : (
        <ScrollView className="flex-1 px-4" contentContainerStyle={{ paddingVertical: 12 }}>
          {loadingReports ? (
            <View className="items-center py-12">
              <ActivityIndicator color="#C9A227" />
            </View>
          ) : reports.length === 0 ? (
            <View className="items-center py-16">
              <Text className="text-4xl mb-3">📋</Text>
              <Text className="text-base font-semibold text-white">No reports yet</Text>
              <Text className="text-sm text-muted-fg mt-1 text-center">
                Weekly tribunal reports and AI analyses will appear here.
              </Text>
            </View>
          ) : (
            reports.map(r => <ReportCard key={r.id} report={r} />)
          )}
        </ScrollView>
      )}
    </View>
  )
}
