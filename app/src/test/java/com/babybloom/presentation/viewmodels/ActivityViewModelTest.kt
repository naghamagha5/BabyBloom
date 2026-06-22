package com.babybloom.presentation.viewmodels

import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.data.local.entity.LevelMasteryEntity
import com.babybloom.data.local.entity.SkillScoreRow
import com.babybloom.di.AppSoundSettings
import com.babybloom.di.NormalSessionProgressStore
import com.babybloom.domain.algorithm.AdaptiveAlgorithmEngine
import com.babybloom.domain.algorithm.SessionPlannerService
import com.babybloom.domain.model.Activity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.InteractionEvent
import com.babybloom.domain.model.LearningContent
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.model.Session
import com.babybloom.domain.model.SessionDecision
import com.babybloom.domain.model.SessionPhase
import com.babybloom.domain.model.User
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.InteractionEventRepository
import com.babybloom.domain.repository.LearningContentRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import com.babybloom.domain.repository.SessionRepository
import com.babybloom.domain.repository.UserRepository
import com.babybloom.domain.progress.OverallProgressCalculator
import com.babybloom.domain.status.ChildStatusEvaluator
import com.babybloom.util.SessionQueueCodec
import com.babybloom.util.attention.AttentionDetector
import com.babybloom.util.speech.SpeechRecognitionManager
import java.lang.reflect.Field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

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
    fun `completed-state exit progress advances to next learning step instead of stale revision step`() {
        val queue = listOf(
            ActivityLaunchStep("listen_choose_letters_d1", "letter_ba", targetContentId = "letter_ba", isTest = true, phase = SessionPhase.REVISION),
            ActivityLaunchStep("listen_choose_colors_d1", "color_red", targetContentId = "color_red", isTest = true, phase = SessionPhase.REVISION),
            ActivityLaunchStep("story_letters_d1", "letter_new", targetContentId = "letter_new", isTest = false, phase = SessionPhase.LEARNING)
        )

        val progress = NormalSessionExitProgressPlanner.progressForCompletedState(
            childId = 1L,
            sessionId = 55L,
            decision = SessionDecision.Next(
                activityId = "story_letters_d1",
                contentId = "letter_new"
            ),
            sessionQueue = queue,
            currentStepIndex = 1
        )

        assertEquals(2, progress?.stepIndex)
        assertEquals("story_letters_d1", SessionQueueCodec.decode(progress?.encodedQueue).getOrNull(2)?.activityId)
    }

    @Test
    fun `completed-state exit progress is cleared when no next step exists`() {
        val progress = NormalSessionExitProgressPlanner.progressForCompletedState(
            childId = 1L,
            sessionId = 55L,
            decision = SessionDecision.SessionComplete,
            sessionQueue = emptyList(),
            currentStepIndex = 0
        )

        assertEquals(null, progress)
    }

    @Test
    fun `revision content score updates immediately after final activity before session end`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 99L
            val existingScore = 0.40f

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "خ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 7L,
                        childId = childId,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_kha",
                        contentScore = existingScore,
                        lastUpdated = 1L
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "letter_kha")
            val finalRevisionStep = revisionSteps.last()
            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )

            revisionSteps.dropLast(1).forEachIndexed { index, step ->
                val seeded = seededResult(
                    sessionId = sessionId,
                    childId = childId,
                    activityId = step.activityId,
                    contentId = "letter_kha",
                    timestamp = (index + 1) * 100L,
                    activityRepository = activityRepository,
                    algorithmEngine = algorithmEngine
                )
                activityResultRepository.saveResult(seeded)
            }

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .filter { it.contentId == "letter_kha" }
                .associateBy { it.activityId }
            val revisionAverage = revisionSteps
                .map { latestScores.getValue(it.activityId).score }
                .average()
                .toFloat()
            val expectedMergedScore = (existingScore + revisionAverage) / 2f
            val updatedMastery = levelMasteryRepository.getByContentId(childId, "letter_kha")

            assertEquals(expectedMergedScore, updatedMastery?.contentScore ?: -1f, 0.0001f)
            assertTrue(updatedMastery!!.lastUpdated > 1L)
            assertTrue(sessionRepository.endedSessionIds.isEmpty())

            val completed = viewModel.uiState.value as ActivityUiState.Completed
            assertTrue(completed.decision is SessionDecision.Next)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `resumed test completion persists content score using results across sessions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val priorSessionId = 88L
            val resumedSessionId = 99L

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "Ø®", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "Ø¯", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = resumedSessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(mutableListOf())
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val testSteps = listOf(
                ActivityLaunchStep("speech_letters_d1", "letter_kha", targetContentId = "letter_kha", isTest = true, phase = SessionPhase.TEST),
                ActivityLaunchStep("match_letters_d1", "letter_kha", targetContentId = "letter_kha", isTest = true, phase = SessionPhase.TEST),
                ActivityLaunchStep("trace_letters_d1", "letter_kha", targetContentId = "letter_kha", isTest = true, phase = SessionPhase.TEST),
                ActivityLaunchStep("story_letters_d1", "letter_extra", targetContentId = "letter_extra", isTest = false, phase = SessionPhase.LEARNING)
            )
            val finalTestStep = testSteps[2]

            activityResultRepository.saveResult(
                seededResult(
                    sessionId = priorSessionId,
                    childId = childId,
                    activityId = testSteps[0].activityId,
                    contentId = "letter_kha",
                    timestamp = 100L,
                    activityRepository = activityRepository,
                    algorithmEngine = algorithmEngine
                )
            )
            activityResultRepository.saveResult(
                seededResult(
                    sessionId = priorSessionId,
                    childId = childId,
                    activityId = testSteps[1].activityId,
                    contentId = "letter_kha",
                    timestamp = 200L,
                    activityRepository = activityRepository,
                    algorithmEngine = algorithmEngine
                )
            )

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", resumedSessionId)
            setPrivateField(viewModel, "sessionQueue", testSteps)
            setPrivateField(viewModel, "currentStepIndex", 2)
            setPrivateField(viewModel, "currentStep", finalTestStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalTestStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalTestStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = 2,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .filter { it.contentId == "letter_kha" }
                .associateBy { it.activityId }
            val expectedAverage = listOf(
                latestScores.getValue("speech_letters_d1").score,
                latestScores.getValue("match_letters_d1").score,
                latestScores.getValue("trace_letters_d1").score
            ).average().toFloat()

            assertEquals(expectedAverage, levelMasteryRepository.getByContentId(childId, "letter_kha")?.contentScore ?: -1f, 0.0001f)
            val completed = viewModel.uiState.value as ActivityUiState.Completed
            assertTrue(completed.decision is SessionDecision.Next)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `after resumed revision and test completion session continues into revision instead of ending`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 120L

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("speech_letters_d2", "LANGUAGE", 2, "SPEECH", "letter_ba"),
                activity("match_letters_d2", "LANGUAGE", 2, "MATCH", "letter_ba"),
                activity("trace_letters_d2", "LANGUAGE", 2, "TRACE", "letter_ba"),
                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("speech_numbers_d1", "NUMERACY", 1, "SPEECH", "number_1"),
                activity("count_d1", "NUMERACY", 1, "COUNT", "number_1"),
                activity("trace_numbers_d1", "NUMERACY", 1, "TRACE", "number_1")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "Ø®", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_ba", "Ø¨", "LETTER_NAME", difficultyLevel = 2, learningOrder = 2),
                LearningContent("animal_lion", "lion", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("number_1", "one", "NUMBER", difficultyLevel = 1, learningOrder = 1)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 2,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(childId = childId, skillArea = "LETTER_NAME", level = 1, contentId = "letter_kha", contentScore = 0.75f, lastUpdated = 10L),
                    LevelMasteryEntity(childId = childId, skillArea = "LETTER_NAME", level = 2, contentId = "letter_ba", contentScore = 0.20f, lastUpdated = 20L),
                    LevelMasteryEntity(childId = childId, skillArea = "ANIMAL", level = 1, contentId = "animal_lion", contentScore = 0.30f, lastUpdated = 30L),
                    LevelMasteryEntity(childId = childId, skillArea = "NUMBER", level = 1, contentId = "number_1", contentScore = 0.40f, lastUpdated = 40L)
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()

            val queue = listOf(
                ActivityLaunchStep("speech_letters_d1", "letter_kha", targetContentId = "letter_kha", isTest = true, phase = SessionPhase.REVISION),
                ActivityLaunchStep("speech_letters_d2", "letter_ba", targetContentId = "letter_ba", isTest = false, phase = SessionPhase.LEARNING),
                ActivityLaunchStep("match_letters_d2", "letter_ba", targetContentId = "letter_ba", isTest = true, phase = SessionPhase.TEST),
                ActivityLaunchStep("trace_letters_d2", "letter_ba", targetContentId = "letter_ba", isTest = true, phase = SessionPhase.TEST)
            )
            val finalTestStep = queue.last()

            activityResultRepository.saveResult(
                seededResult(
                    sessionId = sessionId,
                    childId = childId,
                    activityId = "match_letters_d2",
                    contentId = "letter_ba",
                    timestamp = 100L,
                    activityRepository = activityRepository,
                    algorithmEngine = algorithmEngine
                )
            )

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", queue.lastIndex)
            setPrivateField(viewModel, "currentStep", finalTestStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalTestStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalTestStep.activityId, "letter_ba", 0))
                ),
                currentIndex = 0,
                stepIndex = queue.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = false,
                contentId = "letter_ba",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val completed = viewModel.uiState.value as ActivityUiState.Completed
            assertTrue(completed.decision is SessionDecision.Next)
            val decision = completed.decision as SessionDecision.Next
            val nextQueue = SessionQueueCodec.decode(decision.encodedQueue)
            assertTrue(nextQueue.drop(queue.size).any { it.phase == SessionPhase.REVISION })
            assertTrue(sessionRepository.endedSessionIds.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `assessment-seeded zero content score is replaced by first revision average directly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 98L

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "خ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 6L,
                        childId = childId,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_kha",
                        contentScore = 0f,
                        lastUpdated = 1L
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "letter_kha")
            val finalRevisionStep = revisionSteps.last()
            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )

            revisionSteps.dropLast(1).forEachIndexed { index, step ->
                activityResultRepository.saveResult(
                    seededResult(
                        sessionId = sessionId,
                        childId = childId,
                        activityId = step.activityId,
                        contentId = "letter_kha",
                        timestamp = (index + 1) * 100L,
                        activityRepository = activityRepository,
                        algorithmEngine = algorithmEngine
                    )
                )
            }

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .filter { it.contentId == "letter_kha" }
                .associateBy { it.activityId }
            val revisionAverage = revisionSteps
                .map { latestScores.getValue(it.activityId).score }
                .average()
                .toFloat()
            val updatedMastery = levelMasteryRepository.getByContentId(childId, "letter_kha")

            assertEquals(revisionAverage, updatedMastery?.contentScore ?: -1f, 0.0001f)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `assessment-seeded animal content updates after revision even when one step uses mapped letter content`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 97L

            val activities = mapOf(
                activity("speech_animals_d1", "LANGUAGE", 1, "SPEECH", "animal_lion"),
                activity("match_animals_d1", "LANGUAGE", 1, "MATCH", "animal_lion"),
                activity("listen_choose_animals_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "animal_lion"),
                activity("drag_letters_d1", "MOTOR", 1, "DRAG", "letter_alef"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("animal_lion", "أسد", "ANIMAL", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_alef", "أ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 5L,
                        childId = childId,
                        skillArea = "ANIMAL",
                        level = 1,
                        contentId = "animal_lion",
                        contentScore = 0f,
                        lastUpdated = 1L
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "animal_lion")
            val finalRevisionStep = revisionSteps.last()
            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )

            revisionSteps.dropLast(1).forEachIndexed { index, step ->
                activityResultRepository.saveResult(
                    seededResult(
                        sessionId = sessionId,
                        childId = childId,
                        activityId = step.activityId,
                        contentId = requireNotNull(step.contentId),
                        timestamp = (index + 1) * 100L,
                        activityRepository = activityRepository,
                        algorithmEngine = algorithmEngine
                    )
                )
            }

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, requireNotNull(finalRevisionStep.contentId), 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = requireNotNull(finalRevisionStep.contentId),
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .associateBy { "${it.activityId}:${it.contentId}" }
            val revisionAverage = revisionSteps
                .map { latestScores.getValue("${it.activityId}:${it.contentId}").score }
                .average()
                .toFloat()
            val updatedMastery = levelMasteryRepository.getByContentId(childId, "animal_lion")

            assertEquals(revisionAverage, updatedMastery?.contentScore ?: -1f, 0.0001f)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `revision content score still updates when parent exits immediately after final remaining activity`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 100L
            val existingScore = 0.35f

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "خ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 8L,
                        childId = childId,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_kha",
                        contentScore = existingScore,
                        lastUpdated = 1L
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "letter_kha")
            val finalRevisionStep = revisionSteps.last()
            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )

            revisionSteps.dropLast(1).forEachIndexed { index, step ->
                activityResultRepository.saveResult(
                    seededResult(
                        sessionId = sessionId,
                        childId = childId,
                        activityId = step.activityId,
                        contentId = "letter_kha",
                        timestamp = (index + 1) * 100L,
                        activityRepository = activityRepository,
                        algorithmEngine = algorithmEngine
                    )
                )
            }

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            viewModel.pauseNormalSessionForExit()
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .filter { it.contentId == "letter_kha" }
                .associateBy { it.activityId }
            val revisionAverage = revisionSteps
                .map { latestScores.getValue(it.activityId).score }
                .average()
                .toFloat()
            val expectedMergedScore = (existingScore + revisionAverage) / 2f
            val updatedMastery = levelMasteryRepository.getByContentId(childId, "letter_kha")

            assertEquals(expectedMergedScore, updatedMastery?.contentScore ?: -1f, 0.0001f)
            assertTrue(sessionRepository.endedSessionIds.contains(sessionId))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `new revision cycle does not reuse older activity results before mastery timestamp`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val sessionId = 101L
            val priorMasteryTimestamp = 1_000L
            val existingScore = 0.72f

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "خ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = sessionId,
                    userId = userId,
                    childId = childId,
                    startTime = 2_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 9L,
                        childId = childId,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_kha",
                        contentScore = existingScore,
                        lastUpdated = priorMasteryTimestamp
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val appSoundSettings = allocateAppSoundSettings()
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "letter_kha")
            val finalRevisionStep = revisionSteps.last()
            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )

            // Older completed revision set that should not be reused for the new cycle.
            revisionSteps.forEachIndexed { index, step ->
                activityResultRepository.saveResult(
                    seededResult(
                        sessionId = 88L,
                        childId = childId,
                        activityId = step.activityId,
                        contentId = "letter_kha",
                        timestamp = 100L + index,
                        activityRepository = activityRepository,
                        algorithmEngine = algorithmEngine
                    )
                )
            }

            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            setPrivateField(viewModel, "sessionId", sessionId)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            // Only one fresh activity exists in the new cycle, so mastery must not update yet.
            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val updatedMastery = levelMasteryRepository.getByContentId(childId, "letter_kha")
            assertEquals(existingScore, updatedMastery?.contentScore ?: -1f, 0.0001f)
            assertEquals(priorMasteryTimestamp, updatedMastery?.lastUpdated ?: -1L)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `revision score updates only after remaining activities complete across interrupted sessions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val childId = 1L
            val userId = 10L
            val existingScore = 0f

            val activities = mapOf(
                activity("speech_letters_d1", "LANGUAGE", 1, "SPEECH", "letter_kha"),
                activity("match_letters_d1", "LANGUAGE", 1, "MATCH", "letter_kha"),
                activity("trace_letters_d1", "LANGUAGE", 1, "TRACE", "letter_kha"),
                activity("listen_choose_letters_d1", "LANGUAGE", 1, "LISTEN_AND_CHOOSE", "letter_kha"),
                activity("story_letters_d1", "LANGUAGE", 1, "STORY", "letter_extra")
            )
            val learningContent = listOf(
                LearningContent("letter_kha", "خ", "LETTER_NAME", difficultyLevel = 1, learningOrder = 1),
                LearningContent("letter_extra", "د", "LETTER_NAME", difficultyLevel = 1, learningOrder = 2)
            )
            val profile = ChildProfile(
                childId = childId,
                assessmentCompleted = true,
                languageLevel = 1,
                numeracyLevel = 1,
                motorLevel = 1,
                dominantModality = "VISUAL",
                visualPreferencePercent = 100f,
                audioPreferencePercent = 0f,
                interactivePreferencePercent = 0f
            )

            val activityRepository = FakeActivityRepository(activities)
            val activityResultRepository = FakeActivityResultRepository()
            val childProfileRepository = FakeChildProfileRepository(profile)
            val childRepository = FakeChildRepository(
                Child(
                    id = childId,
                    userId = userId,
                    name = "Mariam",
                    age = 4,
                    status = ChildStatus.ACTIVE,
                    sessionDurationMinutes = 10,
                    soundEffectEnabled = false,
                    backgroundMusicEnabled = false,
                    uiTheme = false
                )
            )
            val userRepository = FakeUserRepository(
                User(
                    id = userId,
                    name = "Parent",
                    email = "parent@example.com",
                    passwordHash = "hash"
                )
            )
            val sessionRepository = FakeSessionRepository(
                Session(
                    id = 201L,
                    userId = userId,
                    childId = childId,
                    startTime = 1_000L,
                    isAssessment = false
                )
            )
            val interactionEventRepository = FakeInteractionEventRepository()
            val levelMasteryRepository = FakeLevelMasteryRepository(
                mutableListOf(
                    LevelMasteryEntity(
                        id = 10L,
                        childId = childId,
                        skillArea = "LETTER_NAME",
                        level = 1,
                        contentId = "letter_kha",
                        contentScore = existingScore,
                        lastUpdated = 1L
                    )
                )
            )
            val learningContentRepository = FakeLearningContentRepository(learningContent)
            val algorithmEngine = AdaptiveAlgorithmEngine()
            val sessionPlannerService = SessionPlannerService(
                activityRepository = activityRepository,
                learningContentRepository = learningContentRepository,
                activityResultRepository = activityResultRepository,
                algorithmEngine = algorithmEngine,
                levelMasteryRepository = levelMasteryRepository
            )
            val revisionSteps = sessionPlannerService.buildRevisionStepsForContent(profile, "letter_kha")

            // Session 1 interrupted: first two activities completed only.
            revisionSteps.take(2).forEachIndexed { index, step ->
                activityResultRepository.saveResult(
                    seededResult(
                        sessionId = 201L,
                        childId = childId,
                        activityId = step.activityId,
                        contentId = "letter_kha",
                        timestamp = 100L + index,
                        activityRepository = activityRepository,
                        algorithmEngine = algorithmEngine
                    )
                )
            }

            var mastery = levelMasteryRepository.getByContentId(childId, "letter_kha")
            assertEquals(existingScore, mastery?.contentScore ?: -1f, 0.0001f)

            // Session 2 interrupted again: third activity completed, still incomplete set.
            activityResultRepository.saveResult(
                seededResult(
                    sessionId = 202L,
                    childId = childId,
                    activityId = revisionSteps[2].activityId,
                    contentId = "letter_kha",
                    timestamp = 300L,
                    activityRepository = activityRepository,
                    algorithmEngine = algorithmEngine
                )
            )

            mastery = levelMasteryRepository.getByContentId(childId, "letter_kha")
            assertEquals(existingScore, mastery?.contentScore ?: -1f, 0.0001f)

            // Session 3 resumes with the final remaining activity and must update immediately.
            sessionRepository.putSession(
                Session(
                    id = 203L,
                    userId = userId,
                    childId = childId,
                    startTime = 4_000L,
                    isAssessment = false
                )
            )
            val appSoundSettings = allocateAppSoundSettings()
            val viewModel = ActivityViewModel(
                activityRepository = activityRepository,
                activityResultRepository = activityResultRepository,
                childProfileRepository = childProfileRepository,
                childRepository = childRepository,
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                interactionEventRepository = interactionEventRepository,
                levelMasteryRepository = levelMasteryRepository,
                learningContentRepository = learningContentRepository,
                algorithmEngine = algorithmEngine,
                sessionPlannerService = sessionPlannerService,
                speechRecognitionManager = allocateWithoutConstructor(SpeechRecognitionManager::class.java),
                appSoundSettings = appSoundSettings,
                normalSessionProgressStore = allocateWithoutConstructor(NormalSessionProgressStore::class.java),
                attentionDetector = allocateWithoutConstructor(AttentionDetector::class.java),
                overallProgressCalculator = overallProgressCalculator(learningContentRepository, levelMasteryRepository),
                childStatusEvaluator = childStatusEvaluator(),
                notificationService = notificationHandler()
            )

            val queue = revisionSteps + ActivityLaunchStep(
                activityId = "story_letters_d1",
                contentId = "letter_extra",
                isTest = false,
                phase = SessionPhase.LEARNING
            )
            val finalRevisionStep = revisionSteps.last()
            setPrivateField(viewModel, "sessionId", 203L)
            setPrivateField(viewModel, "sessionQueue", queue)
            setPrivateField(viewModel, "currentStepIndex", revisionSteps.lastIndex)
            setPrivateField(viewModel, "currentStep", finalRevisionStep)
            activityStateFlow(viewModel).value = ActivityUiState.Playing(
                activityWithContent = activities.getValue(finalRevisionStep.activityId).copy(
                    contentItems = listOf(ActivityContent(finalRevisionStep.activityId, "letter_kha", 0))
                ),
                currentIndex = 0,
                stepIndex = revisionSteps.lastIndex,
                score = 0,
                totalAttempts = 0,
                sessionSettings = ActivitySessionSettings(
                    isCalmMode = false,
                    soundEffectsEnabled = false,
                    backgroundMusicEnabled = false,
                    sessionDurationMs = 600_000L,
                    childId = childId,
                    userId = userId,
                    hasParentPin = false,
                    isAssessment = false,
                    isTest = true
                ),
                sessionRemainingMs = 300_000L
            )

            viewModel.onAnswerSubmitted(
                isCorrect = true,
                contentId = "letter_kha",
                responseTimeMs = 2_500L,
                attempts = 1
            )
            advanceUntilIdle()

            val latestScores = activityResultRepository.getByChild(childId)
                .filter { it.contentId == "letter_kha" }
                .associateBy { it.activityId }
            val revisionAverage = revisionSteps
                .map { latestScores.getValue(it.activityId).score }
                .average()
                .toFloat()
            mastery = levelMasteryRepository.getByContentId(childId, "letter_kha")

            assertEquals(revisionAverage, mastery?.contentScore ?: -1f, 0.0001f)
            val completed = viewModel.uiState.value as ActivityUiState.Completed
            assertTrue(completed.decision is SessionDecision.Next)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun activity(
        id: String,
        skillArea: String,
        difficulty: Int,
        activityType: String,
        contentId: String
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
            contentItems = listOf(ActivityContent(id, contentId, orderIndex = 0))
        )

    private suspend fun seededResult(
        sessionId: Long,
        childId: Long,
        activityId: String,
        contentId: String,
        timestamp: Long,
        activityRepository: FakeActivityRepository,
        algorithmEngine: AdaptiveAlgorithmEngine
    ): ActivityResult {
        val activity = activityRepository.getById(activityId)!!
        val signal = ActivitySignal(
            childId = childId,
            activityId = activityId,
            skillArea = activity.skillArea,
            modality = activity.modality,
            activityType = activity.activityType,
            difficultyLevel = activity.difficultyLevel,
            correctCount = 1,
            incorrectCount = 0,
            attempts = 1,
            attentionScore = 0.8f,
            touchQualityScore = if (activity.activityType in setOf("MATCH", "TRACE")) 0.7f else null,
            speechConfidence = if (activity.activityType == "SPEECH") 0.9f else null,
            durationMs = 3_000L,
            expectedDurationMs = 60_000L
        )
        return ActivityResult(
            sessionId = sessionId,
            childId = childId,
            activityId = activityId,
            contentId = contentId,
            score = algorithmEngine.computeItemScore(signal),
            duration = 3_000L,
            correctCount = 1,
            incorrectCount = 0,
            attempts = 1,
            speechConfidence = signal.speechConfidence,
            touchQualityScore = signal.touchQualityScore,
            attentionScore = signal.attentionScore,
            timestamp = timestamp
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun activityStateFlow(viewModel: ActivityViewModel): MutableStateFlow<ActivityUiState> =
        getDeclaredField(ActivityViewModel::class.java, "_uiState").get(viewModel) as MutableStateFlow<ActivityUiState>

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        getDeclaredField(target.javaClass, name).set(target, value)
    }

    private fun getDeclaredField(type: Class<*>, name: String): Field =
        type.getDeclaredField(name).apply { isAccessible = true }

    private fun allocateAppSoundSettings(): AppSoundSettings =
        allocateWithoutConstructor(AppSoundSettings::class.java).also { soundSettings ->
            setPrivateField(soundSettings, "sfxMap", mutableMapOf<Any, Int>())
            setPrivateField(soundSettings, "loadedIds", mutableSetOf<Int>())
            setPrivateField(soundSettings, "pendingPlay", mutableListOf<Any>())
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(type: Class<T>): T =
        unsafeAllocateInstance.invoke(unsafe, type) as T

    private companion object {
        val unsafe: Any by lazy {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            requireNotNull(field.get(null))
        }
        val unsafeAllocateInstance by lazy {
            Class.forName("sun.misc.Unsafe").getMethod("allocateInstance", Class::class.java)
        }
    }

    private class FakeActivityRepository(
        private val activities: Map<String, ActivityWithContent>
    ) : ActivityRepository {
        override suspend fun seedActivities(activities: List<Activity>) = Unit
        override suspend fun getAll(): List<Activity> = this.activities.values.map { it.activity }
        override suspend fun getFiltered(modality: String, skillArea: String, difficulty: Int): List<Activity> = emptyList()
        override suspend fun count(): Int = activities.size
        override suspend fun getActivityWithContent(activityId: String): ActivityWithContent? = activities[activityId]
        override suspend fun getActivitiesForPlanning(skillArea: String, difficultyLevel: Int): List<Activity> =
            activities.values.map { it.activity }.filter { it.skillArea == skillArea && it.difficultyLevel == difficultyLevel }
        override suspend fun getById(activityId: String): Activity? = activities[activityId]?.activity
    }

    private class FakeActivityResultRepository : ActivityResultRepository {
        private val results = mutableListOf<ActivityResult>()
        private var nextId = 1L

        override suspend fun saveResult(result: ActivityResult): Long {
            val saved = result.copy(id = nextId++)
            results += saved
            return saved.id
        }

        override suspend fun updateScore(resultId: Long, score: Float) {
            val index = results.indexOfFirst { it.id == resultId }
            if (index >= 0) {
                results[index] = results[index].copy(score = score)
            }
        }

        override suspend fun getBySession(sessionId: Long): List<ActivityResult> =
            results.filter { it.sessionId == sessionId }

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
        override suspend fun getForSession(sessionId: Long): List<ActivityResult> =
            results.filter { it.sessionId == sessionId }
    }

    private class FakeChildProfileRepository(
        initialProfile: ChildProfile
    ) : ChildProfileRepository {
        private val profileFlow = MutableStateFlow(initialProfile)

        override suspend fun createProfile(profile: ChildProfile) {
            profileFlow.value = profile
        }

        override suspend fun updateProfile(profile: ChildProfile) {
            profileFlow.value = profile
        }

        override suspend fun getByChildId(childId: Long): ChildProfile? =
            profileFlow.value.takeIf { it.childId == childId }

        override suspend fun upsert(profile: ChildProfile) {
            profileFlow.value = profile
        }

        override fun observeProfile(childId: Long): Flow<ChildProfile?> =
            profileFlow
    }

    private class FakeChildRepository(
        private val child: Child
    ) : ChildRepository {
        override suspend fun createChild(child: Child): Long = child.id
        override suspend fun updateChild(child: Child) = Unit
        override suspend fun deleteChild(child: Child) = Unit
        override fun getChildrenByUser(userId: Long): Flow<List<Child>> = flowOf(listOf(child))
        override suspend fun getById(id: Long): Child? = child.takeIf { it.id == id }
        override fun observeById(id: Long): Flow<Child?> = flowOf(child.takeIf { it.id == id })
    }

    private class FakeUserRepository(
        private val user: User
    ) : UserRepository {
        override suspend fun register(user: User): Long = user.id
        override suspend fun getByEmail(email: String): User? = this.user.takeIf { it.email == email }
        override suspend fun getById(id: Long): User? = user.takeIf { it.id == id }
        override suspend fun emailExists(email: String): Boolean = user.email == email
        override suspend fun setParentLockPin(userId: Long, rawPin: String) = Unit
        override suspend fun verifyParentLockPin(userId: Long, enteredPin: String): Boolean = false
        override suspend fun verifyParentPassword(userId: Long, enteredPassword: String): Boolean = false
    }

    private class FakeSessionRepository(
        session: Session
    ) : SessionRepository {
        private val sessions = mutableMapOf(session.id to session)
        val endedSessionIds = mutableListOf<Long>()

        fun putSession(session: Session) {
            sessions[session.id] = session
        }

        override suspend fun startSession(session: Session): Long {
            sessions[session.id] = session
            return session.id
        }

        override suspend fun endSession(sessionId: Long, endTime: Long) {
            endedSessionIds += sessionId
            sessions[sessionId] = sessions[sessionId]?.copy(endTime = endTime) ?: return
        }

        override suspend fun getSessionById(sessionId: Long): Session? = sessions[sessionId]
        override fun getSessionsByChild(childId: Long): Flow<List<Session>> = flowOf(sessions.values.filter { it.childId == childId })
        override suspend fun getRecentSessions(childId: Long, limit: Int): List<Session> = sessions.values.filter { it.childId == childId }.take(limit)
        override suspend fun getAllSessions(childId: Long): List<Session> = sessions.values.filter { it.childId == childId }
        override fun countByChild(childId: Long): Flow<Int> = flowOf(sessions.values.count { it.childId == childId })
        override suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow> = emptyList()
    }

    private class FakeInteractionEventRepository : InteractionEventRepository {
        override suspend fun saveEvent(event: InteractionEvent): Long = 1L
        override suspend fun getBySessionAndType(sessionId: Long, eventType: String): List<InteractionEvent> = emptyList()
        override suspend fun getByChildAndActivity(childId: Long, activityId: String): List<InteractionEvent> = emptyList()
    }

    private class FakeLevelMasteryRepository(
        private val rows: MutableList<LevelMasteryEntity>
    ) : LevelMasteryRepository {
        override suspend fun upsert(entity: LevelMasteryEntity) {
            val index = rows.indexOfFirst {
                it.childId == entity.childId &&
                    it.skillArea == entity.skillArea &&
                    it.level == entity.level &&
                    it.contentId == entity.contentId
            }
            if (index >= 0) {
                rows[index] = entity
            } else {
                rows += entity
            }
        }

        override suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity? =
            rows.lastOrNull { it.childId == childId && it.skillArea == skillArea && it.level == level && it.contentId.isBlank() }

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

    private class FakeLearningContentRepository(
        private val content: List<LearningContent>
    ) : LearningContentRepository {
        override suspend fun seedContent(contentList: List<LearningContent>): List<Long> = emptyList()
        override suspend fun getById(id: String): LearningContent? = content.firstOrNull { it.id == id }
        override suspend fun getByCategory(category: String): List<LearningContent> = content.filter { it.category == category }
        override suspend fun getContentForActivity(activityId: String): List<LearningContent> = emptyList()
        override suspend fun count(): Int = content.size
    }
}
