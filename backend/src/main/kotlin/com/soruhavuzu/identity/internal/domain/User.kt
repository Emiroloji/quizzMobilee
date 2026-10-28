package com.soruhavuzu.identity.internal.domain

import com.soruhavuzu.shared.ExamId
import com.soruhavuzu.shared.UserId
import java.time.Instant

enum class Role { USER, EDITOR, MODERATOR, ADMIN }

enum class UserStatus { ACTIVE, SUSPENDED, DELETED }

data class User(
    val id: UserId,
    val handle: Handle,
    val birthYear: Int?,
    val role: Role,
    val status: UserStatus,
    val targetExamId: ExamId?,
    val createdAt: Instant,
) {
    val isActive: Boolean get() = status == UserStatus.ACTIVE

    companion object {
        /** IdP'den ilk kez gelen kişi için kullanıcı; takma ad onboarding'de değiştirilir (ADR 0018). */
        fun register(
            id: UserId,
            handle: Handle,
            now: Instant,
        ): User = User(id, handle, null, Role.USER, UserStatus.ACTIVE, null, now)
    }
}
