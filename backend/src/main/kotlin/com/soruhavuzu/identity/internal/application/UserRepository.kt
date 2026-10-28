package com.soruhavuzu.identity.internal.application

import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.UserId

/** identity şemasına erişim portu. */
interface UserRepository {
    fun findById(id: UserId): User?

    fun findByIdentity(identity: ExternalIdentity): User?

    /**
     * Kullanıcıyı ve IdP eşlemesini birlikte yazar. Eşleme ya da takma ad zaten varsa
     * [org.springframework.dao.DuplicateKeyException] fırlatır; çağıran transaction'ı geri alınır.
     */
    fun insert(
        user: User,
        identity: ExternalIdentity,
    )
}

/** IdP tarafındaki kimlik; `subject` yalnızca identity modülünde görünür (kurallar.md A10). */
data class ExternalIdentity(
    val provider: String,
    val subject: String,
)
