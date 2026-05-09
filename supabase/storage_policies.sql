-- Run this in Supabase SQL editor to fix storage upload errors
-- This allows authenticated users to upload to the screentime-shots bucket

INSERT INTO storage.buckets (id, name, public)
VALUES ('screentime-shots', 'screentime-shots', true)
ON CONFLICT (id) DO UPDATE SET public = true;

INSERT INTO storage.buckets (id, name, public)
VALUES ('challenge-photos', 'challenge-photos', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Allow authenticated users to upload to screentime-shots
DROP POLICY IF EXISTS "auth upload screentime" ON storage.objects;
CREATE POLICY "auth upload screentime"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'screentime-shots');

DROP POLICY IF EXISTS "auth update screentime" ON storage.objects;
CREATE POLICY "auth update screentime"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'screentime-shots');

DROP POLICY IF EXISTS "public read screentime" ON storage.objects;
CREATE POLICY "public read screentime"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'screentime-shots');

-- Challenge photos
DROP POLICY IF EXISTS "auth upload challenge" ON storage.objects;
CREATE POLICY "auth upload challenge"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'challenge-photos');

DROP POLICY IF EXISTS "public read challenge" ON storage.objects;
CREATE POLICY "public read challenge"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'challenge-photos');
