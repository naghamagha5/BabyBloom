package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// All raw sensor data goes here — speech, touch, gaze
// event_type tells you which sensor, event_data holds the JSON payload
@Entity(
    tableName = "interaction_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("childId"), Index("activityId")]
)
data class InteractionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val childId: Long,
    val activityId: String,

    // "SPEECH"  → eventData = { "confidence": 0.87, "recognizedText": "قطة" }
    // "TOUCH"   -> eventData = { "touchQualityScore": 0.82, "averageMovementDistance": 120.5 }
    // "GAZE"    → eventData = { "eyeOpenProb": 0.71, "attentive": true }
    val eventType: String,
    val eventData: String,           // JSON string
    val timestamp: Long = System.currentTimeMillis()
)
