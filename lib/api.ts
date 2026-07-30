import { supabase } from '@/lib/supabase'

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? ''

async function getAuthHeaders(): Promise<Record<string, string>> {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function apiPost<T = unknown>(path: string, body: unknown): Promise<T> {
  const headers = await getAuthHeaders()
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`API ${path} failed: ${text}`)
  }
  return res.json() as Promise<T>
}

export async function apiGet<T = unknown>(path: string): Promise<T> {
  const headers = await getAuthHeaders()
  const res = await fetch(`${BASE_URL}${path}`, { headers })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`API ${path} failed: ${text}`)
  }
  return res.json() as Promise<T>
}

// AI endpoints — all live in the web-legacy Vercel backend
export const ai = {
  chat: (messages: unknown[], context: unknown) =>
    apiPost<{ reply: string }>('/api/ai/chat', { messages, context }),

  habitStarter: (goal: string) =>
    apiPost<{ habits: unknown[] }>('/api/ai/habit-starter', { goal }),

  goalPlan: (goalId: string) =>
    apiPost<{ plan: string }>('/api/ai/goal-plan', { goalId }),

  healthRecommend: (data: unknown) =>
    apiPost<{ recommendations: string }>('/api/ai/health-recommend', { data }),

  eveningVerdict: (log: unknown) =>
    apiPost<{ verdict: string }>('/api/ai/evening-verdict', { log }),

  futureSelf: (letter: string) =>
    apiPost<{ reply: string }>('/api/ai/future-self', { letter }),
}
