CREATE SCHEMA identity;

CREATE TABLE identity.users (
  id             uuid PRIMARY KEY,
  handle         text NOT NULL UNIQUE,              -- PII: takma ad
  birth_year     smallint,                          -- PII: 18 yaş altı kontrolü
  role           text NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','EDITOR','MODERATOR','ADMIN')),
  status         text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
  target_exam_id uuid,
  created_at     timestamptz NOT NULL,
  deleted_at     timestamptz
);

-- IdP'den bağımsızlık: Cognito değişirse yalnızca bu tablo etkilenir (ADR 0009)
CREATE TABLE identity.auth_identities (
  provider text NOT NULL,                           -- 'cognito'
  subject  text NOT NULL,                           -- PII: IdP 'sub'
  user_id  uuid NOT NULL REFERENCES identity.users (id),
  PRIMARY KEY (provider, subject)
);

CREATE TABLE identity.devices (
  id         uuid PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES identity.users (id),
  push_token text NOT NULL UNIQUE,                  -- PII
  platform   text NOT NULL CHECK (platform IN ('ios','android')),
  updated_at timestamptz NOT NULL
);
