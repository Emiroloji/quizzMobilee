-- Altyapı tabloları (ADR 0014). Modül kodu bu şemaya SQL yazmaz; yalnızca kütüphaneler kullanır.
CREATE EXTENSION IF NOT EXISTS ltree WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS platform;

-- Spring Modulith Event Publication Registry (outbox), v2 yapısı
CREATE TABLE platform.event_publication (
  id                     uuid NOT NULL,
  listener_id            text NOT NULL,
  event_type             text NOT NULL,
  serialized_event       text NOT NULL,
  publication_date       timestamptz NOT NULL,
  completion_date        timestamptz,
  status                 text,
  completion_attempts    int,
  last_resubmission_date timestamptz,
  PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON platform.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON platform.event_publication (completion_date);

-- ShedLock: zamanlanmış işler çoklu instance'ta tek çalışır
CREATE TABLE platform.shedlock (
  name       varchar(64)  NOT NULL,
  lock_until timestamp    NOT NULL,
  locked_at  timestamp    NOT NULL,
  locked_by  varchar(255) NOT NULL,
  PRIMARY KEY (name)
);
