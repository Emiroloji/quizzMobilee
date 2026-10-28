package com.soruhavuzu.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PaginationTest {
    @Test
    fun `cursor encode-decode dönüşü kayıpsızdır`() {
        val cursor = Cursor("2026-10-28T12:00:00Z|0192f0c4-0000-7000-8000-000000000000")
        Cursor.decode(cursor.encode()) shouldBe cursor
    }

    @Test
    fun `bozuk cursor INVALID_CURSOR problemi üretir`() {
        shouldThrow<ProblemException> { Cursor.decode("%%%") }.code shouldBe ProblemCode.INVALID_CURSOR
    }

    @Test
    fun `limit verilmezse varsayılan, fazlası 100'e kırpılır`() {
        PageRequest.of(null, null).limit shouldBe PageRequest.DEFAULT_LIMIT
        PageRequest.of(null, 1_000).limit shouldBe PageRequest.MAX_LIMIT
    }
}
