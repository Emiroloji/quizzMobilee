-- ref_id'siz olaylar da idempotent olsun: PostgreSQL'de NULL'lar varsayılan olarak birbirinden farklı sayılır.
ALTER TABLE reputation.reputation_events DROP CONSTRAINT reputation_events_kind_ref_id_user_id_key;
ALTER TABLE reputation.reputation_events
  ADD CONSTRAINT reputation_events_kind_ref_id_user_id_key UNIQUE NULLS NOT DISTINCT (kind, ref_id, user_id);
