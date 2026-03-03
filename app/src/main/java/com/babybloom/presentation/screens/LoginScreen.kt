package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.LoginViewModel
import com.babybloom.ui.theme.*

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsState()
    val focusManager  = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Navigation trigger ─────────────────────────────────────────────────
    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            viewModel.onNavigationHandled()
            onNavigateToHome()
        }
    }

    // ── Error snackbar ─────────────────────────────────────────────────────
    LaunchedEffect(uiState.loginError) {
        uiState.loginError?.let {
            snackbarHostState.showSnackbar(it)
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

            // ── Purple gradient top section (matches Register exactly) ─
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.40f)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top    = screenHeight * 0.26f,
                        bottom = 20.dp
                    )
            ) {
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
                    shape     = RoundedCornerShape(24.dp),
                    colors    = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(
                                    start   = 24.dp,
                                    top     = 56.dp,
                                    end     = 24.dp,
                                    bottom  = 24.dp
                                ),
                            horizontalAlignment = Alignment.Start
                        ) {

                            // ── Title ──────────────────────────────────────
                            Text(
                                text = stringResource(R.string.login_title),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign  = TextAlign.Center,
                                    fontSize   = 24.sp
                                ),
                                color    = NavyDark,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── EMAIL ──────────────────────────────────────
                            LoginLabel(text = stringResource(R.string.label_email))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value         = uiState.email,
                                onValueChange = viewModel::onEmailChange,
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
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction    = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                ),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── PASSWORD ───────────────────────────────────
                            LoginLabel(text = stringResource(R.string.label_password))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value         = uiState.password,
                                onValueChange = viewModel::onPasswordChange,
                                placeholder   = {
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
                                visualTransformation = if (uiState.isPasswordVisible)
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction    = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        viewModel.onLoginClick()
                                    }
                                ),
                                singleLine   = true,
                                trailingIcon = {
                                    IconButton(onClick = viewModel::onTogglePasswordVisibility) {
                                        Icon(
                                            painter = painterResource(
                                                if (uiState.isPasswordVisible) R.drawable.ic_eye_on
                                                else R.drawable.ic_eye_off
                                            ),
                                            contentDescription = null,
                                            tint = TextSecondary
                                        )
                                    }
                                }
                            )

                            // ── Forgot Password ────────────────────────────
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick  = { /* TODO */ },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Text(
                                        text  = stringResource(R.string.forgot_password),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LinkColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ── LOGIN BUTTON ───────────────────────────────
                            Button(
                                onClick  = viewModel::onLoginClick,
                                enabled  = !uiState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape  = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NavyDark,
                                    contentColor   = Color.White
                                )
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(22.dp),
                                        color       = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text  = stringResource(R.string.btn_login),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // ── OR Divider ─────────────────────────────────
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier  = Modifier.weight(1f),
                                    color     = BorderGray,
                                    thickness = 1.dp
                                )
                                Text(
                                    text     = stringResource(R.string.or),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    style    = MaterialTheme.typography.bodyMedium,
                                    color    = TextSecondary
                                )
                                HorizontalDivider(
                                    modifier  = Modifier.weight(1f),
                                    color     = BorderGray,
                                    thickness = 1.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text      = stringResource(R.string.no_account),
                                style     = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color     = TextSecondary,
                                modifier  = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // ── REGISTER BUTTON ────────────────────────────
                            Button(
                                onClick  = onNavigateToRegister,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape  = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NavyDark,
                                    contentColor   = Color.White
                                )
                            ) {
                                Text(
                                    text  = stringResource(R.string.btn_register_new),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        } // end Column

                        // ── Scrollbar (CenterEnd = left side in RTL) ───────
                        LoginVerticalScrollbar(
                            scrollState = scrollState,
                            modifier    = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 8.dp, horizontal = 3.dp)
                        )
                    } // end Box
                } // end Card
            }

            // ── Sun image (same position as Register) ──────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = screenHeight * 0.001f)
            ) {
                Image(
                    painter            = painterResource(id = R.drawable.ic_sun_character),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(screenWidth * 0.75f)
                        .align(Alignment.TopCenter)
                )
            }

            // ── Heart decoration (same position as Register) ───────────
            Image(
                painter            = painterResource(id = R.drawable.ic_heart_pink),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .size(screenWidth * 0.30f)
                    .align(Alignment.TopStart)
                    .offset(
                        x = screenWidth * 0.75f,
                        y = screenHeight * 0.20f
                    )
            )

            // ── Snackbar ───────────────────────────────────────────────
            SnackbarHost(
                hostState = snackbarHostState,
                modifier  = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ── Reusable label (identical to RegisterLabel) ────────────────────────────
@Composable
private fun LoginLabel(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color     = NavyDark,
        textAlign = TextAlign.Start,
        modifier  = Modifier.fillMaxWidth()
    )
}

// ── Scroll indicator (identical to RegisterScreen's VerticalScrollbar) ─────
@Composable
private fun LoginVerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue > 0) {
        val scrollProgress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        Box(
            modifier = modifier
                .width(4.dp)
                .background(
                    color = BorderGray,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.3f)
                    .offset(y = (scrollProgress * 450).dp)
                    .background(
                        color = NavyDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}