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

    data class LetterCard(
        val letter: ActivityContent,
        val animal: LearningContentEntity,
        val letterImageAsset: ImageAsset,
        val animalImageAsset: ImageAsset,
        val repeatsDone: Int = 0             // 0–3, shown as progress dots in UI
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

    private val _cardState = MutableStateFlow<StoryCardState>(StoryCardState.Loading)
    val cardState: StateFlow<StoryCardState> = _cardState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val totalRepeats = 3

    // Called from StoryScreen LaunchedEffect whenever the currentItem changes
    fun loadCard(
        item: ActivityContent,
        isCalmMode: Boolean,
        onCardComplete: (elapsedMs: Long) -> Unit
    ) {
        releasePlayer()
        _cardState.value = StoryCardState.Loading
        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            when (item.category) {
                "LETTER_NAME" -> {
                    val animal = learningContentDao.getByLearningOrderAndCategory(
                        item.learningOrder, "ANIMAL"
                    )
                    if (animal == null) {
                        // Fallback: treat as simple card if no matching animal
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

                    // 3 audio files per play: name → sound → animal
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
                    val animals = learningContentDao
                        .getByCategory("ANIMAL")
                        .shuffled()
                        .take(n)

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
                        onRepeatDone = { done ->
                            _cardState.value = state.copy(repeatsDone = done)
                        },
                        onAllDone = {
                            onCardComplete(System.currentTimeMillis() - startTime)
                        }
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

    // Plays a list of audio files in sequence, repeats N times total
    private fun playSequence(
        paths: List<String>,
        repeatsLeft: Int,
        pathIndex: Int = 0,
        onRepeatDone: (Int) -> Unit,
        onAllDone: () -> Unit,
        repeatsDone: Int = 0
    ) {
        if (pathIndex >= paths.size) {
            // One full play of the sequence done
            val newDone = repeatsDone + 1
            onRepeatDone(newDone)
            if (newDone >= totalRepeats) {
                onAllDone()
            } else {
                viewModelScope.launch {
                    delay(600) // brief pause between repeats
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