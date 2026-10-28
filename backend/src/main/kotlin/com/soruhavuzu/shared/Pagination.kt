package com.soruhavuzu.shared

import java.util.Base64

/** Cursor tabanlı sayfalama; offset yoktur (mimari.md §15, kurallar.md §5). */
data class PageRequest(val cursor: Cursor?, val limit: Int) {
    init {
        require(limit in 1..MAX_LIMIT) { "limit 1..$MAX_LIMIT aralığında olmalı" }
    }

    companion object {
        const val MAX_LIMIT = 100
        const val DEFAULT_LIMIT = 20

        fun of(cursor: String?, limit: Int?): PageRequest =
            PageRequest(cursor?.let(Cursor::decode), (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT))
    }
}

data class CursorPage<T>(val items: List<T>, val nextCursor: Cursor?)

/**
 * İstemci için opak sayfa imleci. İçerik sorgunun sıralama anahtarıdır (ör. "createdAt|id");
 * yorumlamak her sorgunun kendi işidir.
 */
@JvmInline value class Cursor(val key: String) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray(Charsets.UTF_8))

    companion object {
        fun decode(raw: String): Cursor =
            try {
                Cursor(String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8))
            } catch (e: IllegalArgumentException) {
                throw ProblemException(ProblemCode.INVALID_CURSOR, "Geçersiz cursor", e)
            }
    }
}
