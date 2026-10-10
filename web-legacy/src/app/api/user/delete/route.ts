import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@supabase/supabase-js'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'

export async function POST(req: NextRequest) {
  try {
    const { user } = await authenticateRequest(req)
    const body = await req.json().catch(() => null)
    if (body?.confirm !== 'DELETE') throw new ApiRouteError(400, 'invalid_request', 'Type DELETE to confirm.')
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL
    const key = process.env.SUPABASE_SERVICE_ROLE_KEY
    if (!url || !key) throw new ApiRouteError(503, 'not_configured', 'Account deletion is not configured. Your account is unchanged.')
    const admin = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } })
    // Paths come from the verified identity and Storage, never the request body.
    for (const bucket of ['avatars', 'challenge-photos', 'log-photos', 'screentime-shots']) {
      const cleanFolder = async (folder: string, depth = 0): Promise<void> => {
        if (depth > 8) throw new ApiRouteError(503, 'unavailable', 'Unable to finish upload cleanup. Please contact support.')
        for (let page = 0; page < 100; page++) {
          const { data, error } = await admin.storage.from(bucket).list(folder, { limit: 100 })
          if (error) {
            if (/not found|does not exist/i.test(error.message)) return
            throw new ApiRouteError(503, 'unavailable', 'Unable to finish account deletion. Please retry.')
          }
          if (!data?.length) return
          for (const item of data.filter(item => !item.id)) await cleanFolder(`${folder}/${item.name}`, depth + 1)
          const paths = data.filter(item => item.id).map(item => `${folder}/${item.name}`)
          if (paths.length) {
            const { error: removeError } = await admin.storage.from(bucket).remove(paths)
            if (removeError) throw new ApiRouteError(503, 'unavailable', 'Unable to finish account deletion. Please retry.')
          }
        }
        throw new ApiRouteError(503, 'unavailable', 'Upload cleanup is incomplete. Please retry.')
      }
      await cleanFolder(user.id)
    }
    // Deleting Auth cascades through public.users. Never delete only the profile.
    const { error } = await admin.auth.admin.deleteUser(user.id)
    if (error) throw new ApiRouteError(503, 'unavailable', 'Unable to finish account deletion. Please retry.')
    return NextResponse.json({ ok: true, authDeleted: true })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ error: error.message, code: error.code }, { status: error.status })
    return NextResponse.json({ error: 'Account deletion failed. Please retry.', code: 'unavailable' }, { status: 503 })
  }
}
