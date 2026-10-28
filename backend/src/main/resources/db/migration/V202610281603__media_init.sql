CREATE SCHEMA media;

CREATE TABLE media.assets (
  id         uuid PRIMARY KEY,
  owner_id   uuid NOT NULL,
  s3_key     text NOT NULL,
  public_url text,
  status     text NOT NULL CHECK (status IN ('UPLOADING','PROCESSING','READY','REJECTED')),
  mime       text,
  width      int,
  height     int,
  bytes      int,
  phash      bit(64),
  moderation jsonb,
  created_at timestamptz NOT NULL
);
