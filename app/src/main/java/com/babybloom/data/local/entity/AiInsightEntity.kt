package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_insights",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("childId")]
)
data class AiInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val childId: Long,
    val insightText: String,
    val generatedAt: Long = System.currentTimeMillis()
)