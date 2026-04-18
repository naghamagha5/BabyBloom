package com.babybloom.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.babybloom.R
import com.babybloom.domain.model.Child
import com.babybloom.ui.theme.*

// ─── Settings Tab ─────────────────────────────────────────────────────────────
@Composable
fun ChildSettingsTab(
    child: Child?,
    currentSessionDurationMinutes: Int,
    showDurationPicker: Boolean,
    showRemoveDialog: Boolean,
    onToggleSoundEffects: (Boolean) -> Unit,
    onToggleBackgroundMusic: (Boolean) -> Unit,
    onToggleUiTheme: (Boolean) -> Unit,
    onSessionDurationClick: () -> Unit,
    onDismissDurationPicker: () -> Unit,
    onConfirmDuration: (Int) -> Unit,
    onRemoveChildClick: () -> Unit,
    onDismissRemoveDialog: () -> Unit,
    onConfirmRemoveChild: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SoundSettingsCard(
                initialSoundEnabled     = child?.soundEffectEnabled ?: true,
                initialMusicEnabled     = child?.backgroundMusicEnabled ?: true,
                onToggleSoundEffects    = onToggleSoundEffects,
                onToggleBackgroundMusic = onToggleBackgroundMusic
            )
            DisplaySettingsCard(
                initialCalmMode = child?.uiTheme ?: false,
                onToggleUiTheme = onToggleUiTheme
            )
            SessionSettingsCard(
                currentDurationMinutes = currentSessionDurationMinutes,
                onDurationClick        = onSessionDurationClick
            )
            RemoveChildCard(
                childName     = child?.name ?: "",
                onRemoveClick = onRemoveChildClick
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showDurationPicker) {
            SessionDurationPickerDialog(
                initialMinutes = currentSessionDurationMinutes,
                onDismiss      = onDismissDurationPicker,
                onConfirm      = onConfirmDuration
            )
        }
        if (showRemoveDialog) {
            RemoveChildConfirmDialog(
                childName = child?.name ?: "",
                onDismiss = onDismissRemoveDialog,
                onConfirm = onConfirmRemoveChild
            )
        }
    }
}

// ─── Sound Settings Card ──────────────────────────────────────────────────────
@Composable
private fun SoundSettingsCard(
    initialSoundEnabled: Boolean,
    initialMusicEnabled: Boolean,
    onToggleSoundEffects: (Boolean) -> Unit,
    onToggleBackgroundMusic: (Boolean) -> Unit
) {
    var soundChecked by remember { mutableStateOf(initialSoundEnabled) }
    var musicChecked by remember { mutableStateOf(initialMusicEnabled) }

    SettingsCard(title = stringResource(R.string.settings_sound_title)) {
        SettingsToggleRow(
            iconRes         = R.drawable.ic_settings_sound_effects,
            title           = stringResource(R.string.settings_sound_effects),
            subtitle        = stringResource(R.string.settings_sound_effects_desc),
            checked         = soundChecked,
            onCheckedChange = { soundChecked = it; onToggleSoundEffects(it) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderGray)
        SettingsToggleRow(
            iconRes         = R.drawable.ic_settings_music,
            title           = stringResource(R.string.settings_background_music),
            subtitle        = stringResource(R.string.settings_background_music_desc),
            checked         = musicChecked,
            onCheckedChange = { musicChecked = it; onToggleBackgroundMusic(it) }
        )
    }
}

// ─── Reusable Toggle Row ──────────────────────────────────────────────────────
@Composable
private fun SettingsToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .background(CardPurple, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = null,
                tint               = ProgressPurple,
                modifier           = Modifier
                    .size(22.dp)
                    .graphicsLayer { scaleX = -1f }
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = NavyDark
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = White,
                checkedTrackColor   = ProgressPurple,
                uncheckedThumbColor = White,
                uncheckedTrackColor = BorderGray
            )
        )
    }
}

// ─── Display Settings Card ────────────────────────────────────────────────────
@Composable
private fun DisplaySettingsCard(
    initialCalmMode: Boolean,
    onToggleUiTheme: (Boolean) -> Unit
) {
    var isCalmMode     by remember { mutableStateOf(initialCalmMode) }
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        DisplayModeInfoDialog(onDismiss = { showInfoDialog = false })
    }

    SettingsCard(
        title      = stringResource(R.string.settings_display_title),
        infoAction = { showInfoDialog = true }
    ) {
        Text(
            text  = stringResource(R.string.settings_display_default),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(CardPurple, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeButton(
                label      = stringResource(R.string.settings_mode_active),
                iconRes    = R.drawable.ic_mode_active,
                isSelected = !isCalmMode,
                modifier   = Modifier.weight(1f),
                onClick    = { isCalmMode = false; onToggleUiTheme(false) }
            )
            ModeButton(
                label      = stringResource(R.string.settings_mode_calm),
                iconRes    = R.drawable.ic_mode_calm,
                isSelected = isCalmMode,
                modifier   = Modifier.weight(1f),
                onClick    = { isCalmMode = true; onToggleUiTheme(true) }
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier         = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) NavyDark else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = if (isSelected) 1f else 0.45f }
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color      = if (isSelected) White else TextSecondary
                )
            )
        }
    }
}

// ─── Session Settings Card ────────────────────────────────────────────────────
@Composable
private fun SessionSettingsCard(
    currentDurationMinutes: Int,
    onDurationClick: () -> Unit
) {
    SettingsCard(title = stringResource(R.string.settings_session_title)) {
        Text(
            text  = stringResource(R.string.settings_session_default_duration, currentDurationMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                .background(White)
                .clickable { onDurationClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_session_duration),
                    contentDescription = null,
                    tint               = Color.Unspecified,
                    modifier           = Modifier.size(36.dp)
                )
                Text(
                    text  = stringResource(R.string.settings_session_duration),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color      = NavyDark
                    )
                )
            }
        }
    }
}

// ─── Remove Child Card ────────────────────────────────────────────────────────
@Composable
private fun RemoveChildCard(
    childName: String,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .border(1.dp, DangerRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = DangerRedLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint               = DangerRed,
                    modifier           = Modifier.size(18.dp)
                )
                Text(
                    text  = stringResource(R.string.settings_remove_child_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DangerRed
                )
            }
            Text(
                text  = stringResource(R.string.settings_remove_child_warning, childName),
                style = MaterialTheme.typography.bodySmall,
                color = DangerRed.copy(alpha = 0.8f)
            )
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DangerRed)
                    .clickable { onRemoveClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint               = White,
                        modifier           = Modifier.size(18.dp)
                    )
                    Text(
                        text  = stringResource(R.string.settings_remove_child_button),
                        style = MaterialTheme.typography.labelLarge.copy(color = White)
                    )
                }
            }
        }
    }
}

// ─── Reusable Card Shell ──────────────────────────────────────────────────────
@Composable
private fun SettingsCard(
    title: String,
    infoAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    ),
                    color    = NavyDark,
                    modifier = Modifier.weight(1f)
                )
                if (infoAction != null) {
                    Box(
                        modifier         = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(CardPurple)
                            .clickable { infoAction() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = "!",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color      = ProgressPurple
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

// ─── Display Mode Info Dialog ─────────────────────────────────────────────────
@Composable
private fun DisplayModeInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text  = stringResource(R.string.display_info_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )
                    HorizontalDivider(color = BorderGray)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_mode_active),
                                contentDescription = null,
                                tint               = Color.Unspecified,
                                modifier           = Modifier.size(22.dp)
                            )
                            Text(
                                text  = stringResource(R.string.display_info_active_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color      = NavyDark
                                )
                            )
                        }
                        Text(
                            text  = stringResource(R.string.display_info_active_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    HorizontalDivider(color = BorderGray)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_mode_calm),
                                contentDescription = null,
                                tint               = Color.Unspecified,
                                modifier           = Modifier.size(22.dp)
                            )
                            Text(
                                text  = stringResource(R.string.display_info_calm_title),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color      = NavyDark
                                )
                            )
                        }
                        Text(
                            text  = stringResource(R.string.display_info_calm_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    HorizontalDivider(color = BorderGray)
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ProgressPurple)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = stringResource(R.string.btn_close),
                            style = MaterialTheme.typography.labelLarge.copy(color = White)
                        )
                    }
                }
            }
        }
    }
}

// ─── Session Duration Picker Dialog ──────────────────────────────────────────
@Composable
private fun SessionDurationPickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val steps = (5..60 step 5).toList()
    var sliderPosition by remember {
        mutableFloatStateOf(
            steps.indexOf(initialMinutes.coerceIn(5, 60)).coerceAtLeast(0).toFloat()
        )
    }
    val selectedMinutes = steps[sliderPosition.toInt().coerceIn(0, steps.lastIndex)]

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text  = stringResource(R.string.duration_picker_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = NavyDark
                    )
                    Text(
                        text      = "$selectedMinutes ${stringResource(R.string.duration_picker_minutes)}",
                        style     = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color      = NavyDark,
                            fontSize   = 36.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        value         = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        valueRange    = 0f..(steps.lastIndex.toFloat()),
                        steps         = steps.size - 2,
                        modifier      = Modifier.fillMaxWidth(),
                        colors        = SliderDefaults.colors(
                            thumbColor         = ProgressPurple,
                            activeTrackColor   = ProgressPurple,
                            inactiveTrackColor = CardPurple
                        )
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.duration_picker_min, steps.first()),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            stringResource(R.string.duration_picker_max, steps.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    HorizontalDivider(color = BorderGray)
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                                .background(White)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = stringResource(R.string.btn_cancel),
                                style = MaterialTheme.typography.labelLarge.copy(color = NavyDark)
                            )
                        }
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ProgressPurple)
                                .clickable { onConfirm(selectedMinutes) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = stringResource(R.string.btn_confirm),
                                style = MaterialTheme.typography.labelLarge.copy(color = White)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Remove Child Confirm Dialog ──────────────────────────────────────────────
@Composable
private fun RemoveChildConfirmDialog(
    childName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint               = DangerRed,
                        modifier           = Modifier.size(40.dp)
                    )
                    Text(
                        text      = stringResource(R.string.settings_remove_confirm_title),
                        style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color     = NavyDark,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text      = stringResource(R.string.settings_remove_confirm_message, childName),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    HorizontalDivider(color = BorderGray)
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                                .background(White)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = stringResource(R.string.btn_cancel),
                                style = MaterialTheme.typography.labelLarge.copy(color = NavyDark)
                            )
                        }
                        Box(
                            modifier         = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DangerRed)
                                .clickable { onConfirm() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = stringResource(R.string.btn_confirm),
                                style = MaterialTheme.typography.labelLarge.copy(color = White)
                            )
                        }
                    }
                }
            }
        }
    }
}