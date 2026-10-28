CREATE SCHEMA calibration;

CREATE TABLE calibration.question_difficulty (
  question_id       uuid PRIMARY KEY,
  option_count      smallint,                       -- null = tahmin düzeltmesi yok
  prior_logit       real NOT NULL,
  difficulty_logit  real NOT NULL,
  n                 int NOT NULL,                   -- n0 ile başlar
  first_attempts    int NOT NULL DEFAULT 0,
  first_correct     int NOT NULL DEFAULT 0,
  difficulty_bucket smallint NOT NULL CHECK (difficulty_bucket BETWEEN 1 AND 10),
  updated_at        timestamptz NOT NULL
);

CREATE TABLE calibration.user_skill (
  user_id    uuid NOT NULL,
  scope_id   uuid NOT NULL,                         -- exam_id (global) veya topic_id
  scope_type text NOT NULL CHECK (scope_type IN ('EXAM','TOPIC')),
  theta      real NOT NULL DEFAULT 0,
  n          int NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, scope_id)
);

CREATE TABLE calibration.uploader_bias (
  user_id uuid PRIMARY KEY,
  bias    real NOT NULL DEFAULT 0,
  m       int NOT NULL DEFAULT 0
);

-- v2 özelliği, tablo v1'de
CREATE TABLE calibration.difficulty_votes (
  user_id     uuid NOT NULL,
  question_id uuid NOT NULL,
  vote        smallint NOT NULL CHECK (vote BETWEEN 1 AND 10),
  created_at  timestamptz NOT NULL,
  PRIMARY KEY (user_id, question_id)
);
