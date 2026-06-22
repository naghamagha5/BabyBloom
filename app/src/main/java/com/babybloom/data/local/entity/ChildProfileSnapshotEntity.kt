package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "child_profile_snapshots",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("childId"), Index(value = ["childId", "capturedAt"])]
)
data class ChildProfileSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val visualPreferencePercent: Float,
    val audioPreferencePercent: Float,
    val interactivePreferencePercent: Float,
    val dominantModality: String,
    val languageLevel: Int,
    val numeracyLevel: Int,
    val motorLevel: Int,
    val capturedAt: Long = System.currentTimeMillis()
)
