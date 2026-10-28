package com.soruhavuzu.events.identity

import com.soruhavuzu.shared.UserId

/** Yeni iç kullanıcı oluşturuldu (mimari.md §5.4). Dinleyenler: reputation, stats. */
data class UserRegistered(
    val userId: UserId,
)
