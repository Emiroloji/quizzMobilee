package com.soruhavuzu

import com.soruhavuzu.support.IntegrationTest
import com.soruhavuzu.support.devToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.json.JsonCompareMode
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
    fun `sürüm bilgisi kimliksiz erişilebilir ve yalnızca uygulama sürümünü içerir`() {
        // deploy-beta, yayına çıkan imajın commit SHA'sını buradan doğrular (APP_VERSION, varsayılan "dev")
        mvc.get("/actuator/info").andExpect {
            status { isOk() }
            // STRICT: sürüm dışında hiçbir bilgi (env, build, git, java) sızmaz
            content { json("""{"app":{"version":"dev"}}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `modül yapısı sıradan kullanıcıya açılmaz`() {
        val token = mvc.devToken("actuator-${System.nanoTime()}")
        mvc.get("/actuator/modulith") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }.andExpect {
            status { isNotFound() }
        }
    }
}
