package com.soruhavuzu.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI belgesi tek API sözleşmesidir; packages/api-client buradan üretilir (mimari.md §15, ADR 0017).
 * Sunucu adresi sabittir ki belge ortamdan bağımsız ve deterministik olsun.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(Info().title("SoruHavuzu API").version("v1"))
            .servers(listOf(Server().url("/")))
            .components(
                Components().addSecuritySchemes(
                    BEARER,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(BEARER))

    /** Kotlin'de null olamayan alanlar şemada `required` olur; istemci tipleri buna göre üretilir. */
    @Bean
    fun requiredNonNullProperties(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.components?.schemas?.values?.forEach { schema ->
                val required =
                    schema.properties
                        .orEmpty()
                        .filterValues { it.types?.contains("null") != true && it.nullable != true }
                        .keys
                        .sorted()
                if (required.isNotEmpty()) schema.required = required
            }
        }

    /** Her işlem RFC 9457 problem+json hata yanıtı döndürebilir; `code` her zaman vardır (mimari.md §15). */
    @Bean
    fun problemResponses(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.components.addSchemas(PROBLEM, problemSchema())
            val response =
                ApiResponse()
                    .description("Hata (RFC 9457, application/problem+json)")
                    .content(
                        Content().addMediaType(
                            "application/problem+json",
                            MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$PROBLEM")),
                        ),
                    )
            openApi.paths
                ?.values
                ?.flatMap { it.readOperations() }
                ?.forEach { it.responses.addApiResponse("default", response) }
        }

    private fun problemSchema(): Schema<*> =
        ObjectSchema()
            .addProperty("type", StringSchema())
            .addProperty("title", StringSchema())
            .addProperty("status", IntegerSchema())
            .addProperty("detail", StringSchema())
            .addProperty("instance", StringSchema())
            .addProperty("code", StringSchema().description("Makine okunur hata kodu, ör. UNAUTHORIZED, NOT_FOUND"))
            .required(listOf("code", "status", "title"))

    private companion object {
        const val BEARER = "bearer"
        const val PROBLEM = "Problem"
    }
}
