package com.babybloom.presentation.viewmodels

import com.babybloom.data.local.entity.AssessmentResultEntity
import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.data.local.entity.LevelMasteryEntity
import com.babybloom.data.local.entity.SkillScoreRow
import com.babybloom.domain.algorithm.AssessmentCategory
import com.babybloom.domain.algorithm.AssessmentPlannerService
import com.babybloom.domain.model.Activity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.model.Session
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.progress.OverallProgressCalculator
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import com.babybloom.domain.repository.LearningContentRepository
import com.babybloom.domain.repository.SessionRepository
import com.babybloom.domain.status.ChildStatusEvaluator
import com.babybloom.util.speech.SpeechRecognitionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssessmentViewModelTest {

    private fun plannerService(activityRepository: FakeActivityRepository): AssessmentPlannerService =
        AssessmentPlannerService(
            activityRepository = activityRepository
        )

    private fun speechRecognitionManager() = allocateWithoutConstructor(SpeechRecognitionManager::class.java)

    private fun overallProgressCalculator(
        learningContentRepository: LearningContentRepository,
        levelMasteryRepository: LevelMasteryRepository
    ) = OverallProgressCalculator(learningContentRepository, levelMasteryRepository)

    private fun childStatusEvaluator() = ChildStatusEvaluator()

    private fun notificationHandler() = object : ParentNotificationHandler {
        override suspend fun onAssessmentResultAvailable(childId: Long, assessmentId: Long) = Unit
        override suspend fun onStatusChanged(childId: Long, previousStatus: ChildStatus, newStatus: ChildStatus) = Unit
        override suspend fun onProgressUpdated(profile: ChildProfile) = Unit
        override suspend fun onInsightReadyCheck(profile: ChildProfile) = Unit
        override suspend fun onAiInsightGenerated(childId: Long) = Unit
        override suspend fun evaluateAssessmentMissing(childId: Long) = Unit
        override suspend fun evaluateSessionInactivity(childId: Long) = Unit
    }

    @Test
    fun `letters advance after two scored correct probes and warmups do not count`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredLetterCorrects = 0
            var sawAdvancedLetters = false

            repeat(40) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.LETTERS && !isWarmUp && scoredLetterCorrects >= 2) {
                    sawAdvancedLetters = true
                    assertEquals(2, activity.difficultyLevel)
                    return@repeat
                }

                val isCorrect = category == AssessmentCategory.LETTERS && !isWarmUp && activity.difficultyLevel == 1
                if (isCorrect) scoredLetterCorrects++
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertEquals(2, scoredLetterCorrects)
            assertTrue("Expected letters to advance after two scored correct probes", sawAdvancedLetters)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `split first two results require a third scored probe before letters advance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredLetterProbes = 0
            var sawThirdLevelOneLetter = false
            var sawAdvancedLetters = false

            repeat(50) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.LETTERS && !isWarmUp) {
                    when {
                        scoredLetterProbes == 2 -> {
                            sawThirdLevelOneLetter = true
                            assertEquals(1, activity.difficultyLevel)
                        }
                        scoredLetterProbes >= 3 -> {
                            sawAdvancedLetters = true
                            assertEquals(2, activity.difficultyLevel)
                            return@repeat
                        }
                    }
                }

                val isCorrect = when {
                    category != AssessmentCategory.LETTERS || isWarmUp -> false
                    scoredLetterProbes == 0 -> true
                    scoredLetterProbes == 1 -> false
                    scoredLetterProbes == 2 -> true
                    else -> false
                }

                if (category == AssessmentCategory.LETTERS && !isWarmUp) {
                    scoredLetterProbes++
                }
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertTrue("Expected a third level-1 letter probe after split results", sawThirdLevelOneLetter)
            assertTrue("Expected letters to advance after the third correct probe", sawAdvancedLetters)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `letters recover from an initial miss and advance after two later correct probes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredLetterProbes = 0
            var sawThirdLevelOneLetter = false
            var sawAdvancedLetters = false

            repeat(50) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.LETTERS && !isWarmUp) {
                    when {
                        scoredLetterProbes == 2 -> {
                            sawThirdLevelOneLetter = true
                            assertEquals(1, activity.difficultyLevel)
                        }
                        scoredLetterProbes >= 3 -> {
                            sawAdvancedLetters = true
                            assertEquals(2, activity.difficultyLevel)
                            return@repeat
                        }
                    }
                }

                val isCorrect = when {
                    category != AssessmentCategory.LETTERS || isWarmUp -> false
                    scoredLetterProbes == 0 -> false
                    scoredLetterProbes == 1 -> true
                    scoredLetterProbes == 2 -> true
                    else -> false
                }

                if (category == AssessmentCategory.LETTERS && !isWarmUp) {
                    scoredLetterProbes++
                }
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertTrue("Expected a third level-1 letter probe after miss-then-correct split", sawThirdLevelOneLetter)
            assertTrue("Expected letters to advance after the later two correct probes", sawAdvancedLetters)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `numbers do not return to previous level after advancing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredNumberCorrects = 0
            var sawAdvancedNumbers = false

            repeat(50) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.NUMBERS && !isWarmUp && scoredNumberCorrects >= 2) {
                    sawAdvancedNumbers = true
                    assertEquals(2, activity.difficultyLevel)
                    assertNotEquals(1, activity.difficultyLevel)
                    return@repeat
                }

                val isCorrect = category == AssessmentCategory.NUMBERS && !isWarmUp && activity.difficultyLevel == 1
                if (isCorrect) scoredNumberCorrects++
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertEquals(2, scoredNumberCorrects)
            assertTrue("Expected numbers to advance after two scored correct probes", sawAdvancedNumbers)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `two incorrect color probes stop at previous level`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredColorProbes = 0
            var sawHigherThanLevel2Color = false

            repeat(50) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.COLORS && !isWarmUp && scoredColorProbes >= 2) {
                    sawHigherThanLevel2Color = activity.difficultyLevel > 2
                }

                val isCorrect = false
                if (category == AssessmentCategory.COLORS && !isWarmUp) {
                    scoredColorProbes++
                }
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertEquals(2, scoredColorProbes)
            assertFalse("Colors should stop at previous level after two incorrect probes", sawHigherThanLevel2Color)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failing animals at the first tested level does not mark animal level one as passed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val childProfileRepository = FakeChildProfileRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository()
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = childProfileRepository,
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            repeat(60) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest
                val isCorrect = false

                viewModel.onActivityComplete(
                    score = if (category == AssessmentCategory.ANIMALS && !isWarmUp) 0 else if (isCorrect) 1 else 0,
                    total = 1
                )
                advanceUntilIdle()
            }

            assertTrue(viewModel.uiState.value is AssessmentUiState.Complete)
            assertTrue(
                "Animals level 1 should not be marked passed after failing the first tested level",
                levelMasteryRepository.getForSkill(1L, "ANIMAL").isEmpty()
            )
            assertEquals(
                "Profile should still start animal learning from level 1 via the merged language level",
                1,
                childProfileRepository.peekProfile()?.languageLevel
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failing letters numbers and animals at level one does not mark their first level as passed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertTrue(runFirstLevelFailureScenario(AssessmentCategory.LETTERS, "LETTER_NAME").isEmpty())
            assertTrue(runFirstLevelFailureScenario(AssessmentCategory.NUMBERS, "NUMBER").isEmpty())
            assertTrue(runFirstLevelFailureScenario(AssessmentCategory.ANIMALS, "ANIMAL").isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failing colors and shapes at base tested level two resolves them to level one`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertEquals(listOf(1), runBaseLevelTwoFailureScenario(AssessmentCategory.COLORS, "COLOR"))
            assertEquals(listOf(1), runBaseLevelTwoFailureScenario(AssessmentCategory.SHAPES, "SHAPE"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `split then incorrect shape probes stop at current level`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(buildSyntheticActivities())
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredShapeProbes = 0
            var sawThirdShapeAtLevel2 = false
            var sawHigherThanLevel2Shape = false

            repeat(60) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val activity = activityRepository.requireActivity(state.currentActivityId).activity
                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest

                if (category == AssessmentCategory.SHAPES && !isWarmUp) {
                    if (scoredShapeProbes == 2) {
                        sawThirdShapeAtLevel2 = true
                        assertEquals(2, activity.difficultyLevel)
                    }
                    if (scoredShapeProbes >= 3 && activity.difficultyLevel > 2) {
                        sawHigherThanLevel2Shape = true
                    }
                }

                val isCorrect = when {
                    category != AssessmentCategory.SHAPES || isWarmUp -> false
                    scoredShapeProbes == 0 -> true
                    scoredShapeProbes == 1 -> false
                    scoredShapeProbes == 2 -> false
                    else -> false
                }

                if (category == AssessmentCategory.SHAPES && !isWarmUp) {
                    scoredShapeProbes++
                }
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            assertTrue("Expected a third shape probe at the current level after split results", sawThirdShapeAtLevel2)
            assertFalse("Shapes should stop at the current level after split then incorrect", sawHigherThanLevel2Shape)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `perfect session reaches advanced levels without replaying warm-up content`() = runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(loadActivitiesFromJson())
            val planner = plannerService(activityRepository)
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = planner,
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = FakeLevelMasteryRepository(),
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), FakeLevelMasteryRepository()),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            val probes = mutableListOf<Pair<String, String?>>()
            repeat(40) {
                when (val state = viewModel.uiState.value) {
                    is AssessmentUiState.Playing -> {
                        probes += state.currentActivityId to state.currentContentId
                        viewModel.onActivityComplete(score = 1, total = 1)
                        advanceUntilIdle()
                    }
                    is AssessmentUiState.Complete -> return@repeat
                    else -> advanceUntilIdle()
                }
            }

            assertTrue("Assessment should complete in the test scenario", viewModel.uiState.value is AssessmentUiState.Complete)
            assertTrue("Assessment ended before 20 scored activities: $probes", probes.size >= 23)

            assertFalse(
                probes.drop(3).any { it.second in setOf("letter_alef", "letter_ba", "number_1") }
            )

            val scoredCategories = probes.drop(3).map { inferCategory(it.first) }
            val maxCategoryRun = scoredCategories
                .fold(mutableListOf<Pair<String, Int>>()) { runs, category ->
                    if (runs.isEmpty() || runs.last().first != category) {
                        runs += category to 1
                    } else {
                        runs[runs.lastIndex] = category to (runs.last().second + 1)
                    }
                    runs
                }
                .maxOfOrNull { it.second } ?: 0
            assertTrue("Expected mixed categories, got: $probes", maxCategoryRun <= 2)

            val duplicateContentIds = probes.drop(3)
                .mapNotNull { it.second }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys

            assertTrue("Expected a small-pool repeat case, got: $probes", duplicateContentIds.isNotEmpty())
            assertTrue(
                "Unexpected repeated content outside 1-2-item levels: $probes",
                probes.drop(3)
                    .filter { it.second in duplicateContentIds }
                    .all { (activityId, _) ->
                        val level = activityRepository.requireActivity(activityId).activity.difficultyLevel
                        planner.availableContentIds(assessmentCategoryFor(activityId), level).size in 1..2
                    }
            )

            val distinctContentPerLevel = probes.drop(3)
                .groupBy { probe ->
                    val activity = activityRepository.requireActivity(probe.first).activity
                    assessmentCategoryFor(probe.first) to activity.difficultyLevel
                }
                .mapValues { (_, levelProbes) -> levelProbes.mapNotNull { it.second }.toSet().size }

            assertTrue(
                "A level should sample at most 3 distinct content IDs: $probes",
                distinctContentPerLevel.all { it.value <= 3 }
            )

            val letterLevelTwoContentIds = probes.drop(3)
                .filter { (activityId, _) ->
                    assessmentCategoryFor(activityId) == AssessmentCategory.LETTERS &&
                        activityRepository.requireActivity(activityId).activity.difficultyLevel == 2
                }
                .mapNotNull { it.second }
                .distinct()

            if (letterLevelTwoContentIds.size >= 2) {
                val learningOrders = letterLevelTwoContentIds.map { contentId ->
                    contentId to loadLearningOrder(contentId)
                }
                assertEquals(
                    "Expected sampled level content IDs to follow ascending learning order: $learningOrders",
                    learningOrders.sortedBy { it.second }.map { it.first },
                    letterLevelTwoContentIds
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `unfinished current letter level falls back to the previous passed level at session end`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val activityRepository = FakeActivityRepository(loadActivitiesFromJson())
            val levelMasteryRepository = FakeLevelMasteryRepository()
            val viewModel = AssessmentViewModel(
                assessmentPlannerService = plannerService(activityRepository),
                assessmentRepository = FakeAssessmentRepository(),
                childProfileRepository = FakeChildProfileRepository(),
                childRepository = FakeChildRepository(),
                sessionRepository = FakeSessionRepository(),
                activityResultRepository = FakeActivityResultRepository(),
                speechRecognitionManager = speechRecognitionManager(),
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = FakeLearningContentRepository(),
                overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            viewModel.startAssessment(1L)
            advanceUntilIdle()
            viewModel.beginActivities()
            advanceUntilIdle()

            var scoredLetterProbes = 0
            repeat(60) {
                val state = viewModel.uiState.value
                if (state is AssessmentUiState.Complete) return@repeat
                if (state !is AssessmentUiState.Playing) {
                    advanceUntilIdle()
                    return@repeat
                }

                val category = assessmentCategoryFor(state.currentActivityId)
                val isWarmUp = !state.isTest
                val isCorrect = when {
                    category != AssessmentCategory.LETTERS || isWarmUp -> false
                    scoredLetterProbes < 4 -> true
                    else -> false
                }

                if (category == AssessmentCategory.LETTERS && !isWarmUp) {
                    scoredLetterProbes++
                }
                viewModel.onActivityComplete(if (isCorrect) 1 else 0, 1)
                advanceUntilIdle()
            }

            val letterRows = levelMasteryRepository.getForSkill(1L, "LETTER_NAME")
            assertTrue("Expected assessment to seed letter mastery rows", letterRows.isNotEmpty())
            assertEquals(listOf(1, 2), letterRows.map { it.level }.sorted())

            val letterContentRows = levelMasteryRepository.getContentScoresForChild(1L)
                .filter { it.skillArea == "LETTER_NAME" }
            assertEquals(
                listOf("letter_alef", "letter_ba", "letter_ha", "letter_meem", "letter_jeem", "letter_dal", "letter_kha", "letter_ta", "letter_seen"),
                letterContentRows.sortedBy { it.lastUpdated }.map { it.contentId }
            )
            assertTrue(letterContentRows.all { it.contentScore == 0f })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun loadActivitiesFromJson(): Map<String, ActivityWithContent> {
        val file = File("src/main/assets/activities.json")
            .takeIf(File::exists)
            ?: File("app/src/main/assets/activities.json")
        val rawJson = file.readText()
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

    private fun loadLearningOrder(contentId: String): Int {
        val learningOrders = mapOf(
            "letter_kha" to 7,
            "letter_ta" to 8,
            "letter_seen" to 9
        )
        return checkNotNull(learningOrders[contentId]) { "Missing test learning order for $contentId" }
    }

    private fun buildSyntheticActivities(): Map<String, ActivityWithContent> {
        fun activity(
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
                    modality = "TEST",
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

        return mapOf(
            activity("drag_numbers_d1", "NUMERACY", 1, "DRAG", "number_1", "number_2", "number_3"),
            activity("count_d1", "NUMERACY", 1, "COUNT", "number_1", "number_2", "number_3"),
            activity("drag_numbers_d2", "NUMERACY", 2, "DRAG", "number_4", "number_5", "number_6"),
            activity("count_d2", "NUMERACY", 2, "COUNT", "number_4", "number_5", "number_6"),
            activity("drag_numbers_d3", "NUMERACY", 3, "DRAG", "number_7", "number_8", "number_9"),
            activity("count_d3", "NUMERACY", 3, "COUNT", "number_7", "number_8", "number_9"),
            activity("match_colors_d2", "MOTOR", 2, "MATCH", "color_yellow", "color_green", "color_black"),
            activity("drag_colors_d2", "MOTOR", 2, "DRAG", "color_yellow", "color_green", "color_black"),
            activity("match_colors_d3", "MOTOR", 3, "MATCH", "color_orange", "color_pink", "color_brown"),
            activity("drag_colors_d3", "MOTOR", 3, "DRAG", "color_orange", "color_pink", "color_brown"),
            activity("trace_shapes_d2", "MOTOR", 2, "TRACE", "shape_square"),
            activity("match_shapes_d2", "MOTOR", 2, "MATCH", "shape_square"),
            activity("trace_shapes_d3", "MOTOR", 3, "TRACE", "shape_triangle"),
            activity("match_shapes_d3", "MOTOR", 3, "MATCH", "shape_triangle"),
            activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_meem", "letter_ha", "letter_jeem"),
            activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_ba", "letter_ha", "letter_dal"),
            activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_alef", "letter_meem", "letter_jeem"),
            activity("listen_choose_letters_d2", "LANGUAGE", 2, "LISTEN_AND_CHOOSE", "letter_ta", "letter_kha", "letter_seen"),
            activity("match_letters_d2", "LANGUAGE", 2, "MATCH", "letter_ta", "letter_kha", "letter_seen"),
            activity("trace_letters_d2", "LANGUAGE", 2, "TRACE", "letter_ta", "letter_kha", "letter_seen"),
            activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion", "animal_duck", "animal_goat"),
            activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion", "animal_duck", "animal_goat"),
            activity("speech_animals_d2", "LANGUAGE", 2, "SPEECH", "animal_fish", "animal_llama", "animal_dove"),
            activity("listen_choose_animals_d2", "LANGUAGE", 2, "LISTEN_AND_CHOOSE", "animal_fish", "animal_llama", "animal_dove")
        )
    }

    private fun inferCategory(activityId: String): String =
        when {
            activityId.contains("color") -> "COLORS"
            activityId.contains("shape") -> "SHAPES"
            activityId.contains("number") || activityId.startsWith("count_") -> "NUMBERS"
            activityId.contains("animal") -> "ANIMALS"
            else -> "LETTERS"
        }

    private fun assessmentCategoryFor(activityId: String): AssessmentCategory =
        when (inferCategory(activityId)) {
            "COLORS" -> AssessmentCategory.COLORS
            "SHAPES" -> AssessmentCategory.SHAPES
            "NUMBERS" -> AssessmentCategory.NUMBERS
            "ANIMALS" -> AssessmentCategory.ANIMALS
            else -> AssessmentCategory.LETTERS
        }

    private suspend fun TestScope.runFirstLevelFailureScenario(
        targetCategory: AssessmentCategory,
        skillArea: String
    ): List<Int> {
        val activityRepository = FakeActivityRepository(buildSyntheticActivities())
        val levelMasteryRepository = FakeLevelMasteryRepository()
        val viewModel = AssessmentViewModel(
            assessmentPlannerService = plannerService(activityRepository),
            assessmentRepository = FakeAssessmentRepository(),
            childProfileRepository = FakeChildProfileRepository(),
            childRepository = FakeChildRepository(),
            sessionRepository = FakeSessionRepository(),
            activityResultRepository = FakeActivityResultRepository(),
            speechRecognitionManager = speechRecognitionManager(),
            levelMasteryRepository = levelMasteryRepository,
            learningContentRepository = FakeLearningContentRepository(),
            overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), levelMasteryRepository),
            childStatusEvaluator = childStatusEvaluator(),
            notificationService = notificationHandler()
        )

        viewModel.startAssessment(1L)
        advanceUntilIdle()
        viewModel.beginActivities()
        advanceUntilIdle()

        repeat(60) {
            val state = viewModel.uiState.value
            if (state is AssessmentUiState.Complete) return@repeat
            if (state !is AssessmentUiState.Playing) {
                advanceUntilIdle()
                return@repeat
            }

            val category = assessmentCategoryFor(state.currentActivityId)
            val isWarmUp = !state.isTest
            val score = if (category == targetCategory && !isWarmUp) 0 else 1

            viewModel.onActivityComplete(score = score, total = 1)
            advanceUntilIdle()
        }

        return levelMasteryRepository.getForSkill(1L, skillArea).map { it.level }.sorted()
    }

    private suspend fun TestScope.runBaseLevelTwoFailureScenario(
        targetCategory: AssessmentCategory,
        skillArea: String
    ): List<Int> {
        val activityRepository = FakeActivityRepository(buildSyntheticActivities())
        val levelMasteryRepository = FakeLevelMasteryRepository()
        val viewModel = AssessmentViewModel(
            assessmentPlannerService = plannerService(activityRepository),
            assessmentRepository = FakeAssessmentRepository(),
            childProfileRepository = FakeChildProfileRepository(),
            childRepository = FakeChildRepository(),
            sessionRepository = FakeSessionRepository(),
            activityResultRepository = FakeActivityResultRepository(),
            speechRecognitionManager = speechRecognitionManager(),
            levelMasteryRepository = levelMasteryRepository,
            learningContentRepository = FakeLearningContentRepository(),
            overallProgressCalculator = overallProgressCalculator(FakeLearningContentRepository(), levelMasteryRepository),
            childStatusEvaluator = childStatusEvaluator(),
            notificationService = notificationHandler()
        )

        viewModel.startAssessment(1L)
        advanceUntilIdle()
        viewModel.beginActivities()
        advanceUntilIdle()

        repeat(60) {
            val state = viewModel.uiState.value
            if (state is AssessmentUiState.Complete) return@repeat
            if (state !is AssessmentUiState.Playing) {
                advanceUntilIdle()
                return@repeat
            }

            val category = assessmentCategoryFor(state.currentActivityId)
            val isWarmUp = !state.isTest
            val score = if (category == targetCategory && !isWarmUp) 0 else 1

            viewModel.onActivityComplete(score = score, total = 1)
            advanceUntilIdle()
        }

        return levelMasteryRepository.getForSkill(1L, skillArea).map { it.level }.sorted()
    }

    private class FakeActivityRepository(
        private val activities: Map<String, ActivityWithContent>
    ) : ActivityRepository {
        fun requireActivity(activityId: String): ActivityWithContent =
            checkNotNull(activities[activityId]) { "Missing activity $activityId" }

        override suspend fun seedActivities(activities: List<Activity>) = Unit
        override suspend fun getAll(): List<Activity> = this.activities.values.map { it.activity }
        override suspend fun getFiltered(modality: String, skillArea: String, difficulty: Int): List<Activity> = emptyList()
        override suspend fun count(): Int = activities.size
        override suspend fun getActivityWithContent(activityId: String): ActivityWithContent? = activities[activityId]
        override suspend fun getActivitiesForPlanning(skillArea: String, difficultyLevel: Int): List<Activity> = emptyList()
        override suspend fun getById(activityId: String): Activity? = activities[activityId]?.activity
    }

    private class FakeChildRepository : ChildRepository {
        private val child = Child(
            id = 1L,
            userId = 10L,
            name = "Test Child",
            age = 4,
            status = ChildStatus.ACTIVE
        )

        override suspend fun createChild(child: Child): Long = child.id
        override suspend fun updateChild(child: Child) = Unit
        override suspend fun deleteChild(child: Child) = Unit
        override fun getChildrenByUser(userId: Long): Flow<List<Child>> = flowOf(listOf(child))
        override suspend fun getById(id: Long): Child? = child.takeIf { it.id == id }
        override fun observeById(id: Long): Flow<Child?> = MutableStateFlow(getByIdSync(id))

        private fun getByIdSync(id: Long): Child? = child.takeIf { it.id == id }
    }

    private class FakeChildProfileRepository : ChildProfileRepository {
        private var profile: ChildProfile? = null

        override suspend fun createProfile(profile: ChildProfile) {
            this.profile = profile
        }

        override suspend fun updateProfile(profile: ChildProfile) {
            this.profile = profile
        }

        override suspend fun getByChildId(childId: Long): ChildProfile? = profile

        override suspend fun upsert(profile: ChildProfile) {
            this.profile = profile
        }

        override fun observeProfile(childId: Long): Flow<ChildProfile?> = MutableStateFlow(profile)

        fun peekProfile(): ChildProfile? = profile
    }

    private class FakeAssessmentRepository : AssessmentRepository {
        override suspend fun save(result: AssessmentResultEntity): Long = 1L
        override suspend fun getLatestForChild(childId: Long): AssessmentResultEntity? = null
        override suspend fun hasAssessment(childId: Long): Boolean = false
        override suspend fun deleteForChild(childId: Long) = Unit
    }

    private class FakeSessionRepository : SessionRepository {
        override suspend fun startSession(session: Session): Long = 1L
        override suspend fun endSession(sessionId: Long, endTime: Long) = Unit
        override suspend fun getSessionById(sessionId: Long): Session? = null
        override fun getSessionsByChild(childId: Long): Flow<List<Session>> = flowOf(emptyList())
        override suspend fun getRecentSessions(childId: Long, limit: Int): List<Session> = emptyList()
        override suspend fun getAllSessions(childId: Long): List<Session> = emptyList()
        override fun countByChild(childId: Long): Flow<Int> = flowOf(0)
        override suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow> = emptyList()
    }

    private class FakeActivityResultRepository : ActivityResultRepository {
        override suspend fun saveResult(result: ActivityResult): Long = 1L
        override suspend fun updateScore(resultId: Long, score: Float) = Unit
        override suspend fun getBySession(sessionId: Long): List<ActivityResult> = emptyList()
        override suspend fun getByChild(childId: Long): List<ActivityResult> = emptyList()
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

    private class FakeLearningContentRepository : LearningContentRepository {
        private val contentById = buildLearningContent().associateBy { it.id }

        override suspend fun seedContent(contentList: List<com.babybloom.domain.model.LearningContent>): List<Long> = emptyList()
        override suspend fun getById(id: String): com.babybloom.domain.model.LearningContent? = contentById[id]
        override suspend fun getByCategory(category: String): List<com.babybloom.domain.model.LearningContent> =
            contentById.values.filter { it.category == category }
        override suspend fun getContentForActivity(activityId: String): List<com.babybloom.domain.model.LearningContent> = emptyList()
        override suspend fun count(): Int = contentById.size

        private fun buildLearningContent(): List<com.babybloom.domain.model.LearningContent> = listOf(
            com.babybloom.domain.model.LearningContent("number_1", "1", "NUMBER", 1, 1),
            com.babybloom.domain.model.LearningContent("number_2", "2", "NUMBER", 1, 2),
            com.babybloom.domain.model.LearningContent("number_3", "3", "NUMBER", 1, 3),
            com.babybloom.domain.model.LearningContent("number_4", "4", "NUMBER", 2, 4),
            com.babybloom.domain.model.LearningContent("number_5", "5", "NUMBER", 2, 5),
            com.babybloom.domain.model.LearningContent("number_6", "6", "NUMBER", 2, 6),
            com.babybloom.domain.model.LearningContent("number_7", "7", "NUMBER", 3, 7),
            com.babybloom.domain.model.LearningContent("number_8", "8", "NUMBER", 3, 8),
            com.babybloom.domain.model.LearningContent("number_9", "9", "NUMBER", 3, 9),
            com.babybloom.domain.model.LearningContent("color_yellow", "yellow", "COLOR", 2, 1),
            com.babybloom.domain.model.LearningContent("color_green", "green", "COLOR", 2, 2),
            com.babybloom.domain.model.LearningContent("color_black", "black", "COLOR", 2, 3),
            com.babybloom.domain.model.LearningContent("color_orange", "orange", "COLOR", 3, 4),
            com.babybloom.domain.model.LearningContent("color_pink", "pink", "COLOR", 3, 5),
            com.babybloom.domain.model.LearningContent("color_brown", "brown", "COLOR", 3, 6),
            com.babybloom.domain.model.LearningContent("shape_square", "square", "SHAPE", 2, 1),
            com.babybloom.domain.model.LearningContent("shape_triangle", "triangle", "SHAPE", 3, 2),
            com.babybloom.domain.model.LearningContent("letter_alef", "alef", "LETTER_NAME", 1, 1),
            com.babybloom.domain.model.LearningContent("letter_ba", "ba", "LETTER_NAME", 1, 2),
            com.babybloom.domain.model.LearningContent("letter_ha", "ha", "LETTER_NAME", 1, 3),
            com.babybloom.domain.model.LearningContent("letter_meem", "meem", "LETTER_NAME", 1, 4),
            com.babybloom.domain.model.LearningContent("letter_jeem", "jeem", "LETTER_NAME", 1, 5),
            com.babybloom.domain.model.LearningContent("letter_dal", "dal", "LETTER_NAME", 1, 6),
            com.babybloom.domain.model.LearningContent("letter_kha", "kha", "LETTER_NAME", 2, 7),
            com.babybloom.domain.model.LearningContent("letter_ta", "ta", "LETTER_NAME", 2, 8),
            com.babybloom.domain.model.LearningContent("letter_seen", "seen", "LETTER_NAME", 2, 9),
            com.babybloom.domain.model.LearningContent("animal_lion", "lion", "ANIMAL", 1, 1),
            com.babybloom.domain.model.LearningContent("animal_duck", "duck", "ANIMAL", 1, 2),
            com.babybloom.domain.model.LearningContent("animal_goat", "goat", "ANIMAL", 1, 3),
            com.babybloom.domain.model.LearningContent("animal_fish", "fish", "ANIMAL", 2, 4),
            com.babybloom.domain.model.LearningContent("animal_llama", "llama", "ANIMAL", 2, 5),
            com.babybloom.domain.model.LearningContent("animal_dove", "dove", "ANIMAL", 2, 6)
        )
    }

    private class FakeLevelMasteryRepository : LevelMasteryRepository {
        private val rows = mutableListOf<LevelMasteryEntity>()

        override suspend fun upsert(entity: LevelMasteryEntity) {
            rows.removeAll {
                it.childId == entity.childId &&
                    it.skillArea == entity.skillArea &&
                    it.level == entity.level &&
                    it.contentId == entity.contentId
            }
            rows += entity
        }

        override suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity? =
            rows.lastOrNull {
                it.childId == childId &&
                    it.skillArea == skillArea &&
                    it.level == level &&
                    it.contentId.isBlank()
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

        override suspend fun deleteAllForChild(childId: Long) {
            rows.removeAll { it.childId == childId }
        }
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

    private fun <T> allocateWithoutConstructor(type: Class<T>): T =
        runCatching {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null)
            @Suppress("UNCHECKED_CAST")
            unsafeClass.getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, type) as T
        }.getOrElse {
            @Suppress("UNCHECKED_CAST")
            Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class::class.java)
                .invoke(null, type) as T
        }

}
