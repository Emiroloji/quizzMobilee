package com.soruhavuzu.support

import org.springframework.jdbc.core.JdbcTemplate

/** `practice.attempts` altındaki partition tablolarının adları. */
fun JdbcTemplate.attemptPartitionNames(): List<String> =
    queryForList(
        """
        SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
        WHERE i.inhparent = 'practice.attempts'::regclass
        """.trimIndent(),
        String::class.java,
    ).filterNotNull()
