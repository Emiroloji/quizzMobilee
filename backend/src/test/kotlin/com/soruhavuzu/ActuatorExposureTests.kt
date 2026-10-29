package com.soruhavuzu

import com.soruhavuzu.support.IntegrationTest
import com.soruhavuzu.support.devToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

class ActuatorExposureTests : IntegrationTest() {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `sağlık kontrolü kimliksiz erişilebilir`() {
        mvc.get("/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `modül yapısı sıradan kullanıcıya açılmaz`() {
        val token = mvc.devToken("actuator-${System.nanoTime()}")
        mvc.get("/actuator/modulith") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isNotFound() }
        }
    }
}
