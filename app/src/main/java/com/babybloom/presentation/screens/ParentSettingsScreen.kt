package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.presentation.viewmodels.EditProfileState
import com.babybloom.presentation.viewmodels.ParentViewModel
import com.babybloom.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// ROOT COMPONENT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentSettingsContent(
    onNavigateToLogin     : () -> Unit = {},
    onNavigateToChangePwd : () -> Unit = {},
    onNavigateToAddChild  : () -> Unit = {},
    modifier              : Modifier   = Modifier,
    viewModel             : ParentViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val editState     by viewModel.editState.collectAsStateWithLifecycle()
    val userName      by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail     by viewModel.userEmail.collectAsStateWithLifecycle()
    val createdAt     by viewModel.createdAt.collectAsStateWithLifecycle()
    val childCount    by viewModel.childCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) { viewModel.onNavigationHandled(); onNavigateToLogin() }
    }

    LaunchedEffect(uiState.navigateToChangePwd) {
        if (uiState.navigateToChangePwd) { viewModel.onNavigationHandled(); onNavigateToChangePwd() }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost        = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        containerColor      = BackgroundLight
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, bottom = 80.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ProfileCard(
                    userName   = userName,
                    userEmail  = userEmail,
                    createdAt  = createdAt,
                    childCount = childCount,
                    viewModel  = viewModel
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsCard(viewModel = viewModel)
                Spacer(modifier = Modifier.height(16.dp))
                AboutCard()
                Spacer(modifier = Modifier.height(16.dp))
                DangerCard(
                    onLogoutClick = { viewModel.showLogoutDialog() },
                    onDeleteClick = { viewModel.showDeleteDialog() }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NavyDark)
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (uiState.showEditDialog) {
        EditProfileDialog(
            editState             = editState,
            onNameChange          = viewModel::onEditNameChanged,
            onEmailChange         = viewModel::onEditEmailChanged,
            onSave                = viewModel::saveProfileChanges,
            onDismiss             = viewModel::closeEditDialog,
            onChangePasswordClick = { viewModel.onChangePasswordClick() }
        )
    }

    if (uiState.showLogoutDialog) {
        ConfirmDialog(
            title         = stringResource(R.string.dialog_logout_title),
            message       = stringResource(R.string.dialog_logout_message),
            confirmText   = stringResource(R.string.btn_confirm_logout),
            onConfirm     = viewModel::confirmLogout,
            onDismiss     = viewModel::dismissLogoutDialog,
            isDestructive = false
        )
    }

    if (uiState.showDeleteDialog) {
        ConfirmDialog(
            title         = stringResource(R.string.dialog_delete_title),
            message       = stringResource(R.string.dialog_delete_message),
            confirmText   = stringResource(R.string.btn_confirm_delete),
            onConfirm     = viewModel::confirmDeleteAccount,
            onDismiss     = viewModel::dismissDeleteDialog,
            isDestructive = true
        )
    }

    val showSetPinDialog by viewModel.showSetPinDialog.collectAsStateWithLifecycle()
    val pinError         by viewModel.pinError.collectAsStateWithLifecycle()

    if (showSetPinDialog) {
        SetPinDialog(
            pinError  = pinError,
            onConfirm = { pin, confirm -> viewModel.saveParentPin(pin, confirm) },
            onDismiss = { viewModel.dismissSetPinDialog() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROFILE CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileCard(
    userName   : String,
    userEmail  : String,
    createdAt  : Long,
    childCount : Int,
    viewModel  : ParentViewModel
) {
    SectionCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(text = userName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                Text(
                    text       = stringResource(R.string.label_your_children_count, childCount),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Purple
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(GradientPurpleMedium.copy(alpha = 0.4f))
                        .clickable { viewModel.openEditDialog() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text       = stringResource(R.string.dialog_edit_profile_title),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color      = NavyDark
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Image(
                painter            = painterResource(id = R.drawable.family_image),
                contentDescription = null,
                modifier           = Modifier
                    .size(78.dp)
                    .clip(CircleShape),
                contentScale       = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        InfoCard(
            label   = stringResource(R.string.label_email_info),
            value   = userEmail,
            iconRes = R.drawable.ic_mail
        )
        Spacer(modifier = Modifier.height(10.dp))
        InfoCard(
            label   = stringResource(R.string.label_join_date),
            value   = viewModel.formatJoinDate(createdAt),
            iconRes = R.drawable.ic_date
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsCard(viewModel: ParentViewModel) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val soundEnabled         by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val musicEnabled         by viewModel.musicEnabled.collectAsStateWithLifecycle()
    val hasPin               by viewModel.hasPin.collectAsStateWithLifecycle()

    SectionCard {
        SectionTitle(text = stringResource(R.string.label_app_settings))
        SettingsToggleItem(
            label           = stringResource(R.string.label_notifications),
            subLabel        = stringResource(R.string.label_notifications_sub),
            iconRes         = R.drawable.ic_bell_filled,
            checked         = notificationsEnabled,
            onCheckedChange = { viewModel.toggleNotifications(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleItem(
            label           = stringResource(R.string.label_sound),
            subLabel        = stringResource(R.string.label_sound_sub),
            iconRes         = R.drawable.ic_sound,
            checked         = soundEnabled,
            onCheckedChange = { viewModel.toggleSound(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleItem(
            label           = stringResource(R.string.label_music),
            subLabel        = stringResource(R.string.label_music_sub),
            iconRes         = R.drawable.ic_music,
            checked         = musicEnabled,
            onCheckedChange = { viewModel.toggleMusic(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        PinSettingItem(
            hasPin      = hasPin,
            onSetPin    = { viewModel.showSetPinDialog() },
            onRemovePin = { viewModel.removeParentPin() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ABOUT CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AboutCard() {
    SectionCard {
        SectionTitle(text = stringResource(R.string.label_about))
        AboutItem(label = stringResource(R.string.label_privacy_policy))
        Spacer(modifier = Modifier.height(10.dp))
        AboutItem(label = stringResource(R.string.label_terms))
        Spacer(modifier = Modifier.height(10.dp))
        AboutItem(label = stringResource(R.string.label_help))
        Spacer(modifier = Modifier.height(10.dp))
        AboutItem(label = stringResource(R.string.label_about_app))
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text      = stringResource(R.string.label_app_version),
            fontSize  = 12.sp,
            color     = TextSecondary,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DANGER CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DangerCard(
    onLogoutClick : () -> Unit,
    onDeleteClick : () -> Unit
) {
    SectionCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
                .background(Color.White, RoundedCornerShape(14.dp))
                .clickable { onLogoutClick() }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = stringResource(R.string.btn_logout),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = NavyDark
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier         = Modifier
                        .size(34.dp)
                        .background(PurpleLavender.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(id = R.drawable.ic_logout),
                        contentDescription = null,
                        tint               = NavyDark,
                        modifier           = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
                .background(DeleteRowBackground, RoundedCornerShape(14.dp))
                .clickable { onDeleteClick() }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = stringResource(R.string.btn_delete_account),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = ErrorRed
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier         = Modifier
                        .size(34.dp)
                        .background(ErrorRed.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        tint               = ErrorRed,
                        modifier           = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EDIT PROFILE DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EditProfileDialog(
    editState             : EditProfileState,
    onNameChange          : (String) -> Unit,
    onEmailChange         : (String) -> Unit,
    onSave                : () -> Unit,
    onDismiss             : () -> Unit,
    onChangePasswordClick : () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text       = stringResource(R.string.dialog_edit_profile_title),
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark,
                    modifier   = Modifier.fillMaxWidth(),
                    textAlign  = TextAlign.End
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value          = editState.name,
                    onValueChange  = onNameChange,
                    label          = { Text(stringResource(R.string.label_name)) },
                    isError        = editState.nameError != null,
                    supportingText = { editState.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(12.dp),
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = NavyDark,
                        unfocusedBorderColor = BorderGray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value           = editState.email,
                    onValueChange   = onEmailChange,
                    label           = { Text(stringResource(R.string.label_email)) },
                    isError         = editState.emailError != null,
                    supportingText  = { editState.emailError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(12.dp),
                    colors          = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = NavyDark,
                        unfocusedBorderColor = BorderGray
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine      = true
                )
                editState.errorMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text      = it,
                        color     = ErrorRed,
                        fontSize  = 12.sp,
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
                editState.successMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text      = it,
                        color     = SuccessGreen,
                        fontSize  = 12.sp,
                        modifier  = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onChangePasswordClick() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = stringResource(R.string.label_change_password),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = GradientPurpleDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter            = painterResource(id = R.drawable.arrow_left_circle),
                        contentDescription = null,
                        tint               = NavyDark,
                        modifier           = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_cancel), color = TextSecondary)
                    }
                    Button(
                        onClick = onSave,
                        enabled = !editState.isLoading,
                        shape   = RoundedCornerShape(12.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = ProgressPurple)
                    ) {
                        if (editState.isLoading) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(18.dp),
                                color       = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.btn_save_changes), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CONFIRM DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ConfirmDialog(
    title         : String,
    message       : String,
    confirmText   : String,
    onConfirm     : () -> Unit,
    onDismiss     : () -> Unit,
    isDestructive : Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Text(
                text       = title,
                fontWeight = FontWeight.Bold,
                color      = NavyDark,
                textAlign  = TextAlign.End,
                modifier   = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text      = message,
                color     = TextSecondary,
                textAlign = TextAlign.End,
                fontSize  = 14.sp,
                modifier  = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) ErrorRed else ProgressPurple
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmText, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 17.dp)
            .fillMaxWidth()
            .shadow(
                elevation    = 8.dp,
                shape        = RoundedCornerShape(24.dp),
                ambientColor = ShadowColor.copy(alpha = 0.07f),
                spotColor    = ShadowColor.copy(alpha = 0.07f)
            )
            .background(color = SectionCardBackground, shape = RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text,
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign  = TextAlign.End,
        color      = NavyDark,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
fun SettingsToggleItem(
    label           : String,
    subLabel        : String,
    iconRes         : Int,
    checked         : Boolean,
    onCheckedChange : (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Switch(
                checked         = checked,
                onCheckedChange = onCheckedChange,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = ProgressPurple,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BorderGray
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(label,    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                    Text(subLabel, fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .background(PurpleLavender.copy(alpha = 0.5f), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(id = iconRes),
                        contentDescription = label,
                        tint               = ProgressPurple,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AboutItem(
    label   : String,
    onClick : (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text      = label,
                fontSize  = 15.sp,
                color     = TextPrimary,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, iconRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(label, fontSize = 11.sp, color = TextSecondary)
                Text(value, fontSize = 14.sp, color = NavyDark)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                painter            = painterResource(id = iconRes),
                contentDescription = label,
                tint               = NavyDark,
                modifier           = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PIN SETTING ITEM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PinSettingItem(
    hasPin      : Boolean,
    onSetPin    : () -> Unit,
    onRemovePin : () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            if (hasPin) {
                TextButton(
                    onClick = onRemovePin,
                    colors  = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("إزالة الرقم", fontSize = 13.sp)
                }
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier              = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier            = Modifier.weight(1f)
                ) {
                    Text(
                        text       = if (hasPin) "تغيير رقم قفل الوالدين"
                        else "تعيين رقم قفل الوالدين",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyDark
                    )
                    Text(
                        text  = if (hasPin) "رقم القفل مفعل ✓"
                        else "يمنع الطفل من الخروج من اللعبة",
                        fontSize = 12.sp,
                        color    = if (hasPin) SuccessGreen else TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier         = Modifier
                        .size(40.dp)
                        .background(PurpleLavender.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
                        .clickable { onSetPin() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(id = R.drawable.ic_lock),
                        contentDescription = null,
                        tint               = ProgressPurple,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SET PIN DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SetPinDialog(
    pinError  : String?,
    onConfirm : (pin: String, confirmPin: String) -> Unit,
    onDismiss : () -> Unit
) {
    var pin        by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val dotCount = 4
    var step by remember { mutableStateOf(0) } // 0 = enter pin, 1 = confirm pin

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier                = Modifier.padding(24.dp),
                horizontalAlignment     = Alignment.CenterHorizontally,
                verticalArrangement     = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text       = if (step == 0) "أدخل الرقم الجديد" else "أكد الرقم",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark
                )

                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val current = if (step == 0) pin else confirmPin
                    repeat(dotCount) { i ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (i < current.length) ProgressPurple else BorderGray,
                                    CircleShape
                                )
                        )
                    }
                }

                // Numpad
                val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    items(keys) { key ->
                        when (key) {
                            "" -> Box(Modifier.size(56.dp))
                            "⌫" -> OutlinedButton(
                                onClick  = {
                                    if (step == 0 && pin.isNotEmpty())
                                        pin = pin.dropLast(1)
                                    else if (step == 1 && confirmPin.isNotEmpty())
                                        confirmPin = confirmPin.dropLast(1)
                                },
                                modifier = Modifier.size(56.dp)
                            ) { Text(key) }
                            else -> Button(
                                onClick = {
                                    if (step == 0 && pin.length < dotCount) {
                                        pin += key
                                        if (pin.length == dotCount) step = 1
                                    } else if (step == 1 && confirmPin.length < dotCount) {
                                        confirmPin += key
                                        if (confirmPin.length == dotCount) {
                                            onConfirm(pin, confirmPin)
                                        }
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = ProgressPurple
                                )
                            ) {
                                Text(key, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                pinError?.let {
                    Text(it, color = ErrorRed, fontSize = 13.sp)
                }

                TextButton(onClick = onDismiss) {
                    Text("إلغاء", color = TextSecondary)
                }
            }
        }
    }
}