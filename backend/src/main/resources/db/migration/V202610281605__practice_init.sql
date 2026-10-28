CREATE SCHEMA practice;

CREATE FUNCTION practice.forbid_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'IMMUTABLE_TABLE: %.% tablosunda % yasak', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'restrict_violation';
END $$;

CREATE TABLE practice.sessions (
  id           uuid PRIMARY KEY,
  user_id      uuid NOT NULL,
  session_type text NOT NULL CHECK (session_type IN ('PRACTICE','CHALLENGE','TIMED','REVIEW','OFFLINE_REVIEW')),
  filter       jsonb NOT NULL,                      -- {examId, topicIds, bucketMin, bucketMax}
  target_p     real,                                -- CHALLENGE için
  started_at   timestamptz NOT NULL,
  ended_at     timestamptz
);

-- APPEND-ONLY, aylık partition
CREATE TABLE practice.attempts (
  id                uuid NOT NULL,
  client_attempt_id uuid NOT NULL,
  user_id           uuid NOT NULL,
  question_id       uuid NOT NULL,
  version_id        uuid NOT NULL,
  session_id        uuid,
  session_type      text NOT NULL CHECK (session_type IN ('PRACTICE','CHALLENGE','TIMED','REVIEW','OFFLINE_REVIEW')),
  selected          jsonb NOT NULL,                 -- {"options":["B"]} | {"value":2.5}
  is_correct        boolean NOT NULL,
  is_first          boolean NOT NULL,
  duration_ms       int NOT NULL CHECK (duration_ms >= 0),
  theta_before      real,
  d_before          real,
  p_expected        real,
  created_at        timestamptz NOT NULL,
  PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE INDEX ix_attempts_user ON practice.attempts (user_id, created_at DESC);
CREATE INDEX ix_attempts_question ON practice.attempts (question_id) WHERE is_first;
CREATE TRIGGER trg_attempts_immutable BEFORE UPDATE OR DELETE ON practice.attempts
  FOR EACH ROW EXECUTE FUNCTION practice.forbid_mutation();
CREATE TRIGGER trg_attempts_no_truncate BEFORE TRUNCATE ON practice.attempts
  FOR EACH STATEMENT EXECUTE FUNCTION practice.forbid_mutation();

-- Idempotency anahtarı; partition'sız olduğu için tekillik gerçekten garanti (ADR 0015). APPEND-ONLY.
CREATE TABLE practice.attempt_keys (
  client_attempt_id uuid PRIMARY KEY,
  user_id           uuid NOT NULL,
  attempt_id        uuid NOT NULL,
  created_at        timestamptz NOT NULL
);
CREATE TRIGGER trg_attempt_keys_immutable BEFORE UPDATE OR DELETE ON practice.attempt_keys
  FOR EACH ROW EXECUTE FUNCTION practice.forbid_mutation();
CREATE TRIGGER trg_attempt_keys_no_truncate BEFORE TRUNCATE ON practice.attempt_keys
  FOR EACH STATEMENT EXECUTE FUNCTION practice.forbid_mutation();

-- p_from ayından başlayarak p_months adet aylık partition oluşturur; var olanı atlar. Oluşturulan sayıyı döner.
CREATE FUNCTION practice.ensure_attempt_partitions(p_from date, p_months int) RETURNS int
LANGUAGE plpgsql AS $$
DECLARE
  month_start date := date_trunc('month', p_from)::date;
  part_name   text;
  created     int := 0;
BEGIN
  FOR i IN 0 .. p_months - 1 LOOP
    part_name := format('attempts_y%sm%s', to_char(month_start, 'YYYY'), to_char(month_start, 'MM'));
    IF to_regclass(format('practice.%I', part_name)) IS NULL THEN
      EXECUTE format(
        'CREATE TABLE practice.%I PARTITION OF practice.attempts FOR VALUES FROM (%L) TO (%L)',
        part_name, month_start::timestamptz, (month_start + interval '1 month')::timestamptz);
      created := created + 1;
    END IF;
    month_start := (month_start + interval '1 month')::date;
  END LOOP;
  RETURN created;
END $$;

SELECT practice.ensure_attempt_partitions(current_date, 3);

CREATE TABLE practice.user_question_state (
  user_id        uuid NOT NULL,
  question_id    uuid NOT NULL,
  solved         boolean NOT NULL DEFAULT false,
  last_correct   boolean,
  wrong_count    int NOT NULL DEFAULT 0,
  favorite       boolean NOT NULL DEFAULT false,
  next_review_at timestamptz,                       -- v2 aralıklı tekrar
  updated_at     timestamptz NOT NULL,
  PRIMARY KEY (user_id, question_id)
);
CREATE INDEX ix_uqs_mistakes ON practice.user_question_state (user_id, updated_at DESC) WHERE wrong_count > 0;
CREATE INDEX ix_uqs_fav ON practice.user_question_state (user_id) WHERE favorite;

-- PROJEKSİYON: content + calibration olaylarından beslenir. Yalnızca ACTIVE sorular.
CREATE TABLE practice.question_pool (
  question_id        uuid PRIMARY KEY,
  current_version_id uuid NOT NULL,
  author_id          uuid NOT NULL,
  exam_id            uuid NOT NULL,
  primary_topic_id   uuid NOT NULL,
  topic_path         public.ltree NOT NULL,
  difficulty_logit   real NOT NULL,
  difficulty_bucket  smallint NOT NULL CHECK (difficulty_bucket BETWEEN 1 AND 10),
  random_key         double precision NOT NULL
);
CREATE INDEX ix_pool_pick  ON practice.question_pool (exam_id, difficulty_bucket, random_key);
CREATE INDEX ix_pool_logit ON practice.question_pool (exam_id, difficulty_logit);
CREATE INDEX ix_pool_topic ON practice.question_pool USING gist (topic_path);
