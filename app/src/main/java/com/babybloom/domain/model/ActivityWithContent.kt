package com.babybloom.domain.model

data class ActivityWithContent(
    val activity: Activity,
    val contentItems: List<ActivityContent>
)