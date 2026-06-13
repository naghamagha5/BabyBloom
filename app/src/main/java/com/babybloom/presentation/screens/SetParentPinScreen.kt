package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.presentation.viewmodels.SetParentPinViewModel
import com.babybloom.ui.theme.BackgroundLight
import com.babybloom.ui.theme.NavyDark
import com.babybloom.ui.theme.ProgressPurple

@Composable
fun SetParentPinScreen(
    onPinSaved: () -> Unit,
    viewModel: SetParentPinViewModel = hiltViewModel()
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler { }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = NavyDark)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "إعداد قفل الوالدين",
                color = NavyDark,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ما هو قفل الوالدين؟",
                        color = NavyDark,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "خاصية قفل الوالدين مصممة لمساعدة الوالدين على التحكم في خروج الطفل من اللعبة. تمنع هذه الخاصية الطفل من مغادرة اللعبة أو الانتقال خارجها بسهولة دون معرفة أو تدخل أحد الوالدين.\n\nهذه الخاصية لا تهدف إلى تقييد الطفل بشكل كامل، بل إلى توفير طبقة حماية إضافية تساعد على بقاء الطفل داخل بيئة اللعب المناسبة له، وتقليل الخروج غير المقصود من التطبيق.\n\nللخروج، يجب على أحد الوالدين إتمام خطوة التحقق المطلوبة.",
                        color = NavyDark.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "اختر رقماً سرياً من 4 أرقام",
                modifier = Modifier.fillMaxWidth(),
                color = NavyDark,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start
            )

            Spacer(Modifier.height(10.dp))

            PinField(
                value = pin,
                label = "الرقم السري",
                enabled = !uiState.isSaving,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(4)
                    viewModel.clearError()
                }
            )

            Spacer(Modifier.height(12.dp))

            PinField(
                value = confirmPin,
                label = "تأكيد الرقم السري",
                enabled = !uiState.isSaving,
                onValueChange = {
                    confirmPin = it.filter(Char::isDigit).take(4)
                    viewModel.clearError()
                }
            )

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.savePin(pin, confirmPin, onPinSaved) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProgressPurple)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Text("حفظ ومتابعة", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PinField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(14.dp)
    )
}
