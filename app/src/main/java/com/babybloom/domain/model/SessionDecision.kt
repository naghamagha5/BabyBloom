package com.babybloom.domain.model

sealed class SessionDecision {
    data class Repeat(
        val activityId: String,
        val contentId: String? = null,
        val encodedQueue: String? = null,
        val stepIndex: Int? = null
    ) : SessionDecision()
    data class Next(
        val activityId: String,
        val contentId: String? = null,
        val encodedQueue: String? = null,
        val stepIndex: Int? = null
    ) : SessionDecision()
    object SessionComplete                    : SessionDecision()
}
