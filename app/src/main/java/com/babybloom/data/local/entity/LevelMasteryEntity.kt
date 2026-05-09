package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "level_mastery",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("childId"),
        Index(value = ["childId", "skillArea", "level"], unique = true)
    ]
)
data class LevelMasteryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val childId: Long,
    val skillArea: String,   // "LANGUAGE", "NUMERACY", "MOTOR"
    val level: Int,          // 1–5
    val masteredCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)