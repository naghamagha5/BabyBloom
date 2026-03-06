package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.ChangePasswordViewModel
import com.babybloom.ui.theme.*

@Composable
fun ChangePasswordScreen(
    onSaveClick : () -> Unit = {},   // after successful update → back to Login
    onBackClick : () -> Unit = {},   // back button → back to Login
    viewModel   : ChangePasswordViewModel = hiltViewModel()
) {
    val uiState           = viewModel.uiState.collectAsState().value
    val unexpectedErrorMessage = stringResource(R.string.error_generic_unexpected)
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate back to Login on success
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onSaveClick()
    }

    // Snackbar for unexpected errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            val message = when (it) {
                "error_unexpected" -> unexpectedErrorMessage
                else               -> it
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // Resolve validation key → Arabic string from strings.xml
    @Composable
    fun resolveError(key: String?): String? = key?.let {
        when (it) {
            "error_name_required"         -> stringResource(R.string.error_name_required)
            "error_name_too_short"        -> stringResource(R.string.error_name_min_length)
            "error_name_too_long"         -> stringResource(R.string.error_name_max_length)
            "error_name_invalid_chars"    -> stringResource(R.string.error_name_invalid_characters)
            "error_name_mismatch"         -> stringResource(R.string.error_name_mismatch)
            "error_email_required"        -> stringResource(R.string.error_email_required)
            "error_email_has_spaces"      -> stringResource(R.string.error_email_no_spaces)
            "error_email_invalid_format"  -> stringResource(R.string.error_email_invalid_format)
            "error_email_too_long"        -> stringResource(R.string.error_email_too_long)
            "error_email_not_found"       -> stringResource(R.string.error_email_not_found)
            "error_password_required"     -> stringResource(R.string.error_password_required)
            "error_password_too_short"    -> stringResource(R.string.error_password_min_length)
            "error_password_too_long"     -> stringResource(R.string.error_password_max_length)
            "error_password_no_uppercase" -> stringResource(R.string.error_password_no_uppercase)
            "error_password_no_lowercase" -> stringResource(R.string.error_password_no_lowercase)
            "error_password_no_digit"     -> stringResource(R.string.error_password_no_digit)
            "error_password_no_special"   -> stringResource(R.string.error_password_no_special_char)
            "error_password_has_spaces"   -> stringResource(R.string.error_password_no_spaces)
            "error_password_same_as_old"  -> stringResource(R.string.error_password_same_as_old)
            "error_confirm_required"      -> stringResource(R.string.error_confirm_password_required)
            "error_confirm_mismatch"      -> stringResource(R.string.error_passwords_mismatch)
            else                          -> it
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth   = configuration.screenWidthDp.dp
    val screenHeight  = configuration.screenHeightDp.dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(GradientPinkDark, GradientPinkMedium, GradientPinkLight)
                        )
                    )
            ) {

                // ── 1. Purple Gradient Card (Top 40%) ──────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.40f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(GradientPurpleLight, GradientPurpleMedium, GradientPurpleDark)
                            ),
                            shape = RoundedCornerShape(bottomStart = 50.dp, bottomEnd = 50.dp)
                        )
                )

                // ── 2. Back Button (Top-End corner) ────────────────────────
                IconButton(
                    onClick  = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 25.dp, top = 25.dp)
                        .size(50.dp)
                        .background(color = NavyDark.copy(alpha = 0.2f), shape = CircleShape)
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.undo),
                        contentDescription = "Back",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.size(28.dp),
                        colorFilter        = ColorFilter.tint(NavyDark)
                    )
                }

                // ── 3. White Scrollable Card ───────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = screenHeight * 0.26f, bottom = 20.dp)
                ) {
                    val scrollState = rememberScrollState()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.TopCenter)
                            .border(width = 2.dp, color = BorderGray, shape = RoundedCornerShape(24.dp)),
                        shape     = RoundedCornerShape(24.dp),
                        colors    = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(start = 24.dp, top = 56.dp, end = 24.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.Start
                            ) {

                                // ── Title ──────────────────────────────────
                                Text(
                                    text     = stringResource(R.string.change_password_title),
                                    style    = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center,
                                        fontSize   = 24.sp
                                    ),
                                    color    = NavyDark,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── FULL NAME ──────────────────────────────
                                ChangePasswordLabel(text = stringResource(R.string.label_name))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.fullName,
                                    onValueChange = { viewModel.onNameChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_full_name),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.nameError != null,
                                    supportingText = {
                                        resolveError(uiState.nameError)?.let {
                                            Text(
                                                text      = it,
                                                color     = MaterialTheme.colorScheme.error,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    },
                                    colors     = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = NavyDark,
                                        unfocusedBorderColor = BorderGray,
                                        errorBorderColor     = MaterialTheme.colorScheme.error
                                    ),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── EMAIL ──────────────────────────────────
                                ChangePasswordLabel(text = stringResource(R.string.label_email))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.email,
                                    onValueChange = { viewModel.onEmailChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_email),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.emailError != null,
                                    supportingText = {
                                        resolveError(uiState.emailError)?.let {
                                            Text(
                                                text      = it,
                                                color     = MaterialTheme.colorScheme.error,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = NavyDark,
                                        unfocusedBorderColor = BorderGray,
                                        errorBorderColor     = MaterialTheme.colorScheme.error
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine      = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── NEW PASSWORD ───────────────────────────
                                ChangePasswordLabel(text = stringResource(R.string.label_new_password))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.newPassword,
                                    onValueChange = { viewModel.onNewPasswordChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_new_password),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.newPasswordError != null,
                                    supportingText = {
                                        resolveError(uiState.newPasswordError)?.let {
                                            Text(
                                                text      = it,
                                                color     = MaterialTheme.colorScheme.error,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = NavyDark,
                                        unfocusedBorderColor = BorderGray,
                                        errorBorderColor     = MaterialTheme.colorScheme.error
                                    ),
                                    visualTransformation = if (uiState.newPasswordVisible)
                                        VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine      = true,
                                    trailingIcon    = {
                                        IconButton(onClick = { viewModel.toggleNewPasswordVisibility() }) {
                                            Icon(
                                                painter = painterResource(
                                                    if (uiState.newPasswordVisible) R.drawable.ic_eye_on
                                                    else R.drawable.ic_eye_off
                                                ),
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── CONFIRM PASSWORD ───────────────────────
                                ChangePasswordLabel(text = stringResource(R.string.label_confirm_password))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.confirmPassword,
                                    onValueChange = { viewModel.onConfirmPasswordChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_confirm_password),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.confirmPasswordError != null,
                                    supportingText = {
                                        resolveError(uiState.confirmPasswordError)?.let {
                                            Text(
                                                text      = it,
                                                color     = MaterialTheme.colorScheme.error,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = NavyDark,
                                        unfocusedBorderColor = BorderGray,
                                        errorBorderColor     = MaterialTheme.colorScheme.error
                                    ),
                                    visualTransformation = if (uiState.confirmPwdVisible)
                                        VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine      = true,
                                    trailingIcon    = {
                                        IconButton(onClick = { viewModel.toggleConfirmPasswordVisibility() }) {
                                            Icon(
                                                painter = painterResource(
                                                    if (uiState.confirmPwdVisible) R.drawable.ic_eye_on
                                                    else R.drawable.ic_eye_off
                                                ),
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // ── CONFIRM CHANGE BUTTON ──────────────────
                                Button(
                                    onClick  = { viewModel.saveNewPassword() },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape    = RoundedCornerShape(14.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = NavyDark,
                                        contentColor   = Color.White
                                    ),
                                    enabled = !uiState.isLoading
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(22.dp),
                                            color       = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text  = stringResource(R.string.btn_confirm_change_password),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            VerticalScrollbar(
                                scrollState = scrollState,
                                modifier    = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp, horizontal = 3.dp)
                            )
                        }
                    }
                }

                // ── 4. Sun Image ───────────────────────────────────────────
                Box(
                    modifier = Modifier.fillMaxWidth().offset(y = screenHeight * 0.001f)
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_sun_character),
                        contentDescription = "Sun Character",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.size(screenWidth * 0.75f).align(Alignment.TopCenter)
                    )
                }

                // ── 5. Heart Decoration ────────────────────────────────────
                Image(
                    painter            = painterResource(id = R.drawable.ic_heart_pink),
                    contentDescription = "Decorative Heart",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(screenWidth * 0.30f)
                        .align(Alignment.TopStart)
                        .offset(x = screenWidth * 0.75f, y = screenHeight * 0.20f)
                )
            }
        }
    }

@Composable
private fun ChangePasswordLabel(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color     = NavyDark,
        textAlign = TextAlign.Start,
        modifier  = Modifier.fillMaxWidth()
    )
}

@Composable
private fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier   : Modifier = Modifier
) {
    if (scrollState.maxValue > 0) {
        val scrollProgress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        Box(
            modifier = modifier
                .width(4.dp)
                .background(color = BorderGray, shape = RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.3f)
                    .offset(y = (scrollProgress * 450).dp)
                    .background(color = NavyDark.copy(alpha = 0.5f), shape = RoundedCornerShape(2.dp))
            )
        }
    }
}