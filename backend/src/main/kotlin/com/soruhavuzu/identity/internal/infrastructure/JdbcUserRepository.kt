package com.soruhavuzu.identity.internal.infrastructure

import com.soruhavuzu.identity.internal.application.ExternalIdentity
import com.soruhavuzu.identity.internal.application.UserRepository
import com.soruhavuzu.identity.internal.domain.Handle
import com.soruhavuzu.identity.internal.domain.Role
import com.soruhavuzu.identity.internal.domain.User
import com.soruhavuzu.identity.internal.domain.UserStatus
import com.soruhavuzu.shared.ExamId
import com.soruhavuzu.shared.UserId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcUserRepository(
    private val jdbc: JdbcClient,
) : UserRepository {
    override fun findById(id: UserId): User? =
        jdbc
            .sql("SELECT $COLUMNS FROM identity.users u WHERE u.id = :id")
            .param("id", id.value)
            .query(MAPPER)
            .optional()
            .orElse(null)

    override fun findByIdentity(identity: ExternalIdentity): User? =
        jdbc
            .sql(
                """
                SELECT $COLUMNS FROM identity.auth_identities a
                JOIN identity.users u ON u.id = a.user_id
                WHERE a.provider = :provider AND a.subject = :subject
                """.trimIndent(),
            ).param("provider", identity.provider)
            .param("subject", identity.subject)
            .query(MAPPER)
            .optional()
            .orElse(null)

    override fun insert(
        user: User,
        identity: ExternalIdentity,
    ) {
        jdbc
            .sql(
                """
                INSERT INTO identity.users (id, handle, birth_year, role, status, target_exam_id, created_at)
                VALUES (:id, :handle, :birthYear, :role, :status, :targetExamId, :createdAt)
                """.trimIndent(),
            ).param("id", user.id.value)
            .param("handle", user.handle.value)
            .param("birthYear", user.birthYear)
            .param("role", user.role.name)
            .param("status", user.status.name)
            .param("targetExamId", user.targetExamId?.value)
            .param("createdAt", Timestamp.from(user.createdAt))
            .update()
        jdbc
            .sql("INSERT INTO identity.auth_identities (provider, subject, user_id) VALUES (:provider, :subject, :userId)")
            .param("provider", identity.provider)
            .param("subject", identity.subject)
            .param("userId", user.id.value)
            .update()
    }

    private companion object {
        const val COLUMNS = "u.id, u.handle, u.birth_year, u.role, u.status, u.target_exam_id, u.created_at"

        val MAPPER =
            RowMapper { rs, _ ->
                User(
                    id = UserId(rs.getObject("id", UUID::class.java)),
                    handle = Handle(rs.getString("handle")),
                    birthYear = rs.getInt("birth_year").takeUnless { rs.wasNull() },
                    role = Role.valueOf(rs.getString("role")),
                    status = UserStatus.valueOf(rs.getString("status")),
                    targetExamId = rs.getObject("target_exam_id", UUID::class.java)?.let(::ExamId),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
    }
}
