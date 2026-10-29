-- client_attempt_id istemciden gelir; tekillik kullanıcı başınadır ki bir kullanıcı başkasının
-- anahtarını kullanarak onun cevabını engelleyemesin (ADR 0015 eki).
ALTER TABLE practice.attempt_keys DROP CONSTRAINT attempt_keys_pkey;
ALTER TABLE practice.attempt_keys ADD PRIMARY KEY (user_id, client_attempt_id);
