import { GoogleGenerativeAI } from '@google/generative-ai'

let _client: GoogleGenerativeAI | null = null

export function getGemini() {
  if (!_client) {
    _client = new GoogleGenerativeAI(process.env.GEMINI_API_KEY!)
  }
  return _client
}

export function getModel(modelName = 'gemini-2.0-flash') {
  return getGemini().getGenerativeModel({ model: modelName })
}

export async function generateText(prompt: string, systemInstruction?: string): Promise<string> {
  const model = getGemini().getGenerativeModel({
    model: 'gemini-2.0-flash',
    systemInstruction: systemInstruction,
  })
  const result = await model.generateContent(prompt)
  return result.response.text()
}
