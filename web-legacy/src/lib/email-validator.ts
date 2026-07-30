// Client-side email validator. Catches obvious typos and disposable domains.
// Real verification still requires email confirmation server-side (Supabase setting).

const TYPO_MAP: Record<string, string> = {
  // Gmail
  'gmial.com': 'gmail.com',  'gmai.com': 'gmail.com',  'gmal.com': 'gmail.com',
  'gnail.com': 'gmail.com',  'gmsil.com': 'gmail.com', 'gmail.con': 'gmail.com',
  'gmail.co':  'gmail.com',  'gmail.cm': 'gmail.com',  'gmail.om': 'gmail.com',
  // Yahoo
  'yhoo.com': 'yahoo.com',   'yaho.com': 'yahoo.com',
  'yahooo.com': 'yahoo.com', 'yahoo.con': 'yahoo.com', 'yhaoo.com': 'yahoo.com',
  // Hotmail
  'hotmial.com': 'hotmail.com', 'hotmai.com': 'hotmail.com',
  'hotmal.com':  'hotmail.com', 'hotmail.con': 'hotmail.com',
  'hotnail.com': 'hotmail.com',
  // Outlook
  'outlok.com': 'outlook.com', 'outloo.com': 'outlook.com',
  'outlook.con': 'outlook.com',
  // iCloud
  'icoud.com': 'icloud.com',  'iclod.com': 'icloud.com',
  'iclud.com': 'icloud.com',  'icloud.con': 'icloud.com',
  // Proton
  'proton.con': 'proton.me',  'protonmial.com': 'protonmail.com',
}

// Starter list of common disposable/throwaway email providers
const DISPOSABLE = new Set([
  'mailinator.com', 'tempmail.com', 'temp-mail.org', '10minutemail.com',
  '10minutemail.net', 'guerrillamail.com', 'guerrillamail.info',
  'throwaway.email', 'fakemail.net', 'sharklasers.com', 'getairmail.com',
  'mailcatch.com', 'yopmail.com', 'maildrop.cc', 'dispostable.com',
  'trashmail.com', 'mintemail.com', 'emailondeck.com', 'getnada.com',
  'mailnesia.com', 'spamgourmet.com', 'inboxbear.com', 'inboxalias.com',
  'temp-mail.io', 'tempr.email', 'mail.tm',
])

export interface EmailValidationResult {
  ok: boolean
  error?: string
  suggestion?: string  // suggested correction (caller may offer to autofill)
}

export function validateEmail(raw: string): EmailValidationResult {
  const e = raw.trim().toLowerCase()
  if (!e) return { ok: false, error: 'Email is required' }

  // RFC-friendly syntax check (good enough for common emails)
  const re = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/
  if (!re.test(e)) return { ok: false, error: 'Please enter a valid email address' }

  const [local, domain] = e.split('@')
  if (!local || !domain) return { ok: false, error: 'Please enter a valid email address' }

  // Catch obvious leading/trailing/consecutive dots
  if (domain.startsWith('.') || domain.endsWith('.') || domain.includes('..')) {
    return { ok: false, error: 'Email domain looks invalid' }
  }

  // Typo suggestion
  if (TYPO_MAP[domain]) {
    const suggested = `${local}@${TYPO_MAP[domain]}`
    return { ok: false, error: `Did you mean ${suggested}?`, suggestion: suggested }
  }

  // Disposable check
  if (DISPOSABLE.has(domain)) {
    return { ok: false, error: 'Disposable email addresses are not allowed. Please use a real email.' }
  }

  // TLD sanity
  const tld = domain.split('.').pop()!
  if (tld.length < 2) return { ok: false, error: 'Email domain looks invalid' }

  return { ok: true }
}
