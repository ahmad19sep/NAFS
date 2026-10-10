-- Additive native coach contract. Apply before deploying the updated chat route.
-- Existing conversations and their original creation dates are retained.
BEGIN;

ALTER TABLE public.ai_conversations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
UPDATE public.ai_conversations SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE public.ai_conversations ALTER COLUMN updated_at SET DEFAULT NOW();
ALTER TABLE public.ai_conversations ALTER COLUMN updated_at SET NOT NULL;

CREATE OR REPLACE FUNCTION public.nafs_stamp_conversation_version()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
BEGIN
  NEW.created_at := OLD.created_at;
  NEW.updated_at := GREATEST(clock_timestamp(), OLD.updated_at + INTERVAL '1 microsecond');
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS nafs_conversation_version ON public.ai_conversations;
CREATE TRIGGER nafs_conversation_version BEFORE UPDATE ON public.ai_conversations
FOR EACH ROW EXECUTE FUNCTION public.nafs_stamp_conversation_version();

CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_updated
ON public.ai_conversations (user_id, updated_at DESC, id);

COMMIT;
