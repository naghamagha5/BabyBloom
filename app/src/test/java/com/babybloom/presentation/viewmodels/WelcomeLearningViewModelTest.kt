package com.babybloom.presentation.viewmodels

import com.babybloom.di.NormalSessionProgress
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeLearningViewModelTest {

    @Test
    fun `finished saved session with zero remaining time is not resumed`() {
        val savedProgress = NormalSessionProgress(
            childId = 1L,
            sessionId = 22L,
            encodedQueue = "stale-queue",
            stepIndex = 3,
            remainingMs = 0L
        )

        assertFalse(NormalSessionResumePlanner.shouldResume(savedProgress))
    }

    @Test
    fun `parent exit progress still resumes before learning when time remains`() {
        val savedProgress = NormalSessionProgress(
            childId = 1L,
            sessionId = 22L,
            encodedQueue = "resume-queue",
            stepIndex = 3,
            remainingMs = 120_000L
        )

        assertTrue(NormalSessionResumePlanner.shouldResume(savedProgress))
    }

    @Test
    fun `completed state exit without timer still resumes next queued step`() {
        val savedProgress = NormalSessionProgress(
            childId = 1L,
            sessionId = 22L,
            encodedQueue = "resume-queue",
            stepIndex = 3,
            remainingMs = null
        )

        assertTrue(NormalSessionResumePlanner.shouldResume(savedProgress))
    }

    @Test
    fun `revision continuation keeps only remaining revision activities`() {
        val remaining = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_letters", "letter_b"),
            revisionStep("trace_letters", "letter_a"),
            revisionStep("listen_choose_letters", "letter_b"),
            revisionStep("speech_letters", "letter_c"),
            revisionStep("trace_letters", "letter_c"),
            ActivityLaunchStep("story_letters", "letter_d", isTest = false, phase = SessionPhase.LEARNING)
        )

        val continuation = RevisionContinuationPlanner.remainingOnly(remaining)

        assertEquals(
            listOf("letter_a", "letter_b", "letter_a", "letter_b", "letter_c", "letter_c"),
            continuation.mapNotNull { it.contentId }
        )
    }

    @Test
    fun `revision continuation excludes already completed activities before saved index`() {
        val remaining = listOf(
            revisionStep("trace_letters", "letter_c"),
            revisionStep("listen_choose_letters", "letter_c"),
            revisionStep("speech_letters", "letter_d"),
            ActivityLaunchStep("story_letters", "letter_e", isTest = false, phase = SessionPhase.LEARNING)
        )

        val continuation = RevisionContinuationPlanner.remainingOnly(remaining)

        assertEquals(
            listOf("letter_c", "letter_c", "letter_d"),
            continuation.mapNotNull { it.contentId }
        )
    }

    @Test
    fun `later batch interruption replays full current batch`() {
        val fullQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c"),
            revisionStep("speech_letters", "letter_d"),
            revisionStep("match_letters", "letter_e"),
            revisionStep("trace_letters", "letter_f")
        )

        assertFalse(RevisionContinuationPlanner.isFirstBatch(fullQueue, currentStepIndex = 4))
        assertEquals(
            listOf("letter_d", "letter_e", "letter_f"),
            RevisionContinuationPlanner.currentBatchContentIds(fullQueue, currentStepIndex = 4)
        )
    }

    @Test
    fun `first batch interruption is detected correctly`() {
        val fullQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c"),
            revisionStep("speech_letters", "letter_d")
        )

        assertTrue(RevisionContinuationPlanner.isFirstBatch(fullQueue, currentStepIndex = 1))
    }

    @Test
    fun `tail of first revision batch is still treated as first batch`() {
        val fullQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c"),
            revisionStep("listen_choose_letters", "letter_a"),
            revisionStep("speech_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c")
        )

        assertTrue(RevisionContinuationPlanner.isFirstBatch(fullQueue, currentStepIndex = 4))
        assertEquals(
            listOf("letter_a", "letter_b", "letter_c"),
            RevisionContinuationPlanner.currentBatchContentIds(fullQueue, currentStepIndex = 4)
        )
    }

    @Test
    fun `mixed-category first batch keeps the same batch ids at later activities`() {
        val fullQueue = listOf(
            revisionStep("speech_letters", "letter_ba"),
            revisionStep("match_colors", "color_red"),
            revisionStep("trace_shapes", "shape_circle"),
            revisionStep("listen_choose_letters", "letter_ba"),
            revisionStep("listen_choose_colors", "color_red"),
            revisionStep("listen_choose_shapes", "shape_circle"),
            revisionStep("speech_animals", "animal_lion"),
            revisionStep("match_letters", "letter_alef")
        )

        assertEquals(
            listOf("letter_ba", "color_red", "shape_circle"),
            RevisionContinuationPlanner.currentBatchContentIds(fullQueue, currentStepIndex = 5)
        )
        assertTrue(RevisionContinuationPlanner.isFirstBatch(fullQueue, currentStepIndex = 5))
    }

    @Test
    fun `first batch interruption with two remaining activities resumes only those activities before learning`() {
        val savedQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c"),
            revisionStep("listen_choose_letters", "letter_a"),
            revisionStep("speech_letters", "letter_b"),
            revisionStep("trace_letters", "letter_c")
        )
        val remaining = savedQueue.drop(4)
        val freshQueue = listOf(
            ActivityLaunchStep("story_letters", "letter_x", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_x", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_letters", "letter_y")
        )

        assertTrue(RevisionContinuationPlanner.isFirstBatch(savedQueue, currentStepIndex = 4))

        val resumedQueue = RevisionContinuationPlanner.remainingOnly(remaining) + freshQueue

        assertEquals(
            listOf(SessionPhase.REVISION, SessionPhase.REVISION, SessionPhase.LEARNING, SessionPhase.TEST, SessionPhase.REVISION),
            resumedQueue.map { it.phase }
        )
        assertEquals(
            listOf("letter_b", "letter_c"),
            resumedQueue.take(2).mapNotNull { it.contentId }
        )
    }

    @Test
    fun `first batch continuation excludes later revision batches from old queue`() {
        val savedQueue = listOf(
            revisionStep("speech_letters", "letter_ba"),
            revisionStep("match_colors", "color_red"),
            revisionStep("trace_shapes", "shape_circle"),
            revisionStep("listen_choose_letters", "letter_ba"),
            revisionStep("listen_choose_colors", "color_red"),
            revisionStep("listen_choose_shapes", "shape_circle"),
            revisionStep("speech_animals", "animal_lion"),
            revisionStep("match_letters", "letter_alef")
        )
        val remaining = RevisionContinuationPlanner.remainingCurrentBatchOnly(
            fullQueue = savedQueue,
            currentStepIndex = 4
        )
        val freshQueue = listOf(
            ActivityLaunchStep("story_letters", "letter_x", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_x", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_numbers", "number_one")
        )
        val resumedQueue = remaining + freshQueue

        assertTrue(RevisionContinuationPlanner.isFirstBatch(savedQueue, currentStepIndex = 4))
        assertEquals(
            listOf("color_red", "shape_circle"),
            remaining.mapNotNull { it.contentId }
        )
        assertFalse(remaining.any { it.contentId == "animal_lion" || it.contentId == "letter_alef" })
        assertEquals(SessionPhase.LEARNING, resumedQueue[2].phase)
    }

    @Test
    fun `first batch resume keeps remaining revision before learning and removes later fresh duplicates`() {
        val savedQueue = listOf(
            revisionStep("speech_letters", "letter_ba"),
            revisionStep("match_colors", "color_red"),
            revisionStep("trace_shapes", "shape_circle"),
            revisionStep("listen_choose_letters", "letter_ba"),
            revisionStep("listen_choose_colors", "color_red"),
            revisionStep("listen_choose_shapes", "shape_circle")
        )
        val remaining = RevisionContinuationPlanner.remainingCurrentBatchOnly(
            fullQueue = savedQueue,
            currentStepIndex = 3
        )
        val replayContentIds = RevisionContinuationPlanner.currentBatchContentIds(
            fullQueue = savedQueue,
            currentStepIndex = 3
        ).toSet()
        val freshQueue = listOf(
            ActivityLaunchStep("story_letters", "letter_new", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_new", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_letters", "letter_ba"),
            revisionStep("speech_shapes", "shape_circle"),
            revisionStep("speech_animals", "animal_lion")
        )

        val mergedQueue = RevisionContinuationPlanner.prependRemainingBatchBeforeLearning(
            freshQueue = freshQueue,
            remainingBatch = remaining,
            replayContentIds = replayContentIds
        )

        assertEquals(
            listOf("letter_ba", "color_red", "shape_circle"),
            mergedQueue.take(3).mapNotNull { it.contentId }
        )
        assertEquals(SessionPhase.LEARNING, mergedQueue[3].phase)
        assertEquals(SessionPhase.TEST, mergedQueue[4].phase)
        val laterFreshRevisionBlock = mergedQueue.drop(5)
        assertFalse(
            laterFreshRevisionBlock.any {
                it.phase == SessionPhase.REVISION &&
                    it.contentId in setOf("letter_ba", "color_red", "shape_circle")
            }
        )
        assertTrue(
            laterFreshRevisionBlock.any {
                it.phase == SessionPhase.REVISION && it.contentId == "animal_lion"
            }
        )
    }

    @Test
    fun `later interrupted revision batch resumes remaining activities then continues with learning test and fresh revision`() {
        val savedQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_animals", "animal_lion"),
            revisionStep("trace_numbers", "number_1"),
            revisionStep("listen_choose_letters", "letter_a"),
            revisionStep("speech_colors", "color_red"),
            revisionStep("match_shapes", "shape_circle"),
            revisionStep("listen_choose_colors", "color_red"),
            revisionStep("listen_choose_shapes", "shape_circle")
        )
        val remaining = RevisionContinuationPlanner.remainingCurrentBatchOnly(
            fullQueue = savedQueue,
            currentStepIndex = 6
        )
        val replayContentIds = RevisionContinuationPlanner.currentBatchContentIds(
            fullQueue = savedQueue,
            currentStepIndex = 6
        ).toSet()
        val freshQueue = listOf(
            ActivityLaunchStep("story_letters", "letter_new", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_new", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_colors", "color_red"),
            revisionStep("speech_shapes", "shape_circle"),
            revisionStep("speech_animals", "animal_duck")
        )

        val mergedQueue = RevisionContinuationPlanner.prependRemainingBatchBeforeLearning(
            freshQueue = freshQueue,
            remainingBatch = remaining,
            replayContentIds = replayContentIds
        )

        assertEquals(
            listOf("color_red", "shape_circle"),
            mergedQueue.take(2).mapNotNull { it.contentId }
        )
        assertEquals(SessionPhase.LEARNING, mergedQueue[2].phase)
        assertEquals(SessionPhase.TEST, mergedQueue[3].phase)
        assertEquals(
            listOf("animal_duck"),
            mergedQueue.drop(4)
                .filter { it.phase == SessionPhase.REVISION }
                .mapNotNull { it.contentId }
        )
    }

    @Test
    fun `later interrupted revision batch is not resumable in next session`() {
        val savedQueue = listOf(
            revisionStep("speech_letters", "letter_ba"),
            revisionStep("match_shapes", "shape_circle"),
            revisionStep("trace_colors", "color_red"),
            revisionStep("speech_animals", "animal_duck"),
            revisionStep("match_numbers", "number_2"),
            revisionStep("trace_colors", "color_blue"),
            revisionStep("listen_choose_animals", "animal_duck")
        )

        assertFalse(RevisionContinuationPlanner.isFirstBatch(savedQueue, currentStepIndex = 6))
    }

    @Test
    fun `remaining first-batch revision is not contaminated by later revision block after learning and test`() {
        val savedQueue = listOf(
            revisionStep("listen_choose_letters", "letter_ba"),
            revisionStep("listen_choose_colors", "color_red"),
            ActivityLaunchStep("story_letters", "letter_new", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_new", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_animals", "animal_lion"),
            revisionStep("match_letters", "letter_alef"),
            revisionStep("trace_numbers", "number_one")
        )

        assertTrue(RevisionContinuationPlanner.isFirstBatch(savedQueue, currentStepIndex = 0))
        assertEquals(
            listOf("letter_ba", "color_red"),
            RevisionContinuationPlanner.currentBatchContentIds(savedQueue, currentStepIndex = 0)
        )
        assertEquals(
            listOf("letter_ba", "color_red"),
            RevisionContinuationPlanner.remainingCurrentBatchOnly(savedQueue, currentStepIndex = 0)
                .mapNotNull { it.contentId }
        )
    }

    @Test
    fun `remaining first-batch revision stops before later repeated revision of same content ids`() {
        val savedQueue = listOf(
            revisionStep("trace_letters", "letter_alef"),
            revisionStep("match_colors", "color_red"),
            revisionStep("trace_numbers", "number_1"),
            ActivityLaunchStep("story_letters", "letter_new", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_new", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_letters", "letter_alef"),
            revisionStep("speech_colors", "color_red"),
            revisionStep("speech_numbers", "number_1")
        )

        assertTrue(RevisionContinuationPlanner.isFirstBatch(savedQueue, currentStepIndex = 0))
        assertEquals(
            listOf("letter_alef", "color_red", "number_1"),
            RevisionContinuationPlanner.remainingCurrentBatchOnly(savedQueue, currentStepIndex = 0)
                .mapNotNull { it.contentId }
        )
    }

    @Test
    fun `later batch detection stays on replay batch only`() {
        val fullQueue = listOf(
            revisionStep("speech_letters", "letter_a"),
            revisionStep("match_colors", "color_red"),
            revisionStep("trace_shapes", "shape_circle"),
            revisionStep("speech_animals", "animal_lion"),
            revisionStep("match_letters", "letter_alef"),
            revisionStep("trace_numbers", "number_one"),
            revisionStep("listen_choose_animals", "animal_lion")
        )

        assertFalse(RevisionContinuationPlanner.isFirstBatch(fullQueue, currentStepIndex = 6))
        assertEquals(
            listOf("animal_lion", "letter_alef", "number_one"),
            RevisionContinuationPlanner.currentBatchContentIds(fullQueue, currentStepIndex = 6)
        )
    }

    @Test
    fun `replay batch is inserted before fresh revision and deduplicated`() {
        val freshQueue = listOf(
            ActivityLaunchStep("story_letters", "letter_x", isTest = false, phase = SessionPhase.LEARNING),
            ActivityLaunchStep("match_letters", "letter_x", isTest = true, phase = SessionPhase.TEST),
            revisionStep("speech_letters", "letter_d"),
            revisionStep("speech_letters", "letter_g")
        )
        val replayBatch = listOf(
            revisionStep("speech_letters", "letter_d"),
            revisionStep("match_letters", "letter_e")
        )

        val merged = RevisionContinuationPlanner.prependReplayBatchToFreshQueue(
            freshQueue = freshQueue,
            replayBatch = replayBatch,
            replayContentIds = setOf("letter_d", "letter_e")
        )

        assertEquals(
            listOf(SessionPhase.LEARNING, SessionPhase.TEST, SessionPhase.REVISION, SessionPhase.REVISION, SessionPhase.REVISION),
            merged.map { it.phase }
        )
        assertEquals(
            listOf("letter_d", "letter_e", "letter_g"),
            merged.filter { it.phase == SessionPhase.REVISION }.mapNotNull { it.contentId }
        )
    }

    private fun revisionStep(activityId: String, contentId: String) =
        ActivityLaunchStep(
            activityId = activityId,
            contentId = contentId,
            isTest = true,
            phase = SessionPhase.REVISION
        )
}
