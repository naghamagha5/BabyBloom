package com.babybloom.domain.model

data class ActivityLaunchStep(
    val activityId: String,
    val contentId: String? = null,
    val targetContentId: String? = contentId,
    val isTest: Boolean = false,
    val phase: SessionPhase = if (isTest) SessionPhase.TEST else SessionPhase.LEARNING
)

enum class SessionPhase {
    LEARNING,
    TEST,
    REVISION
}
