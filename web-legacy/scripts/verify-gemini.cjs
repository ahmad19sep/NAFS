// Optional cloud-build probe. Secrets stay in the build environment; no account data is read.
const fs = require('node:fs');
const path = require('node:path');

async function verify() {
  if (process.env.NAFS_VERIFY_GEMINI !== '1') return;
  const key = process.env.GEMINI_API_KEY?.trim();
  if (!key) throw new Error('GEMINI_API_KEY is missing from the cloud build.');
  const source = fs.readFileSync(path.join(__dirname, '../src/lib/gemini-ai.ts'), 'utf8');
  const defaultModel = source.match(/DEFAULT_GEMINI_MODEL = '([^']+)'/)?.[1];
  const selected = process.env.GEMINI_MODEL?.trim() || defaultModel;
  if (!/^gemini-[a-zA-Z0-9._-]+$/.test(selected)) throw new Error('Invalid GEMINI_MODEL configuration.');
  const safe = text => String(text || '').replaceAll(key, '[redacted]').replace(/AIza[A-Za-z0-9_-]+/g, '[redacted]').slice(0, 800);
  const request = async (endpoint, body) => {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/${endpoint}`, {
      method: body ? 'POST' : 'GET', redirect: 'error',
      headers: { 'x-goog-api-key': key, 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20_000),
    });
    const data = await response.json().catch(() => null);
    return { response, data };
  };
  const { response, data } = await request('models?pageSize=100');
  const available = (data?.models || []).filter(model => model.supportedGenerationMethods?.includes('generateContent') && /gemini-.*flash/.test(model.name)).map(model => model.name.replace('models/', ''));
  console.log('GEMINI_CHECK ' + JSON.stringify({ stage: 'models', status: response.status, selected, available, code: data?.error?.status, message: safe(data?.error?.message) }));
  if (!response.ok) throw new Error('Google rejected the key or model-list request. See GEMINI_CHECK status.');
  const alternatives = ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemini-2.5-flash-lite', 'gemini-2.5-flash'];
  const candidate = alternatives.find(model => model !== selected && available.includes(model));
  const results = await Promise.all([selected, candidate].filter(Boolean).map(async model => {
    try {
    const { response: result, data: output } = await request(`models/${model}:generateContent`, {
      contents: [{ role: 'user', parts: [{ text: 'Reply with only OK.' }] }],
      generationConfig: { maxOutputTokens: 4112, temperature: 1, ...(/^gemini-3[.-]/.test(model) ? { thinkingConfig: { thinkingLevel: 'LOW' } } : {}) },
    });
    const first = output?.candidates?.[0];
    const text = first?.content?.parts?.filter(part => !part.thought && typeof part.text === 'string').map(part => part.text).join('').trim();
    const passed = result.ok && first?.finishReason === 'STOP' && !!text;
    console.log('GEMINI_CHECK ' + JSON.stringify({ stage: 'generate', model, status: result.status, passed, finishReason: first?.finishReason, code: output?.error?.status, message: safe(output?.error?.message) }));
      return { model, passed };
    } catch (error) {
      console.log('GEMINI_CHECK ' + JSON.stringify({ stage: 'generate', model, passed: false, code: error.name === 'TimeoutError' ? 'TIMEOUT' : 'NETWORK_ERROR' }));
      return { model, passed: false };
    }
  }));
  if (!results.find(result => result.model === selected)?.passed) throw new Error('The configured Gemini model failed its live smoke check. See GEMINI_CHECK results.');

  // Execute the app's real transport, routing and recovery schema against Google.
  // This hook exists only in this short-lived build process; Next.js compiles normally afterward.
  const ts = require('typescript');
  require.extensions['.ts'] = (module, filename) => {
    const compiled = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    });
    module._compile(compiled.outputText, filename);
  };
  const { aiChat, aiStructured, aiProviderInfo } = require('../src/lib/ai.ts');
  const { RECOVERY_SCHEMA } = require('../src/lib/ai-schemas.ts');
  const { ASK_ASCEND_SYSTEM } = require('../src/lib/ai-prompts.ts');
  if (aiProviderInfo().provider !== 'gemini') throw new Error('The configured Gemini provider is not selected.');
  const checked = await Promise.all([
    aiChat([{ role: 'system', content: ASK_ASCEND_SYSTEM }, { role: 'system', content: 'Synthetic test only. No account records exist. Stay secular.' }, { role: 'user', content: 'I keep scrolling late at night. Suggest one small action for tonight.' }]).then(text => {
      const passed = typeof text === 'string' && text.trim().length > 0;
      console.log('GEMINI_CHECK ' + JSON.stringify({ stage: 'app_chat', model: selected, passed }));
      return passed;
    }),
    aiStructured('Synthetic test only: create a recovery plan for late-night scrolling with exactly one task to move the charger away from the bed. Include no habits or challenges.', RECOVERY_SCHEMA,
      'You are an accountability coach. Suggest one practical task, with a clear reason. Keep the plan secular.').then(result => {
      const passed = result.ok && result.provider === 'gemini' && result.data.steps.length === 1 && result.data.steps[0].kind === 'task';
      console.log('GEMINI_CHECK ' + JSON.stringify({ stage: 'app_recovery', model: selected, passed, attempts: result.attempts, ...(!result.ok ? { code: result.code } : {}) }));
      return passed;
    }),
  ]);
  if (!checked.every(Boolean)) throw new Error('The configured Gemini app smoke check failed. See GEMINI_CHECK results.');
}

verify().catch(error => {
  console.error('GEMINI_CHECK ' + JSON.stringify({ stage: 'failed', reason: error.name === 'TimeoutError' ? 'Google request timed out.' : /^(Invalid GEMINI_MODEL|GEMINI_API_KEY|Google rejected|The configured Gemini model)/.test(error.message) ? error.message : 'Google could not be reached.' }));
  process.exitCode = 1;
});
