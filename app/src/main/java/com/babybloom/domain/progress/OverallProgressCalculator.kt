package com.babybloom.domain.progress

import com.babybloom.domain.algorithm.AlgorithmWeights
import com.babybloom.domain.repository.LearningContentRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverallProgressCalculator @Inject constructor(
    private val learningContentRepository: LearningContentRepository,
    private val levelMasteryRepository: LevelMasteryRepository
) {

    companion object {
        val ELIGIBLE_CATEGORIES = listOf(
            "LETTER_NAME",
            "ANIMAL",
            "NUMBER",
            "COLOR",
            "SHAPE"
        )
    }

    suspend fun computeForChild(childId: Long): Float {
        val eligibleContentIds = ELIGIBLE_CATEGORIES
            .flatMap { category -> learningContentRepository.getByCategory(category) }
            .mapTo(linkedSetOf()) { it.id }

        if (eligibleContentIds.isEmpty()) return 0f

        val learnedCount = levelMasteryRepository.getContentScoresForChild(childId)
            .asSequence()
            .filter { it.contentId in eligibleContentIds }
            .filter { mastery ->
                val score = mastery.contentScore
                score != null && (score == 0f || score > AlgorithmWeights.CONTENT_PASS_THRESHOLD)
            }
            .map { it.contentId }
            .distinct()
            .count()

        return learnedCount.toFloat() / eligibleContentIds.size.toFloat() * 100f
    }
}
