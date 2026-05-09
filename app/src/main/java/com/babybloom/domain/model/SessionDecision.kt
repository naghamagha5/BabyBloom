package com.babybloom.domain.model

sealed class SessionDecision {
    data class Repeat(
        val activityId: String,
        val contentId: String? = null
    ) : SessionDecision()
    data class Next(
        val activityId: String,
        val contentId: String? = null
    ) : SessionDecision()
    object SessionComplete                    : SessionDecision()
}
