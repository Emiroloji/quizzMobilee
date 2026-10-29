-- Partition sınırları oturumun saat diliminden bağımsız UTC ay sınırlarıdır.
-- Önceki sürüm date→timestamptz dönüşümünde oturum TimeZone'unu kullanıyordu (UTC dışı makinede kayma).
CREATE OR REPLACE FUNCTION practice.ensure_attempt_partitions(p_from date, p_months int) RETURNS int
LANGUAGE plpgsql AS $$
DECLARE
  month_start date := date_trunc('month', p_from)::date;
  part_name   text;
  created     int := 0;
BEGIN
  FOR i IN 0 .. p_months - 1 LOOP
    part_name := format('attempts_y%sm%s', to_char(month_start, 'YYYY'), to_char(month_start, 'MM'));
    IF to_regclass(format('practice.%I', part_name)) IS NULL THEN
      EXECUTE format(
        'CREATE TABLE practice.%I PARTITION OF practice.attempts FOR VALUES FROM (%L) TO (%L)',
        part_name,
        month_start::timestamp AT TIME ZONE 'UTC',
        (month_start + interval '1 month')::timestamp AT TIME ZONE 'UTC');
      created := created + 1;
    END IF;
    month_start := (month_start + interval '1 month')::date;
  END LOOP;
  RETURN created;
END $$;
