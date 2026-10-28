package com.soruhavuzu.identity.internal.domain

/** Kullanıcının herkese görünen takma adı; ad-soyad zorunlu değildir (proje.md §10). */
@JvmInline
value class Handle(
    val value: String,
) {
    init {
        require(PATTERN.matches(value)) { "Takma ad 3-30 karakter, yalnızca a-z, 0-9 ve _ olabilir" }
    }

    companion object {
        private val PATTERN = Regex("^[a-z0-9_]{3,30}$")
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private const val GENERATED_PREFIX = "kullanici_"
        const val GENERATED_SUFFIX_LENGTH = 8

        /** Rastgele baytlardan geçici takma ad üretir; rastgelelik çağırandan gelir (test edilebilirlik). */
        fun generated(randomBytes: ByteArray): Handle {
            require(randomBytes.size == GENERATED_SUFFIX_LENGTH)
            val suffix = randomBytes.joinToString("") { ALPHABET[Math.floorMod(it.toInt(), ALPHABET.length)].toString() }
            return Handle(GENERATED_PREFIX + suffix)
        }
    }
}
