package com.babybloom.domain.model

data class User(
    val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val parentLockPin: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)