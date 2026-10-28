package com.soruhavuzu.identity.internal.web

import com.soruhavuzu.identity.internal.application.GetUserUseCase
import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.AuthenticatedUser
import com.soruhavuzu.shared.ProblemCode
import com.soruhavuzu.shared.ProblemException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Tag(name = "identity")
@RestController
@RequestMapping("/v1/me")
class MeController(
    private val getUser: GetUserUseCase,
) {
    @Operation(
        operationId = "getMe",
        summary = "Oturumdaki kullanıcının profili",
        description = "Yetki: kimliği doğrulanmış her kullanıcı. İlk istekte kullanıcı otomatik oluşturulur.",
    )
    @GetMapping
    fun me(
        @AuthenticationPrincipal principal: AuthenticatedUser,
    ): MeResponse =
        getUser.get(principal.userId)?.let(MeResponse::from)
            ?: throw ProblemException(ProblemCode.NOT_FOUND, "Kullanıcı bulunamadı", status = HttpStatus.NOT_FOUND.value())
}

data class MeResponse(
    val id: UUID,
    val handle: String,
    @field:Schema(allowableValues = ["USER", "EDITOR", "MODERATOR", "ADMIN"])
    val role: String,
    @field:Schema(allowableValues = ["ACTIVE", "SUSPENDED", "DELETED"])
    val status: String,
    val birthYear: Int?,
    val targetExamId: UUID?,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) =
            MeResponse(
                id = user.id.value,
                handle = user.handle.value,
                role = user.role.name,
                status = user.status.name,
                birthYear = user.birthYear,
                targetExamId = user.targetExamId?.value,
                createdAt = user.createdAt,
            )
    }
}
