package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.data.local.entity.SkillScoreRow
import com.babybloom.domain.model.AiInsight
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ParsedInsight
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.model.WeeklyChartData
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.media.SoundPool
import com.babybloom.R
import com.babybloom.di.AppSoundSettings
import dagger.hilt.android.qualifiers.ApplicationContext

data class ChildProfileUiState(
    val child: Child? = null,
    val childProfile: ChildProfile? = null,
    val sessionCount: Int = 0,
    val progressPercent: Int = 0,
    val latestInsight: AiInsight? = null,
    val parsedInsight: ParsedInsight? = null,
    val isLoadingInsight: Boolean = false,
    val recentActivities: List<RecentActivity> = emptyList(),
    val weeklyChartData: WeeklyChartData = WeeklyChartData(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToHome: Boolean = false,
    val showSessionDurationPicker: Boolean = false,
    val showRemoveChildDialog: Boolean = false,
    val currentSessionDurationMinutes: Int = 20
)

@HiltViewModel
class ChildProfileViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val aiInsightRepository: AiInsightRepository,
    private val sessionRepository: SessionRepository,
    private val activityResultRepository: ActivityResultRepository,
    savedStateHandle: SavedStateHandle,
    private val appSoundSettings: AppSoundSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val childId: Long = checkNotNull(savedStateHandle["childId"])

    private val _uiState = MutableStateFlow(ChildProfileUiState())
    val uiState: StateFlow<ChildProfileUiState> = _uiState.asStateFlow()

    // ── Sound ─────────────────────────────────────────────────────────────────
    private val soundPool     = SoundPool.Builder().setMaxStreams(3).build()
    private var toggleSoundId : Int     = 0
    private var soundLoaded   : Boolean = false


    init {
        observeChild()
        observeChildProfile()
        observeSessionCount()
        observeLatestInsight()
        loadRecentActivities()
        loadChartData()
        soundPool.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
        toggleSoundId = soundPool.load(context, R.raw.button_one, 1)
    }

    private fun playToggleSound() {
        if (appSoundSettings.soundEnabled.value && soundLoaded) {
            soundPool.play(toggleSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPool.release()
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeChild() {
        viewModelScope.launch {
            childRepository.observeById(childId).collect { child ->
                _uiState.update { state ->
                    state.copy(
                        child = child,
                        currentSessionDurationMinutes = child?.sessionDurationMinutes
                            ?: state.currentSessionDurationMinutes
                    )
                }
            }
        }
    }

    private fun observeChildProfile() {
        viewModelScope.launch {
            childProfileRepository.observeProfile(childId).collect { profile ->
                _uiState.update { state ->
                    state.copy(
                        childProfile    = profile,
                        progressPercent = profile?.let { computeProgress(it) } ?: 0
                    )
                }
            }
        }
    }

    private fun observeSessionCount() {
        viewModelScope.launch {
            sessionRepository.countByChild(childId).collect { count ->
                _uiState.update { it.copy(sessionCount = count) }
            }
        }
    }

    private fun observeLatestInsight() {
        viewModelScope.launch {
            aiInsightRepository.getLatestInsight(childId).collect { insight ->
                _uiState.update { state ->
                    state.copy(
                        latestInsight = insight,
                        parsedInsight = insight?.let { ParsedInsight.from(it.insightText) }
                    )
                }
            }
        }
    }

    private fun loadRecentActivities() {
        viewModelScope.launch {
            val activities = activityResultRepository.getRecentActivities(childId, limit = 3)
            _uiState.update { it.copy(recentActivities = activities) }
        }
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val skillRows     = activityResultRepository.getSkillScoresForChart(childId)
            val attentionRows = sessionRepository.getAttentionScoresForChart(childId)
            val chartData     = buildChartData(skillRows, attentionRows)
            _uiState.update { it.copy(weeklyChartData = chartData) }
        }
    }

    // ── Chart bucketing ───────────────────────────────────────────────────────

    private fun buildChartData(
        skillRows: List<SkillScoreRow>,
        attentionRows: List<AttentionScoreRow>
    ): WeeklyChartData {
        if (skillRows.isEmpty() && attentionRows.isEmpty()) return WeeklyChartData()

        val allTimestamps = skillRows.map { it.timestamp } + attentionRows.map { it.startTime }
        val minTime   = allTimestamps.min()
        val maxTime   = allTimestamps.max()
        val range     = (maxTime - minTime).coerceAtLeast(1L)
        val bucketMs  = range / 6f

        fun bucket(t: Long) = ((t - minTime) / bucketMs).toInt().coerceIn(0, 5)

        // skill buckets: index → skillArea → list of scores (0–100)
        val skillBuckets = Array(6) { mutableMapOf<String, MutableList<Float>>() }
        skillRows.forEach { row ->
            skillBuckets[bucket(row.timestamp)]
                .getOrPut(row.skillArea) { mutableListOf() }
                .add(row.score * 100f)
        }

        // attention buckets: index → list of scores (0–100)
        val attBuckets = Array(6) { mutableListOf<Float>() }
        attentionRows.forEach { row ->
            attBuckets[bucket(row.startTime)].add(row.attentionScore * 100f)
        }

        fun avg(list: List<Float>) = if (list.isEmpty()) 0f else list.average().toFloat()

        return WeeklyChartData(
            languageScores  = (0..5).map { avg(skillBuckets[it]["LANGUAGE"]  ?: emptyList()) },
            numeracyScores  = (0..5).map { avg(skillBuckets[it]["NUMERACY"]  ?: emptyList()) },
            motorScores     = (0..5).map { avg(skillBuckets[it]["MOTOR"]     ?: emptyList()) },
            attentionScores = (0..5).map { avg(attBuckets[it]) }
        )
    }

    // ── Progress computation ──────────────────────────────────────────────────

    private fun computeProgress(p: ChildProfile): Int {
        val scoreAvg = (p.visualScore + p.audioScore + p.gameScore) / 3f
        val levelAvg = (p.languageLevel + p.numeracyLevel + p.motorLevel) / 9f
        return ((scoreAvg * 0.6f + levelAvg * 0.4f) * 100).toInt().coerceIn(0, 100)
    }

    // ── AI Insight ────────────────────────────────────────────────────────────

    fun onRefreshInsight() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInsight = true) }
            try {
                // TODO: replace with real AI API call
            } finally {
                _uiState.update { it.copy(isLoadingInsight = false) }
            }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun onToggleSoundEffects(enabled: Boolean) {
        playToggleSound()
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            childRepository.updateChild(child.copy(soundEffectEnabled = enabled))
        }
    }

    fun onToggleBackgroundMusic(enabled: Boolean) {
        playToggleSound()
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            childRepository.updateChild(child.copy(backgroundMusicEnabled = enabled))
        }
    }

    fun onToggleUiTheme(isCalmMode: Boolean) {
        playToggleSound()
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            childRepository.updateChild(child.copy(uiTheme = isCalmMode))
        }
    }

    fun onShowSessionDurationPicker() =
        _uiState.update { it.copy(showSessionDurationPicker = true) }

    fun onDismissSessionDurationPicker() =
        _uiState.update { it.copy(showSessionDurationPicker = false) }

    fun onConfirmSessionDuration(minutes: Int) {
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            childRepository.updateChild(child.copy(sessionDurationMinutes = minutes))
            _uiState.update {
                it.copy(
                    showSessionDurationPicker     = false,
                    currentSessionDurationMinutes = minutes
                )
            }
        }
    }

    fun onShowRemoveChildDialog() =
        _uiState.update { it.copy(showRemoveChildDialog = true) }

    fun onDismissRemoveChildDialog() =
        _uiState.update { it.copy(showRemoveChildDialog = false) }

    fun onConfirmRemoveChild() {
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            childRepository.deleteChild(child)
            _uiState.update { it.copy(navigateToHome = true) }
        }
    }

    fun onNavigationHandled() =
        _uiState.update { it.copy(navigateToHome = false) }
}
