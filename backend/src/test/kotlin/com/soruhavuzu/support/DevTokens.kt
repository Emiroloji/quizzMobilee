package com.soruhavuzu.support

import com.jayway.jsonpath.JsonPath
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** Sahte JWT üreticisinden (local/test profili) access token alır. */
fun MockMvc.devToken(
    subject: String,
    clientId: String? = null,
): String {
    val body = if (clientId == null) """{"subject":"$subject"}""" else """{"subject":"$subject","clientId":"$clientId"}"""
    val response =
        post("/v1/dev/tokens") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn().response
    check(response.status == 200) { "Token alınamadı: ${response.status} ${response.contentAsString}" }
    return JsonPath.read(response.contentAsString, "$.accessToken")
}
