package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.ActivityViewModel
import com.babybloom.presentation.viewmodels.ParentHomeViewModel

enum class LockInputMode { PIN, PASSWORD }

@Composable
fun ParentLockScreen(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
) {
    var inputMode by remember { mutableStateOf(LockInputMode.PIN) }
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val dotCount = 4

    BackHandler { /* swallow — can't back out of the lock screen */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.parent_lock_overlay)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = if (inputMode == LockInputMode.PIN)
                    stringResource(R.string.parent_lock_enter_pin)
                else
                    stringResource(R.string.parent_lock_enter_password),
                style = MaterialTheme.typography.titleLarge
            )

            if (inputMode == LockInputMode.PIN) {
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
                NumPad(
                    onDigit = {
                        if (input.length < dotCount) input += it
                    },
                    onDelete = { if (input.isNotEmpty()) input = input.dropLast(1) },
                    onConfirm = { viewModel.verifyPin(input, onUnlocked) { errorMessage = it } }
                )
                TextButton(onClick = { inputMode = LockInputMode.PASSWORD; input = "" }) {
                    Text(stringResource(R.string.parent_lock_forgot_pin))
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.parent_lock_password_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(onClick = {
                    viewModel.verifyPassword(input, onUnlocked) { errorMessage = it }
                }) {
                    Text(stringResource(R.string.parent_lock_confirm))
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.parent_lock_cancel))
            }
        }
    }
}

@Composable
private fun NumPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit
) {
    val deleteKey = stringResource(R.string.numpad_delete)
    val keys = listOf("1","2","3","4","5","6","7","8","9","","0", deleteKey)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(keys) { key ->
            when (key) {
                ""        -> Box(Modifier.size(64.dp))
                deleteKey -> OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.size(64.dp)
                ) { Text(key) }
                else -> Button(
                    onClick = {
                        onDigit(key)
                        if (key == "0") onConfirm()
                    },
                    modifier = Modifier.size(64.dp)
                ) { Text(key, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
