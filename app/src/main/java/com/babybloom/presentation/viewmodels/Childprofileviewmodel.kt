package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.entity.SkillScoreRow
import com.babybloom.domain.model.AnalyticsChartData
import com.babybloom.domain.model.AiInsight
import com.babybloom.domain.model.ChartResolutionData
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ParsedInsight
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.insight.AiInsightGenerator
import com.babybloom.domain.insight.InsightContextBuilder
import com.babybloom.domain.insight.InsightGenerationPolicy
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import android.media.SoundPool
import android.util.Log
import com.babybloom.BuildConfig
import com.babybloom.R
import com.babybloom.di.AppSoundSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChildProfileUiState(
    val child: Child? = null,
    val childProfile: ChildProfile? = null,
    val sessionCount: Int = 0,
    val progressPercent: Int = 0,
    val latestInsight: AiInsight? = null,
    val parsedInsight: ParsedInsight? = null,
    val isLoadingInsight: Boolean = false,
    val canGenerateInsight: Boolean = true,
    val insightGenerationMessage: String? = null,
    val insightGenerationError: String? = null,
    val recentActivities: List<RecentActivity> = emptyList(),
    val chartData: AnalyticsChartData = AnalyticsChartData(),
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
    private val insightContextBuilder: InsightContextBuilder,
    private val aiInsightGenerator: AiInsightGenerator,
    private val notificationService: ParentNotificationHandler,
    savedStateHandle: SavedStateHandle,
    private val appSoundSettings: AppSoundSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val childId: Long = checkNotNull(savedStateHandle["childId"])

    private val _uiState = MutableStateFlow(ChildProfileUiState())
    val uiState: StateFlow<ChildProfileUiState> = _uiState.asStateFlow()
    private var insightLimitResetJob: Job? = null

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
        observeChartData()
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
                        progressPercent = profile?.overallProgressPercent?.toInt()?.coerceIn(0, 100) ?: 0
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
                        parsedInsight = insight?.let { ParsedInsight.from(it.insightText) },
                        canGenerateInsight = InsightGenerationPolicy.canGenerate(insight?.generatedAt),
                        insightGenerationMessage = if (!InsightGenerationPolicy.canGenerate(insight?.generatedAt)) {
                            context.getString(R.string.ai_daily_limit_message)
                        } else null
                    )
                }
                if (!InsightGenerationPolicy.canGenerate(insight?.generatedAt)) {
                    scheduleInsightLimitReset()
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

    private fun observeChartData() {
        viewModelScope.launch {
            childRepository.observeById(childId).combine(
                activityResultRepository.observeSkillScoresForChart(childId)
            ) { child, skillRows ->
                buildChartData(
                    skillRows = skillRows,
                    childCreatedAt = child?.createdAt ?: System.currentTimeMillis()
                )
            }
                .collect { chartData ->
                    _uiState.update { it.copy(chartData = chartData) }
                }
        }
    }

    // ── Chart bucketing ───────────────────────────────────────────────────────

    private fun buildChartData(
        skillRows: List<SkillScoreRow>,
        childCreatedAt: Long
    ): AnalyticsChartData =
        AnalyticsChartData(
            weekly = buildWeeklyResolutionData(skillRows, childCreatedAt),
            daily = buildDailyResolutionData(skillRows, childCreatedAt)
        )

    private fun buildDailyResolutionData(
        skillRows: List<SkillScoreRow>,
        childCreatedAt: Long
    ): ChartResolutionData {
        val dayStarts = buildDayStartsFrom(childCreatedAt)
        if (dayStarts.isEmpty()) return ChartResolutionData()
        val dayLabels = dayStarts.map(::formatDayLabel)
        val firstDayStart = dayStarts.first()
        val chartEndExclusive = dayStarts.last() + DAY_MS

        fun bucket(timestamp: Long): Int {
            if (timestamp < firstDayStart || timestamp >= chartEndExclusive) return -1
            return ((timestamp - firstDayStart) / DAY_MS).toInt().coerceIn(0, dayStarts.lastIndex)
        }

        val skillBuckets = Array(dayStarts.size) { mutableMapOf<String, MutableList<Float>>() }
        skillRows.forEach { row ->
            val bucketIndex = bucket(row.timestamp)
            if (bucketIndex == -1) return@forEach

            skillBuckets[bucketIndex]
                .getOrPut(row.skillArea) { mutableListOf() }
                .add(row.score * 100f)
        }

        fun avgOrNull(list: List<Float>): Float? = if (list.isEmpty()) null else list.average().toFloat()

        return ChartResolutionData(
            periodLabels = dayLabels,
            languageScores = dayStarts.indices.map { avgOrNull(skillBuckets[it]["LANGUAGE"] ?: emptyList()) },
            numeracyScores = dayStarts.indices.map { avgOrNull(skillBuckets[it]["NUMERACY"] ?: emptyList()) },
            motorScores = dayStarts.indices.map { avgOrNull(skillBuckets[it]["MOTOR"] ?: emptyList()) }
        )
    }

    private fun buildWeeklyResolutionData(
        skillRows: List<SkillScoreRow>,
        childCreatedAt: Long
    ): ChartResolutionData {
        val weekStarts = buildWeekStartsFrom(childCreatedAt)
        if (weekStarts.isEmpty()) return ChartResolutionData()
        val weekLabels = weekStarts.map(::formatWeekLabel)
        val firstWeekStart = weekStarts.first()
        val chartEndExclusive = weekStarts.last() + WEEK_MS

        fun bucket(timestamp: Long): Int {
            if (timestamp < firstWeekStart || timestamp >= chartEndExclusive) return -1
            return ((timestamp - firstWeekStart) / WEEK_MS).toInt().coerceIn(0, weekStarts.lastIndex)
        }

        // skill buckets: index → skillArea → list of scores (0–100)
        val skillBuckets = Array(weekStarts.size) { mutableMapOf<String, MutableList<Float>>() }
        skillRows.forEach { row ->
            val bucketIndex = bucket(row.timestamp)
            if (bucketIndex == -1) return@forEach

            skillBuckets[bucketIndex]
                .getOrPut(row.skillArea) { mutableListOf() }
                .add(row.score * 100f)
        }

        // attention buckets: index → list of scores (0–100)
        fun avgOrNull(list: List<Float>): Float? = if (list.isEmpty()) null else list.average().toFloat()

        return ChartResolutionData(
            periodLabels    = weekLabels,
            languageScores  = weekStarts.indices.map { avgOrNull(skillBuckets[it]["LANGUAGE"] ?: emptyList()) },
            numeracyScores  = weekStarts.indices.map { avgOrNull(skillBuckets[it]["NUMERACY"] ?: emptyList()) },
            motorScores     = weekStarts.indices.map { avgOrNull(skillBuckets[it]["MOTOR"] ?: emptyList()) }
        )
    }

    // ── Progress computation ──────────────────────────────────────────────────

    // ── AI Insight ────────────────────────────────────────────────────────────

    private fun buildWeekStartsFrom(anchorTimeMillis: Long): List<Long> {
        val anchorWeekStart = Calendar.getInstance().apply {
            timeInMillis = anchorTimeMillis
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (0 until 6).map { weekIndex ->
            anchorWeekStart + (weekIndex * WEEK_MS)
        }
    }

    private fun buildDayStartsFrom(anchorTimeMillis: Long): List<Long> {
        val anchorDayStart = Calendar.getInstance().apply {
            timeInMillis = anchorTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (0 until 7).map { dayIndex ->
            anchorDayStart + (dayIndex * DAY_MS)
        }
    }

    private fun formatWeekLabel(weekStartMillis: Long): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(weekStartMillis)

    private fun formatDayLabel(dayStartMillis: Long): String =
        SimpleDateFormat("d MMM", Locale.getDefault()).format(dayStartMillis)

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
    }

    fun onRefreshInsight() {
        viewModelScope.launch {
            val latest = aiInsightRepository.getLatestForChild(childId)
            if (!InsightGenerationPolicy.canGenerate(latest?.generatedAt)) {
                _uiState.update {
                    it.copy(
                        canGenerateInsight = false,
                        insightGenerationMessage = context.getString(R.string.ai_daily_limit_message),
                        insightGenerationError = null
                    )
                }
                scheduleInsightLimitReset()
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoadingInsight = true,
                    insightGenerationError = null,
                    insightGenerationMessage = null
                )
            }
            try {
                val insightContext = insightContextBuilder.build(childId)
                val generatedJson = aiInsightGenerator.generate(insightContext)
                val parsed = ParsedInsight.from(generatedJson)
                require(parsed.learningStyle.isNotBlank() && parsed.strengths.isNotBlank()) {
                    "Generated insight is incomplete"
                }

                aiInsightRepository.save(
                    AiInsight(
                        childId = childId,
                        insightText = generatedJson,
                        generatedAt = System.currentTimeMillis()
                    )
                )
                aiInsightRepository.deleteOldForChild(childId, keepLatest = 30)
                notificationService.onAiInsightGenerated(childId)
                _uiState.update {
                    it.copy(
                        parsedInsight = parsed,
                        canGenerateInsight = false,
                        insightGenerationMessage = context.getString(R.string.ai_daily_limit_message)
                    )
                }
                scheduleInsightLimitReset()
            } catch (exception: Exception) {
                Log.e("BabyBloomInsights", "Insight generation failed for childId=$childId", exception)
                val technicalDetail = exception.message
                    ?.replace(Regex("key=[^&\\s]+"), "key=<redacted>")
                    ?.take(180)
                    .orEmpty()
                _uiState.update {
                    it.copy(
                        insightGenerationError = buildString {
                            append(context.getString(R.string.ai_generation_failed))
                            if (BuildConfig.DEBUG && technicalDetail.isNotBlank()) {
                                append("\n")
                                append(technicalDetail)
                            }
                        }
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoadingInsight = false) }
            }
        }
    }

    private fun scheduleInsightLimitReset() {
        insightLimitResetJob?.cancel()
        insightLimitResetJob = viewModelScope.launch {
            delay(InsightGenerationPolicy.millisUntilNextLocalMidnight())
            _uiState.update {
                it.copy(
                    canGenerateInsight = true,
                    insightGenerationMessage = null
                )
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
