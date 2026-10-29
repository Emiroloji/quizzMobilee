-- Yalnızca yeni (boş) volume'da çalışır. Logto kendi veritabanını ve kullanıcısını kullanır (ADR 0024).
-- CREATEROLE: Logto seed adımı kiracı rolleri (logto_tenant_*) oluşturur.
CREATE USER logto WITH PASSWORD 'logto' CREATEROLE;
CREATE DATABASE logto OWNER logto;
