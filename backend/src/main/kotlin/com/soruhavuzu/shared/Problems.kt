package com.soruhavuzu.shared

/**
 * RFC 9457 yanıtlarındaki makine okunur `code` alanı (mimari.md §15).
 * Yeni kod eklemek serbest; var olanı değiştirmek kırıcı değişikliktir (kurallar.md §5).
 */
@JvmInline value class ProblemCode(
    val value: String,
) {
    companion object {
        val VALIDATION_FAILED = ProblemCode("VALIDATION_FAILED")
        val INVALID_CURSOR = ProblemCode("INVALID_CURSOR")
        val NOT_FOUND = ProblemCode("NOT_FOUND")
        val FORBIDDEN = ProblemCode("FORBIDDEN")
        val RATE_LIMITED = ProblemCode("RATE_LIMITED")
        val INTERNAL_ERROR = ProblemCode("INTERNAL_ERROR")
    }
}

/** Sınırda (web) HTTP yanıtına çevrilen hata. Domain hataları modül içinde sealed interface'tir. */
class ProblemException(
    val code: ProblemCode,
    override val message: String,
    cause: Throwable? = null,
    val status: Int = STATUS_BAD_REQUEST,
) : RuntimeException(message, cause) {
    private companion object {
        const val STATUS_BAD_REQUEST = 400
    }
}
