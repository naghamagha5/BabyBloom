package com.babybloom.domain.model

data class Child(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val age: Int,
    val gender: String = "UNSPECIFIED",
    val notes: String = "",
    val avatar: String = "",
    val soundEffectEnabled: Boolean = true,
    val reducedAnimation: Boolean = false,
    val uiTheme: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val status: ChildStatus = ChildStatus.CALM,
    val sessionDurationMinutes: Int = 10,
    val backgroundMusicEnabled: Boolean = true
)
