CREATE SCHEMA reputation;

CREATE FUNCTION reputation.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.% tablosunda % yasak', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'restrict_violation';
END $$;

-- Defter, APPEND-ONLY. Aynı olay iki kez yazılmaz (idempotency).
CREATE TABLE reputation.reputation_events (
  id         uuid PRIMARY KEY,
  user_id    uuid NOT NULL,
  kind       text NOT NULL,
  delta      int NOT NULL,
  ref_id     uuid,
  created_at timestamptz NOT NULL,
  UNIQUE (kind, ref_id, user_id)
);
CREATE TRIGGER trg_reputation_events_immutable BEFORE UPDATE OR DELETE ON reputation.reputation_events
  FOR EACH ROW EXECUTE FUNCTION reputation.forbid_mutation();
CREATE TRIGGER trg_reputation_events_no_truncate BEFORE TRUNCATE ON reputation.reputation_events
  FOR EACH STATEMENT EXECUTE FUNCTION reputation.forbid_mutation();

-- Projeksiyon, defterden yeniden kurulabilir
CREATE TABLE reputation.reputation_balances (
  user_id    uuid PRIMARY KEY,
  balance    int NOT NULL,
  updated_at timestamptz NOT NULL
);
