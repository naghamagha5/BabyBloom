package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StoryCardState {
    object Loading : StoryCardState()
    object Intro   : StoryCardState()

    data class LetterCard(
        val letter: ActivityContent,
        val animal: LearningContentEntity,
        val letterImageAsset: ImageAsset,
        val animalImageAsset: ImageAsset,
        val repeatsDone: Int = 0
    ) : StoryCardState()

    data class NumberCard(
        val number: ActivityContent,
        val animals: List<LearningContentEntity>,
        val numberImageAsset: ImageAsset,
        val repeatsDone: Int = 0
    ) : StoryCardState()

    data class SimpleCard(
        val item: ActivityContent,
        val imageAsset: ImageAsset,
        val repeatsDone: Int = 0
    ) : StoryCardState()
}

@HiltViewModel
class StoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao
) : ViewModel() {

    private val _cardState = MutableStateFlow<StoryCardState>(StoryCardState.Intro)
    val cardState: StateFlow<StoryCardState> = _cardState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val totalRepeats = 3
    private var introPlayed = false

    fun loadCard(
        item: ActivityContent,
        isCalmMode: Boolean,
        onCardComplete: (elapsedMs: Long) -> Unit
    ) {
        releasePlayer()
        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            if (!introPlayed) {
                introPlayed = true
                _cardState.value = StoryCardState.Intro  // ✅ show intro screen
                playAudioFile(
                    path = "activities/audio/speech/listen_and_repeat.ogg",
                    onDone = { loadCardContent(item, isCalmMode, startTime, onCardComplete) }
                )
            } else {
                loadCardContent(item, isCalmMode, startTime, onCardComplete)
            }
        }
    }

    private fun loadCardContent(
        item: ActivityContent,
        isCalmMode: Boolean,
        startTime: Long,
        onCardComplete: (elapsedMs: Long) -> Unit
    ) {
        viewModelScope.launch {
            _cardState.value = StoryCardState.Loading
            when (item.category) {
                "LETTER_NAME" -> {
                    val animal = learningContentDao.getByLearningOrderAndCategory(
                        item.learningOrder, "ANIMAL"
                    )
                    if (animal == null) {
                        val state = StoryCardState.SimpleCard(
                            item = item,
                            imageAsset = AssetPathResolver.imageAssetFor(
                                item.contentId, item.category, isCalmMode
                            )
                        )
                        _cardState.value = state
                        val audioPaths = listOf(
                            AssetPathResolver.audioPathFor(item.contentId, item.category)
                        )
                        playSequence(audioPaths, totalRepeats,
                            onRepeatDone = { done ->
                                _cardState.value = state.copy(repeatsDone = done)
                            },
                            onAllDone = {
                                onCardComplete(System.currentTimeMillis() - startTime)
                            }
                        )
                        return@launch
                    }

                    val state = StoryCardState.LetterCard(
                        letter = item,
                        animal = animal,
                        letterImageAsset = AssetPathResolver.imageAssetFor(
                            item.contentId, item.category, isCalmMode
                        ),
                        animalImageAsset = AssetPathResolver.imageAssetFor(
                            animal.id, animal.category, isCalmMode
                        )
                    )
                    _cardState.value = state

                    val soundContentId = "${item.contentId}_s"
                    val audioPaths = listOf(
                        AssetPathResolver.audioPathFor(item.contentId, "LETTER_NAME"),
                        AssetPathResolver.audioPathFor(soundContentId, "LETTER_SOUND"),
                        AssetPathResolver.audioPathFor(animal.id, "ANIMAL")
                    )
                    playSequence(audioPaths, totalRepeats,
                        onRepeatDone = { done ->
                            _cardState.value = state.copy(repeatsDone = done)
                        },
                        onAllDone = {
                            onCardComplete(System.currentTimeMillis() - startTime)
                        }
                    )
                }

                "NUMBER" -> {
                    val n = item.learningOrder.coerceAtLeast(1)
                    val oneAnimal = learningContentDao.getByCategory("ANIMAL").shuffled().firstOrNull()
                    val animals = if (oneAnimal != null) List(n) { oneAnimal } else emptyList()

                    val state = StoryCardState.NumberCard(
                        number = item,
                        animals = animals,
                        numberImageAsset = AssetPathResolver.imageAssetFor(
                            item.contentId, item.category, isCalmMode
                        )
                    )
                    _cardState.value = state

                    val audioPaths = listOf(
                        AssetPathResolver.audioPathFor(item.contentId, item.category)
                    )
                    playSequence(audioPaths, totalRepeats,
                        onRepeatDone = { done -> _cardState.value = state.copy(repeatsDone = done) },
                        onAllDone = { onCardComplete(System.currentTimeMillis() - startTime) }
                    )
                }

                else -> {
                    val state = StoryCardState.SimpleCard(
                        item = item,
                        imageAsset = AssetPathResolver.imageAssetFor(
                            item.contentId, item.category, isCalmMode
                        )
                    )
                    _cardState.value = state

                    val audioPaths = listOf(
                        AssetPathResolver.audioPathFor(item.contentId, item.category)
                    )
                    playSequence(audioPaths, totalRepeats,
                        onRepeatDone = { done ->
                            _cardState.value = state.copy(repeatsDone = done)
                        },
                        onAllDone = {
                            onCardComplete(System.currentTimeMillis() - startTime)
                        }
                    )
                }
            }
        }
    }

    private fun playAudioFile(path: String, onDone: () -> Unit) {
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnCompletionListener { onDone() }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("StoryViewModel", "Intro audio error, skipping")
                onDone()
                true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("StoryViewModel", "Intro audio not found: $path, skipping")
            onDone()
        }
    }

    private fun playSequence(
        paths: List<String>,
        repeatsLeft: Int,
        pathIndex: Int = 0,
        onRepeatDone: (Int) -> Unit,
        onAllDone: () -> Unit,
        repeatsDone: Int = 0
    ) {
        if (pathIndex >= paths.size) {
            val newDone = repeatsDone + 1
            onRepeatDone(newDone)
            if (newDone >= totalRepeats) {
                onAllDone()
            } else {
                viewModelScope.launch {
                    delay(600)
                    playSequence(paths, repeatsLeft - 1, 0,
                        onRepeatDone, onAllDone, newDone)
                }
            }
            return
        }

        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(paths[pathIndex])
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnCompletionListener {
                playSequence(paths, repeatsLeft, pathIndex + 1,
                    onRepeatDone, onAllDone, repeatsDone)
            }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("StoryViewModel", "Error on: ${paths[pathIndex]}, skipping")
                playSequence(paths, repeatsLeft, pathIndex + 1,
                    onRepeatDone, onAllDone, repeatsDone)
                true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("StoryViewModel", "Audio not found: ${paths[pathIndex]}, skipping")
            playSequence(paths, repeatsLeft, pathIndex + 1,
                onRepeatDone, onAllDone, repeatsDone)
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}