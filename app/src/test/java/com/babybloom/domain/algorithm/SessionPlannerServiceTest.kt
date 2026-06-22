package com.babybloom.domain.algorithm

import com.babybloom.data.local.entity.LevelMasteryEntity
import com.babybloom.data.local.entity.SkillScoreRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.babybloom.domain.model.Activity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.LearningContent
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import com.babybloom.domain.repository.LearningContentRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlannerServiceTest {

    @Test
    fun `assessment mastery skips unseen lower-level letter and animal content`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_l1"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_l1"),
                activity("story_letters_d3", "LANGUAGE", 3, "STORY", "letter_l3"),
                activity("match_letters_d3", "LANGUAGE", 3, "MATCH", "letter_l3"),
                activity("story_animals_d1", "LANGUAGE", 1, "STORY", "animal_l1"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_l1"),
                activity("story_animals_d3", "LANGUAGE", 3, "STORY", "animal_l3"),
                activity("match_animals_d3", "LANGUAGE", 3, "MATCH", "animal_l3"),
                activity("story_numbers_d1", "NUMERACY", 1, "STORY", "number_l1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_l1")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_l1", "letter 1", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_l3", "letter 3", "LETTER_NAME", difficultyLevel = 3, learningOrder = 2),
                LearningContent("animal_l1", "animal 1", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_l3", "animal 3", "ANIMAL", difficultyLevel = 3, learningOrder = 2),
                LearningContent("number_l1", "number 1", "NUMBER", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 2, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 2, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val queue = planner.buildSessionSequence(
            ChildProfile(
                childId = 1L,
                assessmentCompleted = true,
                languageLevel = 4,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )
        )

        val queuedContentIds = queue.mapNotNull { it.contentId }.toSet()
        assertFalse(queuedContentIds.contains("letter_l1"))
        assertFalse(queuedContentIds.contains("animal_l1"))
        assertEquals("letter_l3", queue.firstOrNull { it.contentId?.startsWith("letter_") == true }?.contentId)
        assertEquals("animal_l3", queue.firstOrNull { it.contentId?.startsWith("animal_") == true }?.contentId)
    }

    @Test
    fun `assessment mastery also separates colors and shapes for motor content`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("story_colors_d1", "MOTOR", 1, "STORY", "color_l1"),
                activity("match_colors_d1", "MOTOR", 1, "MATCH", "color_l1"),
                activity("story_colors_d3", "MOTOR", 3, "STORY", "color_l3"),
                activity("match_colors_d3", "MOTOR", 3, "MATCH", "color_l3"),
                activity("story_shapes_d1", "MOTOR", 1, "STORY", "shape_l1"),
                activity("match_shapes_d1", "MOTOR", 1, "MATCH", "shape_l1"),
                activity("story_shapes_d3", "MOTOR", 3, "STORY", "shape_l3"),
                activity("match_shapes_d3", "MOTOR", 3, "MATCH", "shape_l3")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("color_l1", "color 1", "COLOR", difficultyLevel = 1, learningOrder = 1),
                LearningContent("color_l3", "color 3", "COLOR", difficultyLevel = 3, learningOrder = 2),
                LearningContent("shape_l1", "shape 1", "SHAPE", difficultyLevel = 1, learningOrder = 1),
                LearningContent("shape_l3", "shape 3", "SHAPE", difficultyLevel = 3, learningOrder = 2)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 2, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 2, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val queue = planner.buildSessionSequence(
            ChildProfile(
                childId = 1L,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 4,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )
        )

        val queuedContentIds = queue.mapNotNull { it.contentId }.toSet()
        assertFalse(queuedContentIds.contains("color_l1"))
        assertFalse(queuedContentIds.contains("shape_l1"))
        assertTrue(
            "Expected any surfaced motor content to come from the advanced assessed levels: $queuedContentIds",
            queuedContentIds.any { it == "color_l3" || it == "shape_l3" }
        )
    }

    @Test
    fun `stored aggregated test score controls passed content selection`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_l1"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_l1"),
                activity("story_animals_d1", "LANGUAGE", 1, "STORY", "animal_l1"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_l1"),
                activity("story_numbers_d1", "NUMERACY", 1, "STORY", "number_l1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_l1")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_l1", "letter 1", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_l1", "animal 1", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_l1", "number 1", "NUMBER", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = PlannerFakeLevelMasteryRepository(
                listOf(
                    LevelMasteryEntity(
                        childId = 1L,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_l1",
                        contentScore = 0.61f
                    )
                )
            )
        )

        val queue = planner.buildSessionSequence(
            ChildProfile(
                childId = 1L,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )
        )

        val learningContentIds = queue
            .filter { !it.isTest }
            .mapNotNull { it.contentId }
            .toSet()
        assertFalse("letter_l1 should stay out of the learning queue after a stored pass", learningContentIds.contains("letter_l1"))
        assertTrue(learningContentIds.contains("animal_l1"))
    }

    @Test
    fun `revision steps can continue with next persisted content ids after first batch`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_l1"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_l1"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_l1"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_l1"),
                activity("speech_letters_d2", "LANGUAGE", 2, "SPEECH", "letter_l2"),
                activity("match_letters_d2", "LANGUAGE", 2, "MATCH", "letter_l2"),
                activity("trace_letters_d2", "LANGUAGE", 2, "TRACE", "letter_l2"),
                activity("listen_choose_letters_d2", "LANGUAGE", 2, "LISTEN_AND_CHOOSE", "letter_l2"),
                activity("speech_letters_d3", "LANGUAGE", 3, "SPEECH", "letter_l3"),
                activity("match_letters_d3", "LANGUAGE", 3, "MATCH", "letter_l3"),
                activity("trace_letters_d3", "LANGUAGE", 3, "TRACE", "letter_l3"),
                activity("listen_choose_letters_d3", "LANGUAGE", 3, "LISTEN_AND_CHOOSE", "letter_l3"),
                activity("speech_letters_d4", "LANGUAGE", 4, "SPEECH", "letter_l4"),
                activity("match_letters_d4", "LANGUAGE", 4, "MATCH", "letter_l4"),
                activity("trace_letters_d4", "LANGUAGE", 4, "TRACE", "letter_l4"),
                activity("listen_choose_letters_d4", "LANGUAGE", 4, "LISTEN_AND_CHOOSE", "letter_l4"),
                activity("speech_letters_d5", "LANGUAGE", 5, "SPEECH", "letter_l5"),
                activity("match_letters_d5", "LANGUAGE", 5, "MATCH", "letter_l5"),
                activity("trace_letters_d5", "LANGUAGE", 5, "TRACE", "letter_l5"),
                activity("listen_choose_letters_d5", "LANGUAGE", 5, "LISTEN_AND_CHOOSE", "letter_l5")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_l1", "letter 1", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_l2", "letter 2", "LETTER_NAME", difficultyLevel = 2, learningOrder = 2),
                LearningContent("letter_l3", "letter 3", "LETTER_NAME", difficultyLevel = 3, learningOrder = 3),
                LearningContent("letter_l4", "letter 4", "LETTER_NAME", difficultyLevel = 4, learningOrder = 4),
                LearningContent("letter_l5", "letter 5", "LETTER_NAME", difficultyLevel = 5, learningOrder = 5)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, contentId = "letter_l1", contentScore = 0.40f, lastUpdated = 1L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 2, contentId = "letter_l2", contentScore = 0.41f, lastUpdated = 2L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 3, contentId = "letter_l3", contentScore = 0.42f, lastUpdated = 3L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 4, contentId = "letter_l4", contentScore = 0.43f, lastUpdated = 4L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 5, contentId = "letter_l5", contentScore = 0.44f, lastUpdated = 5L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 5, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 5,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val continuation = planner.buildRevisionSteps(
            profile = profile,
            excludedContentIds = setOf("letter_l1", "letter_l2", "letter_l3"),
            limit = 3,
            fallbackToAllWhenEmpty = true
        )

        val revisedIds = continuation.mapNotNull { it.contentId }.toSet()
        assertTrue(revisedIds.contains("letter_l4"))
        assertTrue(revisedIds.contains("letter_l5"))
        assertFalse(revisedIds.contains("letter_l1"))
    }

    @Test
    fun `next revision batch deprioritizes recently revised content ids`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_alef"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_alef"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_alef"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_alef"),
                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("drag_letters_d1", "MOTOR", 1, "DRAG", "letter_alef"),
                activity("speech_numbers_d1", "NUMERACY", 1, "SPEECH", "number_1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_1"),
                activity("drag_numbers_d1", "NUMERACY", 1, "DRAG", "number_1"),
                activity("trace_numbers_d1", "NUMERACY", 1, "TRACE", "number_1"),
                activity("listen_choose_numbers_d1", "NUMERACY", 1, "LISTEN_AND_CHOOSE", "number_1"),
                activity("speech_colors_d1", "MOTOR", 1, "SPEECH", "color_red"),
                activity("drag_colors_d1", "MOTOR", 1, "DRAG", "color_red"),
                activity("match_colors_d1", "MOTOR", 1, "MATCH", "color_red"),
                activity("listen_choose_colors_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "color_red"),
                activity("speech_shapes_d1", "MOTOR", 1, "SPEECH", "shape_circle"),
                activity("drag_shapes_d1", "MOTOR", 1, "DRAG", "shape_circle"),
                activity("trace_shapes_d1", "MOTOR", 1, "TRACE", "shape_circle"),
                activity("match_shapes_d1", "MOTOR", 1, "MATCH", "shape_circle"),
                activity("listen_choose_shapes_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "shape_circle"),
                activity("speech_animals_d2", "LANGUAGE", 2, "SPEECH", "animal_duck"),
                activity("match_animals_d2", "LANGUAGE", 2, "MATCH", "animal_duck"),
                activity("listen_choose_animals_d2", "LANGUAGE", 2, "LISTEN_AND_CHOOSE", "animal_duck"),
                activity("drag_letters_d2", "MOTOR", 2, "DRAG", "letter_ba")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_alef", "alef", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_lion", "lion", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_1", "one", "NUMBER", difficultyLevel = 1, learningOrder = 1),
                LearningContent("color_red", "red", "COLOR", difficultyLevel = 1, learningOrder = 1),
                LearningContent("shape_circle", "circle", "SHAPE", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_duck", "duck", "ANIMAL", difficultyLevel = 2, learningOrder = 2),
                LearningContent("letter_ba", "ba", "LETTER_NAME", difficultyLevel = 2, learningOrder = 2)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, contentId = "letter_alef", contentScore = 0.71f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 1, contentId = "animal_lion", contentScore = 0.70f, lastUpdated = 10_001L),
                LevelMasteryEntity(childId = 1L, skillArea = "NUMBER", level = 1, contentId = "number_1", contentScore = 0.69f, lastUpdated = 10_002L),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, contentId = "color_red", contentScore = 0.20f, lastUpdated = 100L),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 1, contentId = "shape_circle", contentScore = 0.30f, lastUpdated = 200L),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 2, contentId = "animal_duck", contentScore = 0.40f, lastUpdated = 300L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 2, contentId = "letter_ba", contentScore = 0.50f, lastUpdated = 400L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 2, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 2, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "NUMBER", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 1, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 2,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val continuation = planner.buildRevisionSteps(
            profile = profile,
            excludedContentIds = emptySet(),
            limit = 3,
            fallbackToAllWhenEmpty = true
        )

        val revisedIds = continuation.mapNotNull { it.targetContentId ?: it.contentId }.toSet()
        assertTrue(revisedIds.contains("color_red"))
        assertTrue(revisedIds.contains("shape_circle"))
        assertTrue(revisedIds.contains("animal_duck"))
        assertFalse(revisedIds.contains("letter_alef"))
        assertFalse(revisedIds.contains("animal_lion"))
        assertFalse(revisedIds.contains("number_1"))
    }

    @Test
    fun `next revision batch does not refill with the newest revised cohort when older content still exists`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_alef"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_alef"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_alef"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_alef"),
                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("drag_letters_d1", "MOTOR", 1, "DRAG", "letter_alef"),
                activity("speech_numbers_d1", "NUMERACY", 1, "SPEECH", "number_1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_1"),
                activity("drag_numbers_d1", "NUMERACY", 1, "DRAG", "number_1"),
                activity("trace_numbers_d1", "NUMERACY", 1, "TRACE", "number_1"),
                activity("listen_choose_numbers_d1", "NUMERACY", 1, "LISTEN_AND_CHOOSE", "number_1"),
                activity("speech_colors_d1", "MOTOR", 1, "SPEECH", "color_red"),
                activity("drag_colors_d1", "MOTOR", 1, "DRAG", "color_red"),
                activity("match_colors_d1", "MOTOR", 1, "MATCH", "color_red"),
                activity("listen_choose_colors_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "color_red")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_alef", "alef", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_lion", "lion", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_1", "one", "NUMBER", difficultyLevel = 1, learningOrder = 1),
                LearningContent("color_red", "red", "COLOR", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, contentId = "letter_alef", contentScore = 0.80f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 1, contentId = "animal_lion", contentScore = 0.82f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "NUMBER", level = 1, contentId = "number_1", contentScore = 0.78f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, contentId = "color_red", contentScore = 0.10f, lastUpdated = 100L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "ANIMAL", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "NUMBER", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 1,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val continuation = planner.buildRevisionSteps(
            profile = profile,
            excludedContentIds = emptySet(),
            limit = 3,
            fallbackToAllWhenEmpty = true
        )

        val revisedIds = continuation.mapNotNull { it.targetContentId ?: it.contentId }.toSet()
        assertEquals(setOf("color_red"), revisedIds)
    }

    @Test
    fun `same session revision continuation does not reuse already revised content ids when no new candidates remain`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_ba"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_ba"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_ba"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_ba"),
                activity("speech_shapes_d1", "MOTOR", 1, "SPEECH", "shape_circle"),
                activity("drag_shapes_d1", "MOTOR", 1, "DRAG", "shape_circle"),
                activity("trace_shapes_d1", "MOTOR", 1, "TRACE", "shape_circle"),
                activity("match_shapes_d1", "MOTOR", 1, "MATCH", "shape_circle"),
                activity("listen_choose_shapes_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "shape_circle"),
                activity("speech_colors_d1", "MOTOR", 1, "SPEECH", "color_red"),
                activity("drag_colors_d1", "MOTOR", 1, "DRAG", "color_red"),
                activity("match_colors_d1", "MOTOR", 1, "MATCH", "color_red"),
                activity("listen_choose_colors_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "color_red")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_ba", "ba", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("shape_circle", "circle", "SHAPE", difficultyLevel = 1, learningOrder = 1),
                LearningContent("color_red", "red", "COLOR", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val levelMasteryRepository = PlannerFakeLevelMasteryRepository(
            listOf(
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, contentId = "letter_ba", contentScore = 0.80f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 1, contentId = "shape_circle", contentScore = 0.82f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, contentId = "color_red", contentScore = 0.78f, lastUpdated = 10_000L),
                LevelMasteryEntity(childId = 1L, skillArea = "LETTER_NAME", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "SHAPE", level = 1, masteredCount = 1),
                LevelMasteryEntity(childId = 1L, skillArea = "COLOR", level = 1, masteredCount = 1)
            )
        )

        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = levelMasteryRepository
        )

        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 1,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val continuation = planner.buildRevisionSteps(
            profile = profile,
            excludedContentIds = setOf("letter_ba", "shape_circle", "color_red"),
            limit = 3,
            fallbackToAllWhenEmpty = false
        )

        assertTrue(continuation.isEmpty())
    }

    @Test
    fun `revision steps include every mapped activity for each category`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_ba"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_ba"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_ba"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_ba"),

                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("drag_letters_d1", "MOTOR", 1, "DRAG", "letter_ba"),

                activity("speech_numbers_d1", "NUMERACY", 1, "SPEECH", "number_1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_1"),
                activity("drag_numbers_d1", "NUMERACY", 1, "DRAG", "number_1"),
                activity("trace_numbers_d1", "NUMERACY", 1, "TRACE", "number_1"),
                activity("listen_choose_numbers_d1", "NUMERACY", 1, "LISTEN_AND_CHOOSE", "number_1"),

                activity("speech_shapes_d1", "MOTOR", 1, "SPEECH", "shape_circle"),
                activity("drag_shapes_d1", "MOTOR", 1, "DRAG", "shape_circle"),
                activity("trace_shapes_d1", "MOTOR", 1, "TRACE", "shape_circle"),
                activity("match_shapes_d1", "MOTOR", 1, "MATCH", "shape_circle"),
                activity("listen_choose_shapes_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "shape_circle"),

                activity("speech_colors_d1", "MOTOR", 1, "SPEECH", "color_red"),
                activity("drag_colors_d1", "MOTOR", 1, "DRAG", "color_red"),
                activity("match_colors_d1", "MOTOR", 1, "MATCH", "color_red"),
                activity("listen_choose_colors_d1", "MOTOR", 1, "LISTEN_AND_CHOOSE", "color_red")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_ba", "ba", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_lion", "lion", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_1", "one", "NUMBER", difficultyLevel = 1, learningOrder = 1),
                LearningContent("shape_circle", "circle", "SHAPE", difficultyLevel = 1, learningOrder = 1),
                LearningContent("color_red", "red", "COLOR", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = PlannerFakeLevelMasteryRepository(emptyList())
        )
        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 1,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val letterSteps = planner.buildRevisionStepsForContent(profile, "letter_ba")
        val animalSteps = planner.buildRevisionStepsForContent(profile, "animal_lion")
        val numberSteps = planner.buildRevisionStepsForContent(profile, "number_1")
        val shapeSteps = planner.buildRevisionStepsForContent(profile, "shape_circle")
        val colorSteps = planner.buildRevisionStepsForContent(profile, "color_red")

        assertEquals(setOf("speech_letters_d1", "match_letters_d1", "trace_letters_d1", "listen_choose_letters_d1"), letterSteps.map { it.activityId }.toSet())
        assertEquals(setOf("speech_animals_d1", "drag_letters_d1", "match_animals_d1", "listen_choose_animals_d1"), animalSteps.map { it.activityId }.toSet())
        assertEquals("letter_ba", animalSteps.first { it.activityId == "drag_letters_d1" }.contentId)
        assertEquals("animal_lion", animalSteps.first { it.activityId == "drag_letters_d1" }.targetContentId)
        assertEquals(setOf("speech_numbers_d1", "count_d1", "drag_numbers_d1", "trace_numbers_d1", "listen_choose_numbers_d1"), numberSteps.map { it.activityId }.toSet())
        assertEquals(setOf("speech_shapes_d1", "drag_shapes_d1", "trace_shapes_d1", "match_shapes_d1", "listen_choose_shapes_d1"), shapeSteps.map { it.activityId }.toSet())
        assertEquals(setOf("speech_colors_d1", "drag_colors_d1", "match_colors_d1", "listen_choose_colors_d1"), colorSteps.map { it.activityId }.toSet())
    }

    @Test
    fun `resumed revision batch still carries all remaining activities for shown content ids`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_ba"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_ba"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_ba"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_ba"),

                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("drag_letters_d1", "MOTOR", 1, "DRAG", "letter_ba"),

                activity("speech_numbers_d1", "NUMERACY", 1, "SPEECH", "number_1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_1"),
                activity("drag_numbers_d1", "NUMERACY", 1, "DRAG", "number_1"),
                activity("trace_numbers_d1", "NUMERACY", 1, "TRACE", "number_1"),
                activity("listen_choose_numbers_d1", "NUMERACY", 1, "LISTEN_AND_CHOOSE", "number_1")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_ba", "ba", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("animal_lion", "lion", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_1", "one", "NUMBER", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = PlannerFakeLevelMasteryRepository(emptyList())
        )
        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 1,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val expected = planner.buildRevisionStepsForContentIds(
            profile = profile,
            contentIds = listOf("letter_ba", "animal_lion", "number_1")
        )
        val completedKeys = setOf(
            "speech_letters_d1:letter_ba",
            "speech_animals_d1:animal_lion",
            "speech_numbers_d1:number_1",
            "match_letters_d1:letter_ba",
            "drag_letters_d1:letter_ba",
            "count_d1:number_1"
        )

        val remaining = expected.filterNot { "${it.activityId}:${it.contentId}" in completedKeys }

        assertEquals(
            expected.size - completedKeys.size,
            remaining.size
        )
        assertEquals(
            setOf(
                "trace_letters_d1", "listen_choose_letters_d1",
                "match_animals_d1", "listen_choose_animals_d1",
                "drag_numbers_d1", "trace_numbers_d1", "listen_choose_numbers_d1"
            ),
            remaining.map { it.activityId }.toSet()
        )
    }

    @Test
    fun `letter revision ignores speech letters sounds family and keeps the intended speech activity`() = runTest {
        val activityRepository = PlannerFakeActivityRepository(
            mapOf(
                activity("speech_letters_sounds_d1", "LANGUAGE", 1, "SPEECH", "letter_ba_s"),
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_ba"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_ba"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_ba"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_ba")
            )
        )
        val learningContentRepository = PlannerFakeLearningContentRepository(
            listOf(
                LearningContent("letter_ba", "ba", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_ba_s", "ba sound", "LETTER_SOUND", difficultyLevel = 1, learningOrder = 1)
            )
        )
        val planner = SessionPlannerService(
            activityRepository = activityRepository,
            learningContentRepository = learningContentRepository,
            activityResultRepository = PlannerFakeActivityResultRepository(),
            algorithmEngine = AdaptiveAlgorithmEngine(),
            levelMasteryRepository = PlannerFakeLevelMasteryRepository(emptyList())
        )
        val profile = ChildProfile(
            childId = 1L,
            assessmentCompleted = true,
            languageLevel = 1,
            numeracyLevel = 1,
            motorLevel = 1,
            dominantModality = "VISUAL",
            visualPreferencePercent = 100f,
            audioPreferencePercent = 0f,
            interactivePreferencePercent = 0f
        )

        val steps = planner.buildRevisionStepsForContent(profile, "letter_ba")

        assertTrue(steps.any { it.activityId == "speech_letters_d1" && it.contentId == "letter_ba" })
        assertFalse(steps.any { it.activityId == "speech_letters_sounds_d1" })
        assertEquals(4, steps.size)
    }

    @Test
    fun `real seeded revision mappings cover every reachable content activity`() {
        val activities = loadActivitiesFromAssetJson()
        val learningContent = loadLearningContentFromSeeder()
        val activityContentIds = activities.mapValues { (_, activity) ->
            activity.contentItems.map { it.contentId }.toSet()
        }
        val letterIdsByOrder = learningContent
            .filter { it.category == "LETTER_NAME" }
            .associate { it.learningOrder to it.id }

        val missingLinks = mutableListOf<String>()
        learningContent.forEach { content ->
            val expectedActivityIds = when (content.category) {
                "LETTER_NAME" -> listOf(
                    "speech_letters_d${content.difficultyLevel}" to content.id,
                    "match_letters_d${content.difficultyLevel}" to content.id,
                    "trace_letters_d${content.difficultyLevel}" to content.id,
                    "listen_choose_letters_d${content.difficultyLevel}" to content.id
                )
                "ANIMAL" -> listOf(
                    "speech_animals_d${content.difficultyLevel}" to content.id,
                    "match_animals_d${content.difficultyLevel}" to content.id,
                    "listen_choose_animals_d${content.difficultyLevel}" to content.id,
                    "drag_letters_d${content.difficultyLevel}" to letterIdsByOrder.getValue(content.learningOrder)
                )
                "NUMBER" -> listOf(
                    "speech_numbers_d${content.difficultyLevel}" to content.id,
                    "count_d${content.difficultyLevel}" to content.id,
                    "drag_numbers_d${content.difficultyLevel}" to content.id,
                    "trace_numbers_d${content.difficultyLevel}" to content.id,
                    "listen_choose_numbers_d${content.difficultyLevel}" to content.id
                )
                "SHAPE" -> listOf(
                    "speech_shapes_d${content.difficultyLevel}" to content.id,
                    "drag_shapes_d${content.difficultyLevel}" to content.id,
                    "trace_shapes_d${content.difficultyLevel}" to content.id,
                    "match_shapes_d${content.difficultyLevel}" to content.id,
                    "listen_choose_shapes_d${content.difficultyLevel}" to content.id
                )
                "COLOR" -> listOf(
                    "speech_colors_d${content.difficultyLevel}" to content.id,
                    "drag_colors_d${content.difficultyLevel}" to content.id,
                    "match_colors_d${content.difficultyLevel}" to content.id,
                    "listen_choose_colors_d${content.difficultyLevel}" to content.id
                )
                else -> emptyList()
            }
            expectedActivityIds.forEach { (activityId, expectedContentId) ->
                if (expectedContentId !in (activityContentIds[activityId] ?: emptySet())) {
                    missingLinks += "${content.id} -> $activityId missing $expectedContentId"
                }
            }
        }

        assertTrue(
            "Expected real seeded revision/test mappings to cover every reachable content, missing: $missingLinks",
            missingLinks.isEmpty()
        )
    }

    private fun activity(
        id: String,
        skillArea: String,
        difficulty: Int,
        activityType: String,
        vararg contentIds: String
    ): Pair<String, ActivityWithContent> =
        id to ActivityWithContent(
            activity = Activity(
                id = id,
                title = id,
                description = id,
                modality = "VISUAL",
                skillArea = skillArea,
                difficultyLevel = difficulty,
                activityType = activityType,
                isActive = true,
                configJson = "{}"
            ),
            contentItems = contentIds.mapIndexed { index, contentId ->
                ActivityContent(activityId = id, contentId = contentId, orderIndex = index)
            }
        )

    private fun loadActivitiesFromAssetJson(): Map<String, ActivityWithContent> {
        val file = File("src/main/assets/activities.json")
            .takeIf(File::exists)
            ?: File("app/src/main/assets/activities.json")
        val rawJson = file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val type = object : TypeToken<List<ActivityJson>>() {}.type
        val items = Gson().fromJson<List<ActivityJson>>(rawJson, type)

        return items.associate { item ->
            item.id to ActivityWithContent(
                activity = Activity(
                    id = item.id,
                    title = item.title,
                    description = item.description,
                    modality = item.modality,
                    skillArea = item.skillArea,
                    difficultyLevel = item.difficultyLevel,
                    activityType = item.activityType,
                    isActive = item.isActive,
                    configJson = item.configJson
                ),
                contentItems = item.contentIds.mapIndexed { index, contentId ->
                    ActivityContent(activityId = item.id, contentId = contentId, orderIndex = index)
                }
            )
        }
    }

    private fun loadLearningContentFromSeeder(): List<LearningContent> {
        val file = File("src/main/java/com/babybloom/data/local/seeder/LearningContentSeeder.kt")
            .takeIf(File::exists)
            ?: File("app/src/main/java/com/babybloom/data/local/seeder/LearningContentSeeder.kt")
        val raw = file.readText(Charsets.UTF_8)
        val regex = Regex(
            """id\s*=\s*"([^"]+)".*?category\s*=\s*Category\.([A-Z_]+),.*?difficultyLevel\s*=\s*(\d+),.*?learningOrder\s*=\s*(\d+)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        return regex.findAll(raw).map { match ->
            LearningContent(
                id = match.groupValues[1],
                labelAr = match.groupValues[1],
                category = match.groupValues[2],
                difficultyLevel = match.groupValues[3].toInt(),
                learningOrder = match.groupValues[4].toInt()
            )
        }.toList()
    }

    private data class ActivityJson(
        val id: String,
        val title: String,
        val description: String,
        val modality: String,
        val skillArea: String,
        val difficultyLevel: Int,
        val activityType: String,
        val isActive: Boolean,
        val configJson: String,
        val contentIds: List<String>
    )

    private class PlannerFakeActivityRepository(
        private val activities: Map<String, ActivityWithContent>
    ) : ActivityRepository {
        override suspend fun seedActivities(activities: List<Activity>) = Unit
        override suspend fun getAll(): List<Activity> = this.activities.values.map { it.activity }
        override suspend fun getFiltered(modality: String, skillArea: String, difficulty: Int): List<Activity> = emptyList()
        override suspend fun count(): Int = activities.size
        override suspend fun getActivityWithContent(activityId: String): ActivityWithContent? = activities[activityId]
        override suspend fun getActivitiesForPlanning(skillArea: String, difficultyLevel: Int): List<Activity> = emptyList()
        override suspend fun getById(activityId: String): Activity? = activities[activityId]?.activity
    }

    private class PlannerFakeLearningContentRepository(
        private val content: List<LearningContent>
    ) : LearningContentRepository {
        override suspend fun seedContent(contentList: List<LearningContent>): List<Long> = emptyList()
        override suspend fun getById(id: String): LearningContent? = content.firstOrNull { it.id == id }
        override suspend fun getByCategory(category: String): List<LearningContent> = content.filter { it.category == category }
        override suspend fun getContentForActivity(activityId: String): List<LearningContent> = emptyList()
        override suspend fun count(): Int = content.size
    }

    private class PlannerFakeActivityResultRepository(
        private val results: List<ActivityResult> = emptyList()
    ) : ActivityResultRepository {
        override suspend fun saveResult(result: ActivityResult): Long = 1L
        override suspend fun updateScore(resultId: Long, score: Float) = Unit
        override suspend fun getBySession(sessionId: Long): List<ActivityResult> = emptyList()
        override suspend fun getByChild(childId: Long): List<ActivityResult> =
            results.filter { it.childId == childId }
        override suspend fun getRecentBySkillArea(childId: Long, skillArea: String, limit: Int): List<ActivityResult> = emptyList()
        override suspend fun getRecentByModality(childId: Long, modality: String, limit: Int): List<ActivityResult> = emptyList()
        override fun observeByChild(childId: Long): Flow<List<ActivityResult>> = flowOf(emptyList())
        override suspend fun getRecentActivities(childId: Long, limit: Int): List<RecentActivity> = emptyList()
        override suspend fun getSkillScoresForChart(childId: Long): List<SkillScoreRow> = emptyList()
        override fun observeSkillScoresForChart(childId: Long): Flow<List<SkillScoreRow>> = flowOf(emptyList())
        override suspend fun getResultsWithAttention(childId: Long, limit: Int): List<ActivityResult> = emptyList()
        override suspend fun getResultsWithSpeech(childId: Long, limit: Int): List<ActivityResult> = emptyList()
        override suspend fun getResultsWithTouch(childId: Long, limit: Int): List<ActivityResult> = emptyList()
        override suspend fun getForSession(sessionId: Long): List<ActivityResult> = emptyList()
    }

    private class PlannerFakeLevelMasteryRepository(
        initialRows: List<LevelMasteryEntity>
    ) : LevelMasteryRepository {
        private val rows = initialRows.toMutableList()

        override suspend fun upsert(entity: LevelMasteryEntity) = Unit
        override suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity? =
            rows.lastOrNull {
                it.childId == childId && it.skillArea == skillArea && it.level == level && it.contentId.isBlank()
            }
        override suspend fun getForSkill(childId: Long, skillArea: String): List<LevelMasteryEntity> =
            rows.filter { it.childId == childId && it.skillArea == skillArea && it.contentId.isBlank() }
        override suspend fun getAllForChild(childId: Long): List<LevelMasteryEntity> =
            rows.filter { it.childId == childId && it.contentId.isBlank() }
        override suspend fun getByContentId(childId: Long, contentId: String): LevelMasteryEntity? =
            rows.lastOrNull { it.childId == childId && it.contentId == contentId }
        override suspend fun getContentScoresForChild(childId: Long): List<LevelMasteryEntity> =
            rows.filter { it.childId == childId && it.contentId.isNotBlank() }
        override suspend fun incrementMastered(childId: Long, skillArea: String, level: Int) = Unit
        override suspend fun getMasteredCount(childId: Long, skillArea: String, level: Int): Int =
            get(childId, skillArea, level)?.masteredCount ?: 0
        override suspend fun deleteAllForChild(childId: Long) = Unit
    }
}
