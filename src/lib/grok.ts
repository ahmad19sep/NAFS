const GROK_BASE = 'https://api.x.ai/v1'

function headers() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${process.env.GROK_API_KEY}`,
  }
}

export async function grokChat(
  messages: { role: 'system' | 'user' | 'assistant'; content: string }[],
  model = 'grok-3-fast'
): Promise<string> {
  const res = await fetch(`${GROK_BASE}/chat/completions`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ model, messages, temperature: 0.7 }),
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`Grok error ${res.status}: ${err}`)
  }
  const data = await res.json()
  return data.choices[0]?.message?.content ?? ''
}

export async function grokVision(
  base64: string,
  mimeType: string,
  prompt: string,
  model = 'grok-2-vision'
): Promise<string> {
  const res = await fetch(`${GROK_BASE}/chat/completions`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({
      model,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'image_url',
              image_url: { url: `data:${mimeType};base64,${base64}` },
            },
            { type: 'text', text: prompt },
          ],
        },
      ],
      temperature: 0.3,
    }),
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`Grok vision error ${res.status}: ${err}`)
  }
  const data = await res.json()
  return data.choices[0]?.message?.content ?? ''
}

export async function grokText(prompt: string, system?: string, model = 'grok-3-fast'): Promise<string> {
  const messages: { role: 'system' | 'user'; content: string }[] = []
  if (system) messages.push({ role: 'system', content: system })
  messages.push({ role: 'user', content: prompt })
  return grokChat(messages, model)
}
