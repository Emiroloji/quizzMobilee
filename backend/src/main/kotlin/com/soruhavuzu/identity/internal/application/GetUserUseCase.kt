package com.soruhavuzu.identity.internal.application

import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.UserId
import org.springframework.stereotype.Service

@Service
class GetUserUseCase(
    private val users: UserRepository,
) {
    fun get(id: UserId): User? = users.findById(id)
}
