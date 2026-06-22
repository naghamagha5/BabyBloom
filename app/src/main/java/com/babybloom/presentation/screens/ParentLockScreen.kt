package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.ActivityViewModel
import kotlinx.coroutines.delay

private val DarkNavy = Color(0xFF1A1A2E)

enum class LockInputMode { PIN, PASSWORD }

@Composable
fun ParentLockScreen(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
) {
    var inputMode    by remember { mutableStateOf(LockInputMode.PIN) }
    var input        by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var secondsLeft  by remember { mutableStateOf(60) }
    val dotCount = 4

    BackHandler { /* swallow — can't back out of the lock screen */ }

    // ── 60-second countdown — auto-dismiss like cancel when it reaches 0 ──
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        onDismiss()
    }

    // ── Auto-confirm when 4 digits are entered ────────────────────────────
    LaunchedEffect(input) {
        if (inputMode == LockInputMode.PIN && input.length == dotCount) {
            viewModel.verifyPin(input, onUnlocked) { error ->
                errorMessage = error
                input = ""
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.parent_lock_overlay)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {

            // ── Lock icon in dark circle ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(DarkNavy, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter            = painterResource(id = R.drawable.ic_lock),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // ── Title ─────────────────────────────────────────────────────
            Text(
                text = if (inputMode == LockInputMode.PIN)
                    stringResource(R.string.parent_lock_enter_pin)
                else
                    stringResource(R.string.parent_lock_enter_password),
                style = MaterialTheme.typography.titleLarge
            )

            // ── Countdown pill ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "المتبقي: $secondsLeft ثانية",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (inputMode == LockInputMode.PIN) {

                // ── 4 dots ────────────────────────────────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(dotCount) { i ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (i < input.length)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                    }
                }

                // ── Numpad ────────────────────────────────────────────────
                NumPad(
                    onDigit  = { if (input.length < dotCount) input += it },
                    onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) }
                )

                // ── Forgot PIN button — dark background ───────────────────
                Button(
                    onClick = {
                        inputMode = LockInputMode.PASSWORD
                        input = ""
                        errorMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkNavy,
                        contentColor   = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.parent_lock_forgot_pin))
                }

            } else {

                OutlinedTextField(
                    value                = input,
                    onValueChange        = { input = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label                = { Text(stringResource(R.string.parent_lock_password_label)) },
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier             = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.verifyPassword(input, onUnlocked) { error ->
                            errorMessage = error
                            input = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.parent_lock_confirm))
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            // ── Cancel button — dark background ───────────────────────────
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkNavy,
                    contentColor   = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.parent_lock_cancel))
            }
        }
    }
}

@Composable
private fun NumPad(
    onDigit  : (String) -> Unit,
    onDelete : () -> Unit
) {
    val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        // Disable internal scrolling — grid always shows all 12 cells
        userScrollEnabled     = false
    ) {
        items(keys) { key ->
            when (key) {
                ""   -> Box(Modifier.size(64.dp))           // empty spacer cell
                "⌫"  -> Button(
                    onClick  = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = Color.White
                    )
                ) {
                    Text(key, style = MaterialTheme.typography.titleLarge)
                }
                else -> Button(
                    onClick  = { onDigit(key) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text(key, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}