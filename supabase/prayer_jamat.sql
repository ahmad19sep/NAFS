-- Prayer scoring: 0 = missed, 1 = prayed alone, 2 = prayed in jamat
-- Total possible: 5 prayers × 2 = 10 + extra prayers

-- Convert existing boolean columns to integer (0/1/2)
ALTER TABLE public.prayer_logs
  ALTER COLUMN fajr DROP DEFAULT,
  ALTER COLUMN fajr TYPE INTEGER USING (CASE WHEN fajr THEN 1 ELSE 0 END),
  ALTER COLUMN fajr SET DEFAULT 0;

ALTER TABLE public.prayer_logs
  ALTER COLUMN dhuhr DROP DEFAULT,
  ALTER COLUMN dhuhr TYPE INTEGER USING (CASE WHEN dhuhr THEN 1 ELSE 0 END),
  ALTER COLUMN dhuhr SET DEFAULT 0;

ALTER TABLE public.prayer_logs
  ALTER COLUMN asr DROP DEFAULT,
  ALTER COLUMN asr TYPE INTEGER USING (CASE WHEN asr THEN 1 ELSE 0 END),
  ALTER COLUMN asr SET DEFAULT 0;

ALTER TABLE public.prayer_logs
  ALTER COLUMN maghrib DROP DEFAULT,
  ALTER COLUMN maghrib TYPE INTEGER USING (CASE WHEN maghrib THEN 1 ELSE 0 END),
  ALTER COLUMN maghrib SET DEFAULT 0;

ALTER TABLE public.prayer_logs
  ALTER COLUMN isha DROP DEFAULT,
  ALTER COLUMN isha TYPE INTEGER USING (CASE WHEN isha THEN 1 ELSE 0 END),
  ALTER COLUMN isha SET DEFAULT 0;

-- Extra prayers (Tahajjud, Sunnah, Witr, Duha etc.)
-- JSONB array of { name: string, status: 0|1|2 }
ALTER TABLE public.prayer_logs
  ADD COLUMN IF NOT EXISTS extra_prayers JSONB NOT NULL DEFAULT '[]';
