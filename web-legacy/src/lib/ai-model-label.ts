/** Saved reviews describe the model that actually wrote them. */
export function aiModelLabel(model: string | null | undefined, configured: 'gemini' | 'anthropic' | 'cloudflare'): string {
  if (model) {
    if (model.startsWith('gemini-')) return 'Gemini'
    if (model.startsWith('claude-')) return 'Claude'
    if (model.startsWith('@cf/')) return 'the free model'
    return 'AI'
  }
  return configured === 'gemini' ? 'Gemini' : configured === 'anthropic' ? 'Claude' : 'the free model'
}
