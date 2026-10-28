CREATE SCHEMA stats;

CREATE TABLE stats.daily_activity (
  user_id uuid NOT NULL,
  day     date NOT NULL,
  solved  int NOT NULL,
  correct int NOT NULL,
  PRIMARY KEY (user_id, day)
);

CREATE TABLE stats.topic_stats (
  user_id  uuid NOT NULL,
  topic_id uuid NOT NULL,
  attempts int NOT NULL,
  correct  int NOT NULL,
  PRIMARY KEY (user_id, topic_id)
);
