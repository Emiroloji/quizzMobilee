package com.soruhavuzu.identity

import com.jayway.jsonpath.JsonPath
import com.soruhavuzu.events.identity.UserRegistered
import com.soruhavuzu.support.IntegrationTest
import com.soruhavuzu.support.devToken
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@Import(UserRegisteredEventTests.ListenerConfig::class)
class UserRegisteredEventTests : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var listener: RecordingListener

    @Test
    fun `yeni kullanıcı UserRegistered yayınlar ve olay kaydı outbox'ta tamamlanır`() {
        val token = mvc.devToken("sub-${UUID.randomUUID()}")
        val body =
            mvc
                .get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response.contentAsString
        val userId = UUID.fromString(JsonPath.read(body, "$.id"))

        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            listener.received.count { it.userId.value == userId } shouldBe 1
            jdbc.queryForObject(
                """
                SELECT count(*) FROM platform.event_publication
                WHERE event_type = ? AND serialized_event LIKE ? AND completion_date IS NOT NULL
                """.trimIndent(),
                Int::class.java,
                UserRegistered::class.java.name,
                "%$userId%",
            ) shouldBe 1
        }
    }

    // @ApplicationModuleListener async + transactional proxy ister; Kotlin sınıfları varsayılan olarak final
    open class RecordingListener {
        open val received = CopyOnWriteArrayList<UserRegistered>()

        @ApplicationModuleListener
        open fun on(event: UserRegistered) {
            received += event
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ListenerConfig {
        @Bean
        fun recordingListener() = RecordingListener()
    }
}
