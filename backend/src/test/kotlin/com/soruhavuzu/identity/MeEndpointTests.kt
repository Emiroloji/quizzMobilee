package com.soruhavuzu.identity

import com.soruhavuzu.support.IntegrationTest
import com.soruhavuzu.support.devToken
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MeEndpointTests : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcTemplate

    private fun newSubject() = "sub-${UUID.randomUUID()}"

    @Test
    fun `token olmadan istek 401 ve UNAUTHORIZED problemi döner`() {
        mvc.get("/v1/me").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `bozuk token 401 döner`() {
        mvc.get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer bozuk.token.degeri") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `izin listesinde olmayan istemcinin token'ı reddedilir`() {
        val token = mvc.devToken(newSubject(), clientId = "yabanci-istemci")
        mvc.get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `ilk istekte kullanıcı oluşur, sonraki istekte aynı kullanıcı döner`() {
        val subject = newSubject()
        val token = mvc.devToken(subject)

        val first = me(token)
        val second = me(token)

        first shouldBe second
        jdbc.queryForObject("SELECT count(*) FROM identity.auth_identities WHERE subject = ?", Int::class.java, subject) shouldBe 1
        mvc.get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("USER") }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.handle") { value(org.hamcrest.Matchers.startsWith("kullanici_")) }
        }
    }

    @Test
    fun `aynı kişinin eşzamanlı ilk istekleri tek kullanıcı oluşturur`() {
        val subject = newSubject()
        val token = mvc.devToken(subject)
        val pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        val ids =
            try {
                pool.invokeAll(List(CONCURRENT_REQUESTS) { Callable { me(token) } }).map { it.get() }
            } finally {
                pool.shutdown()
            }

        ids.toSet() shouldHaveSize 1
        jdbc.queryForObject("SELECT count(*) FROM identity.auth_identities WHERE subject = ?", Int::class.java, subject) shouldBe 1
    }

    @Test
    fun `olmayan uç nokta kimlik doğrulanmış istekte 404 NOT_FOUND problemi döner`() {
        val token = mvc.devToken(newSubject())
        mvc.get("/v1/olmayan-kaynak") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `askıya alınmış kullanıcı 403 ACCOUNT_INACTIVE alır`() {
        val token = mvc.devToken(newSubject())
        val id = me(token)
        jdbc.update("UPDATE identity.users SET status = 'SUSPENDED' WHERE id = ?::uuid", id)

        mvc.get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCOUNT_INACTIVE") }
        }
    }

    private fun me(token: String): String {
        val response =
            mvc
                .get("/v1/me") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
                .andReturn()
                .response
        response.status shouldBe 200
        return com.jayway.jsonpath.JsonPath
            .read<String>(response.contentAsString, "$.id")
            .also { it shouldStartWith "" }
    }

    private companion object {
        const val CONCURRENT_REQUESTS = 8
    }
}
