package com.babybloom.domain.notifications

import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus

interface ParentNotificationHandler {
    suspend fun onAssessmentResultAvailable(childId: Long, assessmentId: Long)
    suspend fun onStatusChanged(childId: Long, previousStatus: ChildStatus, newStatus: ChildStatus)
    suspend fun onProgressUpdated(profile: ChildProfile)
    suspend fun onInsightReadyCheck(profile: ChildProfile)
    suspend fun onAiInsightGenerated(childId: Long)
    suspend fun evaluateAssessmentMissing(childId: Long)
    suspend fun evaluateSessionInactivity(childId: Long)
}
