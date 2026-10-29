package com.soruhavuzu.shared.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import com.jayway.jsonpath.JsonPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.slf4j.event.KeyValuePair
import java.time.Instant

class JsonLogFormatterTest {
    private val formatter = JsonLogFormatter()
    private val logger = LoggerContext().getLogger("com.soruhavuzu.identity.internal.application.ResolveUserUseCase")

    private fun event(
        message: String,
        mdc: Map<String, String> = emptyMap(),
        keyValues: List<KeyValuePair> = emptyList(),
        error: Throwable? = null,
    ) = LoggingEvent("fqcn", logger, Level.INFO, message, error, null).apply {
        setInstant(Instant.parse("2026-10-29T10:00:00Z"))
        setMDCPropertyMap(mdc)
        keyValues.forEach { addKeyValuePair(it) }
    }

    @Test
    fun `mimari dokümandaki alanlarla tek satır JSON üretir`() {
        val line =
            formatter.format(
                event(
                    "Yeni kullanıcı oluşturuldu",
                    mdc = mapOf("trace_id" to "t1", "span_id" to "s1", "user_id_hash" to "abc123"),
                    keyValues = listOf(KeyValuePair("event", "user_registered"), KeyValuePair("user", "abc123")),
                ),
            )

        line shouldEndWith "\n"
        line.trimEnd() shouldNotContain "\n"
        JsonPath.read<String>(line, "$.timestamp") shouldBe "2026-10-29T10:00:00Z"
        JsonPath.read<String>(line, "$.level") shouldBe "INFO"
        JsonPath.read<String>(line, "$.module") shouldBe "identity"
        JsonPath.read<String>(line, "$.event") shouldBe "user_registered"
        JsonPath.read<String>(line, "$.traceId") shouldBe "t1"
        JsonPath.read<String>(line, "$.spanId") shouldBe "s1"
        JsonPath.read<String>(line, "$.userId") shouldBe "abc123"
        JsonPath.read<String>(line, "$.fields.user") shouldBe "abc123"
    }

    @Test
    fun `hata türü ve yığını ayrı alanda taşınır`() {
        val line = formatter.format(event("Beklenmeyen hata", error = IllegalStateException("x")))
        JsonPath.read<String>(line, "$.error.type") shouldBe "java.lang.IllegalStateException"
    }

    @Test
    fun `uygulama dışı kaydediciler framework modülüdür`() {
        JsonLogFormatter.moduleOf("org.springframework.web.Servlet") shouldBe "framework"
        JsonLogFormatter.moduleOf("com.soruhavuzu.practice.internal.application.X") shouldBe "practice"
    }
}
