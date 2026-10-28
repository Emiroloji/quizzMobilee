# ADR 0014 — Altyapı tabloları için `platform` şeması

- **Tarih:** 2026-10-28
- **Durum:** Kabul

## Bağlam
Bazı tablolar hiçbir iş modülüne ait değil, kütüphanelere ait:
- Spring Modulith Event Publication Registry: `event_publication`
- ShedLock: `shedlock` (zamanlanmış işlerin çoklu instance'ta tek çalışması, mimari.md §2)
- Flyway: `flyway_schema_history`

ADR 0002'ye göre her tablo bir modülün şemasında olmalı. Bu tabloları bir iş modülüne koymak, o modülü sahibi olmadığı verinin sahibi yapar. `public` şemasına koymak ise sahipsiz tablo üretir.

## Karar
- Bu tablolar **`platform`** şemasında tutulur. `platform` bir iş modülü değildir. Kod, bu şemaya yalnızca kütüphaneler (Modulith, ShedLock, Flyway) aracılığıyla erişir. Modül SQL'i `platform` şemasına yazamaz (kurallar.md A3 lint'i bunu da kapsar).
- Flyway `default-schema = platform`. Modül şemaları, mimari.md §20.1'deki gibi kendi `<modul>_init` migration'larında `CREATE SCHEMA` ile oluşturulur.
- PostgreSQL eklentileri (`ltree`, `vector`) `public` şemasında kurulur. Tipleri tüm şemalar kullanır.

## Alternatifler
- **`public` şeması:** Sahipsiz; yanlışlıkla modül tablosu da buraya düşebilir.
- **Her modüle kendi `event_publication` tablosu:** Modulith tek bir registry bekler.

## Sonuçlar
- (+) ADR 0002 korunur; altyapı tabloları görünür ve izole kalır.
- (+) Bir modül servise ayrıldığında `platform` şeması o servise kopyalanır (her servisin kendi registry'si olur).
