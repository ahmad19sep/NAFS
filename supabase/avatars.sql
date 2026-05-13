-- Avatars storage bucket + policies for profile pictures

INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO UPDATE SET public = true;

DROP POLICY IF EXISTS "auth upload avatar"  ON storage.objects;
DROP POLICY IF EXISTS "auth update avatar"  ON storage.objects;
DROP POLICY IF EXISTS "auth delete avatar"  ON storage.objects;
DROP POLICY IF EXISTS "public read avatar"  ON storage.objects;

CREATE POLICY "auth upload avatar"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'avatars');

CREATE POLICY "auth update avatar"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'avatars');

CREATE POLICY "auth delete avatar"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'avatars');

CREATE POLICY "public read avatar"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'avatars');

-- Make sure avatar_url exists on users (it should already)
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS avatar_url TEXT;
