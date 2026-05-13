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
                step.isTest.toString()
            ).joinToString(PART_SEPARATOR)
        }

    fun decode(raw: String?): List<ActivityLaunchStep> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(STEP_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val parts = token.split(PART_SEPARATOR, limit = 3)
                val activityId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val contentId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val isTest = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
                ActivityLaunchStep(activityId = activityId, contentId = contentId, isTest = isTest)
            }
    }
}
