package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "child_profiles",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChildProfileEntity(
    @PrimaryKey
    val childId: Long,

    // modality scores — 0.0 to 1.0
    val visualScore: Float = 0.5f,
    val audioScore: Float = 0.5f,
    val gameScore: Float = 0.5f,

    // skill levels — 1 = easy, 2 = medium, 3 = hard
    val languageLevel: Int = 1,
    val numeracyLevel: Int = 1,
    val motorLevel: Int = 1,

    val totalSessionCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)