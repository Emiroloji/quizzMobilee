-- Dokploy Postgres'inde bir kez, yönetici olarak çalıştırılır (runbook adım 3). Parolalar psql değişkeniyle verilir:
-- psql -v app_password=... -v logto_password=... -f postgres-init.sql
CREATE USER soruhavuzu WITH PASSWORD :'app_password';
CREATE DATABASE soruhavuzu OWNER soruhavuzu;
-- CREATEROLE: Logto'nun `db seed` adımı rol oluşturur; yoksa SQLSTATE 42501 ile başarısız olur.
CREATE USER logto WITH CREATEROLE PASSWORD :'logto_password';
CREATE DATABASE logto OWNER logto;
\connect soruhavuzu
CREATE EXTENSION IF NOT EXISTS ltree WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
