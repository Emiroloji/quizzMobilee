CREATE SCHEMA notification;

CREATE TABLE notification.notifications (
  id         uuid PRIMARY KEY,
  user_id    uuid NOT NULL,
  kind       text NOT NULL,
  payload    jsonb NOT NULL,                        -- PII olabilir
  sent_at    timestamptz,
  read_at    timestamptz,
  created_at timestamptz NOT NULL
);
