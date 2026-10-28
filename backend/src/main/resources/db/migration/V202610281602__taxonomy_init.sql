CREATE SCHEMA taxonomy;

CREATE TABLE taxonomy.exams (
  id     uuid PRIMARY KEY,
  code   text NOT NULL UNIQUE,
  name   text NOT NULL,
  active boolean NOT NULL
);

CREATE TABLE taxonomy.topics (
  id         uuid PRIMARY KEY,
  exam_id    uuid NOT NULL REFERENCES taxonomy.exams (id),
  parent_id  uuid REFERENCES taxonomy.topics (id),
  name       text NOT NULL,
  path       public.ltree NOT NULL,                 -- hızlı alt ağaç sorgusu
  sort_order int NOT NULL DEFAULT 0
);
CREATE INDEX ix_topics_path ON taxonomy.topics USING gist (path);
