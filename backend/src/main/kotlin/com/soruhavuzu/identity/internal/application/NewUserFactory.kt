package com.soruhavuzu.identity.internal.application

import com.soruhavuzu.identity.internal.domain.Handle
import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.shared.IdGenerator
import com.soruhavuzu.shared.RandomSource
import com.soruhavuzu.shared.UserId
import org.springframework.stereotype.Component
import java.time.Clock

/** IdP'den ilk kez gelen kişi için yeni kullanıcı: UUIDv7 kimlik, geçici takma ad, şimdiki zaman. */
@Component
class NewUserFactory(
    private val ids: IdGenerator,
    private val random: RandomSource,
    private val clock: Clock,
) {
    fun create(): User =
        User.register(
            id = UserId(ids.newId()),
            handle = Handle.generated(random.nextBytes(Handle.GENERATED_SUFFIX_LENGTH)),
            now = clock.instant(),
        )
}
