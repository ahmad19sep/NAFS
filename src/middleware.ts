import { NextResponse, type NextRequest } from 'next/server'

// This middleware must never perform network I/O. Calling supabase.auth
// getSession()/getUser() here triggers an untimed token-refresh fetch whenever
// the access token has expired, which hangs the edge function until Vercel
// kills it (MIDDLEWARE_INVOCATION_TIMEOUT). Cookie presence is enough: this is
// only a redirect gate, and every page/route re-verifies via requireUser()/
// getUser(). The browser client auto-refreshes and rewrites the cookie.
export function middleware(request: NextRequest) {
  if (!process.env.NEXT_PUBLIC_SUPABASE_URL) {
    return NextResponse.next({ request })
  }

  const hasAuthCookie = request.cookies
    .getAll()
    .some(({ name, value }) => /^sb-.+-auth-token(\.\d+)?$/.test(name) && value)

  const { pathname } = request.nextUrl
  const publicRoutes = ['/auth', '/auth/callback', '/auth/error']
  const isPublicRoute = publicRoutes.some((r) => pathname.startsWith(r))

  if (!hasAuthCookie && !isPublicRoute) {
    const url = request.nextUrl.clone()
    url.pathname = '/auth'
    return NextResponse.redirect(url)
  }

  if (hasAuthCookie && pathname === '/auth') {
    const url = request.nextUrl.clone()
    url.pathname = '/dashboard'
    return NextResponse.redirect(url)
  }

  return NextResponse.next({ request })
}

export const config = {
  matcher: [
    '/((?!api|_next/static|_next/image|favicon.ico|icons|manifest.json|sw.js|workbox-.*|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
}
