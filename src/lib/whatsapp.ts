// WhatsApp delivery via CallMeBot — free personal-use gateway.
//
// One-time setup (the phone that should RECEIVE reports):
//   1. Save +34 644 59 71 67 as a contact (CallMeBot).
//   2. Send it this WhatsApp message: "I allow callmebot to send me messages"
//   3. It replies with your personal apikey.
//   4. Set env vars (Vercel + .env.local):
//        CALLMEBOT_PHONE=+92XXXXXXXXXX   (your number, with country code)
//        CALLMEBOT_APIKEY=123456

export function hasWhatsApp(): boolean {
  return !!process.env.CALLMEBOT_PHONE && !!process.env.CALLMEBOT_APIKEY
}

export async function sendWhatsApp(text: string): Promise<boolean> {
  if (!hasWhatsApp()) return false
  try {
    const url =
      `https://api.callmebot.com/whatsapp.php?phone=${encodeURIComponent(process.env.CALLMEBOT_PHONE!)}` +
      `&apikey=${encodeURIComponent(process.env.CALLMEBOT_APIKEY!)}` +
      `&text=${encodeURIComponent(text)}`
    const res = await fetch(url, { signal: AbortSignal.timeout(15000) })
    return res.ok
  } catch (err: any) {
    console.warn('[whatsapp] send failed:', err?.message)
    return false
  }
}
