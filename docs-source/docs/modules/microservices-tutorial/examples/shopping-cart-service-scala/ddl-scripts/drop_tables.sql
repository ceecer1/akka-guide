-- You might want to use these drop statements anytime or never

DROP INDEX IF EXISTS event_journal_slice_idx;

DROP TABLE IF EXISTS public.event_journal;

DROP INDEX IF EXISTS snapshot_slice_idx;

DROP TABLE IF EXISTS public.snapshot;

DROP INDEX IF EXISTS durable_state_slice_idx;

DROP TABLE IF EXISTS public.durable_state;

DROP TABLE IF EXISTS public.akka_projection_offset_store;

DROP TABLE IF EXISTS public.akka_projection_timestamp_offset_store;

DROP TABLE IF EXISTS public.akka_projection_management;

DROP TABLE IF EXISTS public.item_popularity;