CREATE SCHEMA content;

-- Değişmez tablolar için UPDATE/DELETE/TRUNCATE engeli (kurallar.md A5, ADR 0006)
CREATE FUNCTION content.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.% tablosunda % yasak', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'restrict_violation';
END $$;

CREATE TABLE content.questions (
  id                      uuid PRIMARY KEY,
  author_id               uuid NOT NULL,
  exam_id                 uuid NOT NULL,
  primary_topic_id        uuid NOT NULL,
  status                  text NOT NULL CHECK (status IN
                            ('DRAFT','PENDING','IN_REVIEW','ACTIVE','FLAGGED','REJECTED','REMOVED')),
  current_version_id      uuid,
  uploader_rating         smallint NOT NULL CHECK (uploader_rating BETWEEN 1 AND 10),  -- ham, değişmez
  originality_declared_at timestamptz NOT NULL,
  created_at              timestamptz NOT NULL,
  updated_at              timestamptz NOT NULL,
  lock_version            int NOT NULL DEFAULT 0   -- optimistic locking
);
CREATE INDEX ix_questions_author ON content.questions (author_id, created_at DESC);
CREATE INDEX ix_questions_status ON content.questions (status) WHERE status IN ('PENDING','IN_REVIEW','FLAGGED');

-- DEĞİŞMEZ
CREATE TABLE content.question_versions (
  id             uuid PRIMARY KEY,
  question_id    uuid NOT NULL REFERENCES content.questions (id),
  version        int NOT NULL,
  body           jsonb NOT NULL,                    -- blok-JSON
  answer_spec    jsonb NOT NULL,
  solution       jsonb NOT NULL,                    -- blok-JSON
  question_type  text NOT NULL CHECK (question_type IN ('mcq','numeric','open')),
  option_count   smallint,                          -- mcq için; tahmin düzeltmesi
  text_hash      bytea NOT NULL,                    -- normalize metin SHA-256
  schema_version smallint NOT NULL,
  created_by     uuid NOT NULL,
  created_at     timestamptz NOT NULL,
  UNIQUE (question_id, version)
);
CREATE INDEX ix_qv_hash ON content.question_versions (text_hash);
CREATE TRIGGER trg_question_versions_immutable BEFORE UPDATE OR DELETE ON content.question_versions
  FOR EACH ROW EXECUTE FUNCTION content.forbid_mutation();
CREATE TRIGGER trg_question_versions_no_truncate BEFORE TRUNCATE ON content.question_versions
  FOR EACH STATEMENT EXECUTE FUNCTION content.forbid_mutation();

-- v2 embedding'leri; versiyondan türetilir, yeniden üretilebilir (ADR 0016)
CREATE TABLE content.question_version_embeddings (
  version_id uuid NOT NULL REFERENCES content.question_versions (id),
  model      text NOT NULL,
  embedding  public.vector(512) NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY (version_id, model)
);

CREATE TABLE content.question_topics (
  topic_id    uuid NOT NULL,
  question_id uuid NOT NULL REFERENCES content.questions (id),
  PRIMARY KEY (topic_id, question_id)
);

CREATE TABLE content.question_assets (
  question_version_id uuid NOT NULL REFERENCES content.question_versions (id),
  asset_id            uuid NOT NULL,
  PRIMARY KEY (question_version_id, asset_id)
);
