package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import com.babybloom.util.SoundEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListenChoiceOption(
    val id: String,
    val labelAr: String,
    val imageAsset: ImageAsset,
    val audioPath: String,
    val isCorrect: Boolean
)

enum class ListenAnswerFeedback { IDLE, CORRECT, WRONG }

data class ListenAndChooseGameState(
    val contentId: String? = null,
    val isLoading: Boolean = true,
    val isTest: Boolean = false,
    val category: String = "",
    val instructionText: String = "",
    val instructionAudioPath: String = "",
    val targetAudioPath: String = "",
    val correctId: String = "",
    val attemptsLeft: Int = 3,
    val attemptsUsed: Int = 0,
    val options: List<ListenChoiceOption> = emptyList(),
    val isAudioLocked: Boolean = true,
    val selectedOptionId: String? = null,
    val revealedCorrectId: String? = null,
    val answerFeedback: ListenAnswerFeedback = ListenAnswerFeedback.IDLE,
    val isAnswered: Boolean = false,
    val isCorrect: Boolean = false,
    val showCelebration: Boolean = false,
    val startTimeMs: Long = 0L
) {
    val currentAttemptNumber: Int get() = attemptsUsed + 1
    val canReplay: Boolean get() = !isAnswered && !isAudioLocked && attemptsUsed < 2
}

@HiltViewModel
class ListenAndChooseGameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: com.babybloom.data.local.dao.LearningContentDao,
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val QUESTION_TIMEOUT_MS = 10_000L
        const val ATTEMPT_ENCODE_FACTOR = 100_000L
        const val CELEBRATION_DURATION_MS = 2_200L
        const val WRONG_FEEDBACK_MS = 600L
        const val CATEGORY_ANIMAL = "ANIMAL"
        const val CATEGORY_COLOR = "COLOR"
        const val CATEGORY_NUMBER = "NUMBER"
        const val CATEGORY_SHAPE = "SHAPE"
        const val CATEGORY_LETTER_NAME = "LETTER_NAME"
    }

    private val _state = MutableStateFlow(ListenAndChooseGameState())
    val state: StateFlow<ListenAndChooseGameState> = _state.asStateFlow()

    private var onComplete: ((isCorrect: Boolean, encodedMs: Long) -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var timerJob: Job? = null
    private var loadGeneration = 0L
    private var completedGeneration = -1L

    fun loadContent(
        currentItem: ActivityContent,
        isCalmMode: Boolean,
        isTest: Boolean,
        onComplete: (isCorrect: Boolean, encodedMs: Long) -> Unit
    ) {
        loadGeneration++
        completedGeneration = -1L
        this.onComplete = onComplete
        timerJob?.cancel()
        releasePlayer()
        _state.value = ListenAndChooseGameState(
            contentId = currentItem.contentId,
            isLoading = true,
            isTest = isTest
        )

        viewModelScope.launch {
            val category = normalizeCategory(currentItem.category, currentItem.contentId)
            val correctEntity = learningContentDao.getById(currentItem.contentId) ?: run {
                Log.e("ListenChooseVM", "Missing entity for ${currentItem.contentId}")
                return@launch
            }
            val allItems = learningContentDao.getByCategory(category)
            val options = buildOptions(
                correctId = correctEntity.id,
                category = category,
                allItems = allItems,
                isCalmMode = isCalmMode,
                isTest = isTest
            )
            val instructionText = instructionTextFor(category)
            val instructionAudio = AssetPathResolver.listenAndChooseInstructionPath(category)
            val targetAudio = AssetPathResolver.audioPathFor(correctEntity.id, category)

            _state.value = ListenAndChooseGameState(
                contentId = currentItem.contentId,
                isLoading = false,
                isTest = isTest,
                category = category,
                instructionText = instructionText,
                instructionAudioPath = instructionAudio,
                targetAudioPath = targetAudio,
                correctId = correctEntity.id,
                attemptsLeft = MAX_ATTEMPTS,
                attemptsUsed = 0,
                options = options,
                isAudioLocked = true,
                selectedOptionId = null,
                revealedCorrectId = null,
                answerFeedback = ListenAnswerFeedback.IDLE,
                isAnswered = false,
                isCorrect = false,
                showCelebration = false,
                startTimeMs = System.currentTimeMillis()
            )

            startAttempt(
                attemptNumber = 1,
                includeInstruction = true
            )
        }
    }

    fun stopContent(contentId: String) {
        if (_state.value.contentId != contentId) return
        loadGeneration++
        timerJob?.cancel()
        releasePlayer()
        onComplete = null
        _state.value = ListenAndChooseGameState(
            contentId = contentId,
            isLoading = true
        )
    }

    fun onReplayClicked() {
        val current = _state.value
        if (!current.canReplay || current.isAudioLocked) return
        timerJob?.cancel()
        releasePlayer()
        appSoundSettings.playSoundEffect(SoundEffect.TAP)
        startNextAttempt(playWrongSound = false, includeInstruction = false)
    }

    fun onChoiceSelected(optionId: String) {
        val current = _state.value
        if (current.isAnswered || current.isAudioLocked) return
        timerJob?.cancel()
        releasePlayer()
        appSoundSettings.playSoundEffect(SoundEffect.TAP)

        val option = current.options.find { it.id == optionId } ?: return
        if (option.isCorrect) {
            completeCorrect(optionId)
        } else {
            handleWrongSelection(optionId)
        }
    }

    private fun buildOptions(
        correctId: String,
        category: String,
        allItems: List<com.babybloom.data.local.entity.LearningContentEntity>,
        isCalmMode: Boolean,
        isTest: Boolean
    ): List<ListenChoiceOption> {
        val correct = allItems.find { it.id == correctId } ?: return emptyList()
        val picked = if (isTest) {
            val distractors = allItems
                .filter { it.id != correctId }
                .shuffled()
                .take(3)
            (listOf(correct) + distractors).shuffled()
        } else {
            listOf(correct)
        }
        return picked.map { entity ->
            ListenChoiceOption(
                id = entity.id,
                labelAr = entity.labelAr,
                imageAsset = AssetPathResolver.imageAssetFor(entity.id, category, isCalmMode),
                audioPath = AssetPathResolver.audioPathFor(entity.id, category),
                isCorrect = entity.id == correctId
            )
        }
    }

    private fun startAttempt(attemptNumber: Int, includeInstruction: Boolean) {
        val current = _state.value
        if (current.isAnswered || current.targetAudioPath.isBlank()) return
        val generation = loadGeneration
        val unlockAfterSegments = (if (includeInstruction && current.instructionAudioPath.isNotBlank()) 1 else 0) + 1

        val paths = buildList {
            if (includeInstruction && current.instructionAudioPath.isNotBlank()) {
                add(current.instructionAudioPath)
            }
            repeat(attemptNumber.coerceAtLeast(1)) { add(current.targetAudioPath) }
        }

        _state.value = current.copy(isAudioLocked = true)
        playSequence(
            paths = paths,
            onSegmentComplete = { completedCount ->
                if (completedCount == unlockAfterSegments &&
                    generation == loadGeneration &&
                    _state.value.contentId == current.contentId &&
                    !_state.value.isAnswered
                ) {
                    _state.value = _state.value.copy(isAudioLocked = false)
                    startAttemptTimer()
                }
            },
            onComplete = {
                if (generation != loadGeneration || _state.value.contentId != current.contentId) return@playSequence
                if (_state.value.isAudioLocked && !_state.value.isAnswered) {
                    _state.value = _state.value.copy(isAudioLocked = false)
                    startAttemptTimer()
                }
            }
        )
    }

    private fun startAttemptTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            delay(QUESTION_TIMEOUT_MS)
            if (!_state.value.isAnswered && !_state.value.isAudioLocked) {
                startNextAttempt(playWrongSound = true, includeInstruction = false)
            }
        }
    }

    private fun startNextAttempt(playWrongSound: Boolean, includeInstruction: Boolean) {
        val current = _state.value
        if (current.isAnswered) return

        val consumedAttempts = current.attemptsUsed + 1
        val attemptsLeft = (MAX_ATTEMPTS - consumedAttempts).coerceAtLeast(0)
        if (attemptsLeft == 0) {
            completeIncorrect(consumedAttempts, playWrongSound)
            return
        }

        _state.value = current.copy(
            attemptsUsed = consumedAttempts,
            attemptsLeft = attemptsLeft,
            isAudioLocked = true,
            selectedOptionId = null,
            revealedCorrectId = null,
            answerFeedback = ListenAnswerFeedback.IDLE,
            isCorrect = false,
            showCelebration = false
        )

        val nextAttemptNumber = consumedAttempts + 1
        if (playWrongSound) {
            appSoundSettings.playSoundEffect(SoundEffect.WRONG)
        }
        startAttempt(nextAttemptNumber, includeInstruction)
    }

    private fun completeCorrect(selectedOptionId: String) {
        val current = _state.value
        if (current.isAnswered) return

        val attempts = current.currentAttemptNumber
        val elapsedMs = System.currentTimeMillis() - current.startTimeMs
        val encoded = elapsedMs + attempts.toLong() * ATTEMPT_ENCODE_FACTOR
        val generation = loadGeneration

        _state.value = current.copy(
            selectedOptionId = selectedOptionId,
            revealedCorrectId = selectedOptionId,
            answerFeedback = ListenAnswerFeedback.CORRECT,
            isAnswered = true,
            isCorrect = true,
            attemptsUsed = attempts,
            attemptsLeft = (MAX_ATTEMPTS - attempts).coerceAtLeast(0),
            isAudioLocked = true,
            showCelebration = false
        )

        appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
        viewModelScope.launch {
            if (generation != loadGeneration || _state.value.contentId != current.contentId) return@launch
            _state.value = _state.value.copy(showCelebration = true)
            delay(CELEBRATION_DURATION_MS)
            if (generation != loadGeneration || _state.value.contentId != current.contentId) return@launch
            _state.value = _state.value.copy(showCelebration = false)
            dispatchCompleteOnce(true, encoded, generation)
        }
    }

    private fun handleWrongSelection(selectedOptionId: String) {
        val current = _state.value
        if (current.isAnswered) return

        val consumedAttempts = current.attemptsUsed + 1
        val attemptsLeft = (MAX_ATTEMPTS - consumedAttempts).coerceAtLeast(0)

        _state.value = current.copy(
            selectedOptionId = selectedOptionId,
            revealedCorrectId = null,
            answerFeedback = ListenAnswerFeedback.WRONG,
            isAudioLocked = true
        )

        viewModelScope.launch {
            appSoundSettings.playSoundEffect(SoundEffect.WRONG)
            delay(WRONG_FEEDBACK_MS)
            if (attemptsLeft == 0) {
                completeIncorrect(consumedAttempts, playWrongSound = false)
            } else {
                _state.value = _state.value.copy(
                    selectedOptionId = null,
                    revealedCorrectId = null,
                    answerFeedback = ListenAnswerFeedback.IDLE
                )
                startNextAttempt(playWrongSound = false, includeInstruction = false)
            }
        }
    }

    private fun completeIncorrect(attempts: Int, playWrongSound: Boolean) {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs = System.currentTimeMillis() - current.startTimeMs
        val encoded = elapsedMs + attempts.toLong() * ATTEMPT_ENCODE_FACTOR
        val generation = loadGeneration

        _state.value = current.copy(
            revealedCorrectId = null,
            isAnswered = true,
            isCorrect = false,
            attemptsUsed = attempts,
            attemptsLeft = 0,
            isAudioLocked = true,
            showCelebration = false
        )

        if (playWrongSound) {
            appSoundSettings.playSoundEffect(SoundEffect.WRONG)
        }
        viewModelScope.launch {
            dispatchCompleteOnce(false, encoded, generation)
        }
    }

    private fun dispatchCompleteOnce(isCorrect: Boolean, encodedMs: Long, generation: Long) {
        if (generation != loadGeneration || completedGeneration == generation) return
        completedGeneration = generation
        onComplete?.invoke(isCorrect, encodedMs)
    }

    private fun instructionTextFor(category: String): String = when (category) {
        CATEGORY_LETTER_NAME -> context.getString(com.babybloom.R.string.listen_choose_instruction_letter)
        CATEGORY_ANIMAL -> context.getString(com.babybloom.R.string.listen_choose_instruction_animal)
        CATEGORY_NUMBER -> context.getString(com.babybloom.R.string.listen_choose_instruction_number)
        CATEGORY_COLOR -> context.getString(com.babybloom.R.string.listen_choose_instruction_color)
        CATEGORY_SHAPE -> context.getString(com.babybloom.R.string.listen_choose_instruction_shape)
        else -> context.getString(com.babybloom.R.string.listen_choose_instruction_letter)
    }

    private fun normalizeCategory(category: String, contentId: String): String {
        val normalized = category.uppercase().trim()
        if (normalized.isNotBlank() && normalized != "UNKNOWN") return normalized
        return when {
            contentId.startsWith("letter_", ignoreCase = true) -> CATEGORY_LETTER_NAME
            contentId.startsWith("animal_", ignoreCase = true) -> CATEGORY_ANIMAL
            contentId.startsWith("number_", ignoreCase = true) -> CATEGORY_NUMBER
            contentId.startsWith("color_", ignoreCase = true) -> CATEGORY_COLOR
            contentId.startsWith("shape_", ignoreCase = true) -> CATEGORY_SHAPE
            else -> CATEGORY_LETTER_NAME
        }
    }

    private fun playSequence(
        paths: List<String>,
        index: Int = 0,
        onSegmentComplete: (completedCount: Int) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        if (index >= paths.size) {
            onComplete()
            return
        }
        val path = paths[index]
        if (isDisabledSoundEffectPath(path)) {
            onSegmentComplete(index + 1)
            playSequence(paths, index + 1, onSegmentComplete, onComplete)
            return
        }
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnCompletionListener {
                onSegmentComplete(index + 1)
                playSequence(paths, index + 1, onSegmentComplete, onComplete)
            }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("ListenChooseVM", "Error playing $path")
                onSegmentComplete(index + 1)
                playSequence(paths, index + 1, onSegmentComplete, onComplete)
                true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("ListenChooseVM", "Audio not found: $path - ${e.message}")
            onSegmentComplete(index + 1)
            playSequence(paths, index + 1, onSegmentComplete, onComplete)
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun isDisabledSoundEffectPath(path: String): Boolean {
        val fileName = path.substringAfterLast('/')
        return fileName in SoundEffect.fileNames && !appSoundSettings.soundEnabled.value
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        releasePlayer()
    }
}
