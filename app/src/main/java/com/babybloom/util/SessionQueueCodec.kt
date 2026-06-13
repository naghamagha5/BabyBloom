package com.babybloom.util

import com.babybloom.domain.model.ActivityLaunchStep

object SessionQueueCodec {
    private const val STEP_SEPARATOR = ";;"
    private const val PART_SEPARATOR = "::"

    fun encode(queue: List<ActivityLaunchStep>): String =
        queue.joinToString(STEP_SEPARATOR) { step ->
            listOf(
                step.activityId,
                step.contentId.orEmpty(),
                step.targetContentId.orEmpty(),
                step.isTest.toString(),
                step.phase.name
            ).joinToString(PART_SEPARATOR)
        }

    fun decode(raw: String?): List<ActivityLaunchStep> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(STEP_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val parts = token.split(PART_SEPARATOR)
                val activityId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val contentId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val hasTargetContentId = parts.getOrNull(2)?.toBooleanStrictOrNull() == null
                val targetContentId = if (hasTargetContentId) {
                    parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: contentId
                } else {
                    contentId
                }
                val isTest = parts.getOrNull(if (hasTargetContentId) 3 else 2)
                    ?.toBooleanStrictOrNull() ?: false
                val phase = parts.getOrNull(if (hasTargetContentId) 4 else 3)
                    ?.let { runCatching { com.babybloom.domain.model.SessionPhase.valueOf(it) }.getOrNull() }
                    ?: if (isTest) com.babybloom.domain.model.SessionPhase.TEST
                    else com.babybloom.domain.model.SessionPhase.LEARNING
                ActivityLaunchStep(
                    activityId = activityId,
                    contentId = contentId,
                    targetContentId = targetContentId,
                    isTest = isTest,
                    phase = phase
                )
            }
    }
}
