package com.soruhavuzu

import com.soruhavuzu.support.IntegrationTest
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * API sözleşmesi tek kaynaktan, koddan üretilir (mimari.md §15). Bu test belgeyi
 * packages/api-client/openapi.json'a yazar; CI fark görürse build kırılır (ADR 0017).
 */
class OpenApiDocumentTests : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `OpenAPI belgesi üretilir ve api-client paketine yazılır`() {
        val document =
            mvc
                .get("/v3/api-docs")
                .andReturn()
                .response.contentAsString

        document shouldContain "\"/v1/me\""
        document shouldContain "\"getMe\""
        document shouldNotContain "/v1/dev/tokens"

        OUTPUT.parent.createDirectories()
        OUTPUT.writeText(document.trimEnd() + "\n")
    }

    private companion object {
        val OUTPUT: Path = Path.of("../packages/api-client/openapi.json")
    }
}
