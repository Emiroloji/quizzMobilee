package com.soruhavuzu.shared

import java.util.UUID

// Kimlikler tiplidir; ham UUID modül sınırından geçmez (kurallar.md §3.1).

@JvmInline value class UserId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class ExamId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class TopicId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class AssetId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class QuestionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class VersionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class SessionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class AttemptId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}

@JvmInline value class ReportId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
