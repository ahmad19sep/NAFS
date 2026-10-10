# AI in NAFS

All AI features use the shared [AI entry point](../web-legacy/src/lib/ai.ts). Gemini handles Coach chat, evening verdicts, recovery plans, starter packs, letter replies, growth/report reviews and text-based screen-time analysis when configured. Existing feature contracts remain text-based.

## Gemini setup

1. Create a key in [Google AI Studio](https://aistudio.google.com/apikey).
2. In [Vercel project settings](https://vercel.com/ahmadwork665-8154s-projects/nafs/settings/environment-variables), add the sensitive server variable **GEMINI_API_KEY** for Production. Add **AI_PROVIDER=gemini** to select it explicitly. **GEMINI_MODEL=gemini-3.5-flash-lite** selects the model verified in production. Without an override the code defaults to Gemini 3.8 Flash.
3. Redeploy the backend so the new deployment receives the variables. The project root is **web-legacy**. Do not deploy a local prebuilt output containing placeholder environment values.
4. Verify a Coach reply and recovery-plan preview with a test account. Confirm provider is **gemini**, model is the configured model, and retrying the same request does not create a second generation or write. Test a report review separately; saved reviews retain the model that originally wrote them.

For local development, copy [web-legacy/.env.local.example](../web-legacy/.env.local.example) to **web-legacy/.env.local**, enter the Supabase configuration and key privately, then restart Next.js. Never put the Gemini key in Android gradle.properties, an APK, Expo public variables, NEXT_PUBLIC variables, logs or chat. Android uses authenticated backend routes and needs no Google secret.

Production uses **AI_PROVIDER=gemini** and **GEMINI_MODEL=gemini-3.5-flash-lite**, with the sensitive GEMINI_API_KEY kept in Vercel. Deployment **dpl_F8ZdqGVLWWxzrDhiUngVJL7zEtWv** is Ready at [nafs-one.vercel.app](https://nafs-one.vercel.app). All ten production HTTP/authentication checks passed. The key value was not downloaded or displayed.

The first selected model, Gemini 3.8 Flash, timed out during two 20-second cloud generation probes after Google accepted the key. Flash-Lite answered successfully. Live checks then passed through the actual app chat code with the Coach system prompt and its recovery-plan code with the full RECOVERY_SCHEMA, on the first attempt. These checks used synthetic text and changed no account records. Retrying the user's Coach request and validating account persistence/replay remain separate acceptance.

## Provider selection

| Server configuration | Standard features | Deep analysis |
| --- | --- | --- |
| AI_PROVIDER=gemini | Gemini; a missing key fails clearly | Gemini; no fallback |
| AI_PROVIDER unset, Gemini key present | Gemini | Gemini |
| AI_PROVIDER unset, no Gemini key | Existing Cloudflare Worker | Existing Claude when configured; otherwise Cloudflare |
| AI_PROVIDER=cloudflare | Cloudflare | Existing Claude when configured; otherwise Cloudflare |

Unknown provider names fail as configuration errors. Gemini transport failures never silently select Cloudflare or Claude. This lets the new backend deploy safely before the key is configured while making the eventual selection explicit.

## Requests and validation

[gemini-ai.ts](../web-legacy/src/lib/gemini-ai.ts) calls Google's stateless **generateContent** REST API. It preserves system instructions and conversation history, maps assistant messages to Google's model role, sends the key only in the x-goog-api-key header, forbids redirects and disables response caching. No Google SDK dependency is required.

The code default is [Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/models); production explicitly selects [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite) after the successful live checks. Gemini 3 models use LOW thinking; the output budget includes reasoning headroom. GEMINI_MODEL can select another available Gemini text model. Model availability and quota are determined by the Google project.

Structured requests send a JSON Schema derived from the application's runtime schema. Returned data is parsed and validated locally before use. One corrective retry is allowed for malformed/schema-invalid output within the existing 50-second total budget. Authentication, quota and transport errors return explicit failures. The timeout covers both connection and response-body reading; caller cancellation is honored.

Blocked, truncated, malformed and empty responses are rejected before account writes. Thinking text is excluded from returned answers. Upstream error bodies are not included in client errors. Saved verdict/letter/report metadata identifies the model actually used; Android accepts Gemini recovery plans and cached previews.

See Google's [generateContent API reference](https://ai.google.dev/api/generate-content) for the wire format and generation options.

## Cloud diagnostics

The optional [verify-gemini.cjs](../web-legacy/scripts/verify-gemini.cjs) prebuild check runs only when NAFS_VERIFY_GEMINI=1. It uses the remote server key to check available models and synthetic generation, then invokes the real chat/recovery code. Failed checks stop the cloud build before promotion. It prints status/model/result metadata, redacts provider error messages and reads no account data. Normal builds skip Google calls. Runtime transport failures log safe model/status/reason metadata without prompts, keys or upstream bodies.

## Legacy compatibility and delivery

Cloudflare uses CLOUDFLARE_APP_KEY and optional CLOUDFLARE_AI_URL, with model @cf/openai/gpt-oss-20b. Claude uses ANTHROPIC_API_KEY and optional ANTHROPIC_MODEL/ANTHROPIC_WORKSPACE_ID for deep analysis. Its existing fallback reports the actual provider and reason. These settings are unnecessary for an explicit Gemini setup.

Cron delivery remains disabled when CRON_SECRET is absent. Configuring Gemini does not enable scheduled notifications. Android release signing, Google OAuth and other deployment acceptance are tracked separately in [PLAN.md](../output/deployment/PLAN.md).
