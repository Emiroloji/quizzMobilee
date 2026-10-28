CREATE SCHEMA moderation;

CREATE FUNCTION moderation.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.% tablosunda % yasak', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'restrict_violation';
END $$;

CREATE TABLE moderation.reports (
  id          uuid PRIMARY KEY,
  question_id uuid NOT NULL,
  version_id  uuid NOT NULL,
  reporter_id uuid NOT NULL,
  reason      text NOT NULL CHECK (reason IN ('WRONG_KEY','COPYRIGHT','INAPPROPRIATE','DUPLICATE','UNCLEAR','OTHER')),
  note        text,                                 -- PII: serbest metin
  weight      smallint NOT NULL,
  status      text NOT NULL CHECK (status IN ('OPEN','UPHELD','DISMISSED')),
  resolved_by uuid,
  resolved_at timestamptz,
  created_at  timestamptz NOT NULL,
  UNIQUE (question_id, reporter_id)
);

CREATE TABLE moderation.review_items (
  id          uuid PRIMARY KEY,
  question_id uuid NOT NULL,
  kind        text NOT NULL CHECK (kind IN ('NEW_SUBMISSION','FLAGGED','KEY_SUSPECT','DUPLICATE_CANDIDATE','COPYRIGHT')),
  priority    smallint NOT NULL,
  status      text NOT NULL CHECK (status IN ('OPEN','DONE')),
  payload     jsonb,
  assigned_to uuid,
  created_at  timestamptz NOT NULL,
  closed_at   timestamptz
);
CREATE INDEX ix_review_open ON moderation.review_items (priority DESC, created_at) WHERE status = 'OPEN';

CREATE TABLE moderation.key_suspects (
  question_id uuid PRIMARY KEY,
  evidence    jsonb NOT NULL,
  detected_at timestamptz NOT NULL,
  resolved_at timestamptz
);

-- Denetim izi, APPEND-ONLY
CREATE TABLE moderation.moderation_actions (
  id          uuid PRIMARY KEY,
  target_type text NOT NULL,
  target_id   uuid NOT NULL,
  actor_id    uuid NOT NULL,
  action      text NOT NULL,
  from_status text,
  to_status   text,
  reason      text,
  created_at  timestamptz NOT NULL
);
CREATE TRIGGER trg_moderation_actions_immutable BEFORE UPDATE OR DELETE ON moderation.moderation_actions
  FOR EACH ROW EXECUTE FUNCTION moderation.forbid_mutation();
CREATE TRIGGER trg_moderation_actions_no_truncate BEFORE TRUNCATE ON moderation.moderation_actions
  FOR EACH STATEMENT EXECUTE FUNCTION moderation.forbid_mutation();

CREATE TABLE moderation.sanctions (
  id         uuid PRIMARY KEY,
  user_id    uuid NOT NULL,
  kind       text NOT NULL,
  until      timestamptz,
  reason     text,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL
);
