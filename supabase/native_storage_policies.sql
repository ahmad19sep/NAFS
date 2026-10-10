-- Native media support and ownership guards for the existing public buckets.
-- Existing files and public visibility are retained. Web avatars stored as
-- <user-id>.<extension> remain writable by that user; native uploads use
-- <user-id>/<filename>. Guards also constrain older permissive policies.
BEGIN;

INSERT INTO storage.buckets (id, name, public)
VALUES ('log-photos', 'log-photos', true)
ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION public.nafs_owns_media_path(p_bucket text, p_name text)
RETURNS boolean LANGUAGE sql STABLE SECURITY INVOKER
SET search_path = pg_catalog, public AS $$
  SELECT auth.uid() IS NOT NULL AND (
    (storage.foldername(p_name))[1] = auth.uid()::text
    OR (
      p_bucket = 'avatars' AND strpos(p_name, '/') = 0
      AND split_part(p_name, '.', 1) = auth.uid()::text
      AND strpos(p_name, '.') > 0
    )
  );
$$;

DROP POLICY IF EXISTS "nafs own media" ON storage.objects;
CREATE POLICY "nafs own media" ON storage.objects
FOR ALL TO authenticated
USING (
  bucket_id IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  AND public.nafs_owns_media_path(bucket_id, name)
)
WITH CHECK (
  bucket_id IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  AND public.nafs_owns_media_path(bucket_id, name)
);

DROP POLICY IF EXISTS "nafs media insert guard" ON storage.objects;
CREATE POLICY "nafs media insert guard" ON storage.objects AS RESTRICTIVE
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id NOT IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  OR public.nafs_owns_media_path(bucket_id, name)
);

DROP POLICY IF EXISTS "nafs media update guard" ON storage.objects;
CREATE POLICY "nafs media update guard" ON storage.objects AS RESTRICTIVE
FOR UPDATE TO authenticated
USING (
  bucket_id NOT IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  OR public.nafs_owns_media_path(bucket_id, name)
)
WITH CHECK (
  bucket_id NOT IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  OR public.nafs_owns_media_path(bucket_id, name)
);

DROP POLICY IF EXISTS "nafs media delete guard" ON storage.objects;
CREATE POLICY "nafs media delete guard" ON storage.objects AS RESTRICTIVE
FOR DELETE TO authenticated
USING (
  bucket_id NOT IN ('avatars', 'challenge-photos', 'screentime-shots', 'log-photos')
  OR public.nafs_owns_media_path(bucket_id, name)
);

DROP POLICY IF EXISTS "public read log photos" ON storage.objects;
CREATE POLICY "public read log photos" ON storage.objects
FOR SELECT TO public USING (bucket_id = 'log-photos');

COMMIT;
