package com.soruhavuzu.shared

/**
 * Kimliği doğrulanmış isteğin principal'ı (ADR 0018). Controller'lar `@AuthenticationPrincipal` ile alır;
 * istemciden gelen userId'ye asla güvenilmez (kurallar.md §9).
 */
data class AuthenticatedUser(
    val userId: UserId,
)
