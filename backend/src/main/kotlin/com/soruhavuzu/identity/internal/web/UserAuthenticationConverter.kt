package com.soruhavuzu.identity.internal.web

import com.soruhavuzu.identity.internal.application.ExternalIdentity
import com.soruhavuzu.identity.internal.application.ResolveUserUseCase
import com.soruhavuzu.shared.AuthenticatedUser
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component

/**
 * Doğrulanmış Jwt'yi iç principal'a çevirir. `sub` burada kalır; diğer modüller yalnızca
 * [AuthenticatedUser] görür (kurallar.md A10, ADR 0018). Rol veritabanından gelir.
 */
@Component
class UserAuthenticationConverter(
    private val resolveUser: ResolveUserUseCase,
    private val properties: IdentityProperties,
) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val subject = jwt.subject ?: throw InvalidBearerTokenException("Token'da sub yok")
        val user = resolveUser.resolve(ExternalIdentity(properties.token.provider, subject))
        if (!user.isActive) throw DisabledException("Hesap aktif değil")
        return UsernamePasswordAuthenticationToken.authenticated(
            AuthenticatedUser(user.id),
            null,
            listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
        )
    }
}
