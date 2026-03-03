package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.babybloom.presentation.viewmodels.RegisterViewModel
import com.babybloom.ui.theme.*

@Composable
fun RegisterScreen(
    onCreateAccount: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    viewModel: RegisterViewModel = hiltViewModel()
) {
    var fullName          by remember { mutableStateOf("") }
    var email             by remember { mutableStateOf("") }
    var password          by remember { mutableStateOf("") }
    var confirmPassword   by remember { mutableStateOf("") }
    var passwordVisible   by remember { mutableStateOf(false) }
    var confirmPwdVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onCreateAccount()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ── FIX 1: Get screen dimensions to place images proportionally ────────
    // Using fractions of screen size instead of fixed dp values
    // This makes images appear at the same RELATIVE position on every device
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

                // ── FIX 2: Purple gradient fills exactly the top 35% of screen ──
                // Before: hardcoded 350.dp which was too small on large phones
                // Now: always 35% of whatever screen height the device has
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.40f)   // ← 35% of screen height
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    GradientPurpleLight,
                                    GradientPurpleMedium,
                                    GradientPurpleDark
                                )
                            ),
                            shape = RoundedCornerShape(
                                bottomStart = 50.dp,
                                bottomEnd   = 50.dp
                            )
                        )
                )

                // ── White scrollable card ──────────────────────────────────
                // FIX 3: Scroll indicator added inside the card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top    = screenHeight * 0.26f,  // ← proportional to screen
                            bottom = 20.dp
                        )
                ) {
                    // FIX 3: scrollState hoisted here so we can pass it to
                    // both the Column AND the scrollbar indicator
                    val scrollState = rememberScrollState()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .align(Alignment.TopCenter)
                            .border(
                                width = 2.dp,
                                color = BorderGray,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {

                            // ── Scrollable form content ────────────────────
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)   // ← uses hoisted state
                                    .padding(
                                        start   = 24.dp,
                                        top     = 56.dp,
                                        end     = 24.dp,
                                        bottom  = 24.dp
                                    ),
                                horizontalAlignment = Alignment.Start
                            ) {

                                // Title
                                Text(
                                    text = stringResource(R.string.register_title),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center,
                                        fontSize   = 24.sp
                                    ),
                                    color    = NavyDark,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── NAME ───────────────────────────────────
                                RegisterLabel(text = stringResource(R.string.label_name))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = fullName,
                                    onValueChange = {
                                        fullName = it
                                        viewModel.onNameChanged(it)
                                    },
                                    placeholder = {
                                        Text(
                                            text      = stringResource(R.string.hint_full_name),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle     = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    isError       = uiState.nameError != null,
                                    supportingText = {
                                        uiState.nameError?.let {
                                            Text(
                                                text      = it,
                                                color     = MaterialTheme.colorScheme.error,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    },
                                    colors    = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = NavyDark,
                                        unfocusedBorderColor = BorderGray,
                                        errorBorderColor     = MaterialTheme.colorScheme.error
                                    ),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── EMAIL ──────────────────────────────────
                                RegisterLabel(text = stringResource(R.string.label_email))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = email,
                                    onValueChange = {
                                        email = it
                                        viewModel.onEmailChanged(it)
                                    },
                                    placeholder = {
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
                                        uiState.emailError?.let {
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

                                // ── PASSWORD ───────────────────────────────
                                RegisterLabel(text = stringResource(R.string.label_password))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = password,
                                    onValueChange = {
                                        password = it
                                        viewModel.onPasswordChanged(it, confirmPassword)
                                    },
                                    placeholder = {
                                        Text(
                                            text      = stringResource(R.string.hint_password),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.passwordError != null,
                                    supportingText = {
                                        uiState.passwordError?.let {
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
                                    visualTransformation = if (passwordVisible)
                                        VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine      = true,
                                    trailingIcon    = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                painter = painterResource(
                                                    if (passwordVisible) R.drawable.ic_eye_on
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
                                RegisterLabel(text = stringResource(R.string.label_confirm_password))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = confirmPassword,
                                    onValueChange = {
                                        confirmPassword = it
                                        viewModel.onConfirmPasswordChanged(password, it)
                                    },
                                    placeholder = {
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
                                        uiState.confirmPasswordError?.let {
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
                                    visualTransformation = if (confirmPwdVisible)
                                        VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine      = true,
                                    trailingIcon    = {
                                        IconButton(onClick = { confirmPwdVisible = !confirmPwdVisible }) {
                                            Icon(
                                                painter = painterResource(
                                                    if (confirmPwdVisible) R.drawable.ic_eye_on
                                                    else R.drawable.ic_eye_off
                                                ),
                                                contentDescription = null,
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                // ── REGISTER BUTTON ────────────────────────
                                Button(
                                    onClick = {
                                        viewModel.register(
                                            name            = fullName,
                                            email           = email,
                                            password        = password,
                                            confirmPassword = confirmPassword
                                        )
                                    },
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
                                            text  = stringResource(R.string.btn_create_account),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // ── OR Divider ─────────────────────────────
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray, thickness = 1.dp)
                                    Text(
                                        text     = stringResource(R.string.or),
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        style    = MaterialTheme.typography.bodyMedium,
                                        color    = TextSecondary
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray, thickness = 1.dp)
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text      = stringResource(R.string.already_have_account),
                                    style     = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color     = TextSecondary,
                                    modifier  = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // ── LOGIN BUTTON ───────────────────────────
                                Button(
                                    onClick  = onLoginClick,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape    = RoundedCornerShape(14.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = NavyDark,
                                        contentColor   = Color.White
                                    )
                                ) {
                                    Text(
                                        text  = stringResource(R.string.btn_login),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            // ── FIX 3: Scroll indicator bar ───────────────
                            // Shows a thin bar on the right edge of the card
                            // Only visible when content is scrollable
                            // Fades in when user touches, fades out when idle
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

                // ── FIX 2: Sun image — proportional position ───────────────

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = screenHeight * 0.001f)
                ) {
                    Image(
                        painter            = painterResource(id = R.drawable.ic_sun_character),
                        contentDescription = "Sun Character",
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .size(screenWidth * 0.75f)   // 72% of screen width
                            .align(Alignment.TopCenter)
                    )
                }

                // ── FIX 2: Heart — proportional position ──────────────────
                // Placed at 28% from left, 29% from top of screen
                // This ratio stays the same on every device
                Image(
                    painter            = painterResource(id = R.drawable.ic_heart_pink),
                    contentDescription = "Decorative Heart",
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(screenWidth * 0.30f)        // 14% of screen width
                        .align(Alignment.TopStart)
                        .offset(
                            x = screenWidth * 0.75f,      // 4% from left edge
                            y = screenHeight * 0.20f      // 29% from top
                        )
                )
            }
        }
    }


// ── Reusable label ─────────────────────────────────────────────────────────
@Composable
private fun RegisterLabel(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color     = NavyDark,
        textAlign = TextAlign.Start,
        modifier  = Modifier.fillMaxWidth()
    )
}

// ── FIX 3: Custom scroll indicator composable ─────────────────────────────
// A thin rounded bar that shows scroll progress
// androidx.compose.foundation.ScrollState gives us value and maxValue
@Composable
private fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    // Only show the scrollbar if there is actually content to scroll
    if (scrollState.maxValue > 0) {
        // Calculate how far through the content we are (0.0 to 1.0)
        val scrollProgress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

        Box(
            modifier = modifier
                .width(4.dp)
                .background(
                    color = BorderGray,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            // The moving thumb inside the track
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.3f)           // thumb is 30% of track height
                    .offset(y = (scrollProgress * 450).dp)   // moves as user scrolls
                    .background(
                        color = NavyDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}