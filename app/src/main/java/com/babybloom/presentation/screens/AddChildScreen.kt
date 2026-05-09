package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.AddChildViewModel
import com.babybloom.ui.theme.*

@Composable
fun AddChildScreen(
    onSaveChild: (Long) -> Unit = {},
    viewModel  : AddChildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ── Navigate to Home when child saved successfully ─────────────────────
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            uiState.savedChildId?.let(onSaveChild)
        }
    }

    // ── Snackbar for unexpected DB errors ──────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            val message = when (it) {
                "error_saving_child" -> context.getString(R.string.error_saving_child)
                else                 -> it
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // ── Resolves validation key → Arabic string ────────────────────────────
    @Composable
    fun resolveError(key: String?): String? = key?.let {
        when (it) {
            "error_name_required"      -> stringResource(R.string.error_name_required)
            "error_name_too_short"     -> stringResource(R.string.error_name_min_length)
            "error_name_too_long"      -> stringResource(R.string.error_name_max_length)
            "error_name_invalid_chars" -> stringResource(R.string.error_name_invalid_characters)
            "error_age_required"       -> stringResource(R.string.error_age_required)
            "error_age_not_number"     -> stringResource(R.string.error_age_not_number)
            "error_age_too_low"        -> stringResource(R.string.error_age_too_low)
            "error_age_too_high"       -> stringResource(R.string.error_age_too_high)
            "error_gender_required"    -> stringResource(R.string.error_gender_required)
            "error_avatar_required"    -> stringResource(R.string.error_avatar_required)
            else                       -> it
        }
    }

    // ── Avatar lookup tables — index maps to asset path and drawable ───────
    // Index 1–6 matches what the design uses for selectedBoyAvatar/selectedGirlAvatar
    val boyAvatarPaths = mapOf(
        1 to "avatars/boy_1.webp", 2 to "avatars/boy_2.webp", 3 to "avatars/boy_3.webp",
        4 to "avatars/boy_4.webp", 5 to "avatars/boy_5.webp", 6 to "avatars/boy_6.webp"
    )
    val girlAvatarPaths = mapOf(
        1 to "avatars/girl_1.webp", 2 to "avatars/girl_2.webp", 3 to "avatars/girl_3.webp",
        4 to "avatars/girl_4.webp", 5 to "avatars/girl_5.webp", 6 to "avatars/girl_6.webp"
    )
    val boyDrawables = mapOf(
        1 to R.drawable.avatar_boy_1, 2 to R.drawable.avatar_boy_2, 3 to R.drawable.avatar_boy_3,
        4 to R.drawable.avatar_boy_4, 5 to R.drawable.avatar_boy_5, 6 to R.drawable.avatar_boy_6
    )
    val girlDrawables = mapOf(
        1 to R.drawable.avatar_girl_1, 2 to R.drawable.avatar_girl_2, 3 to R.drawable.avatar_girl_3,
        4 to R.drawable.avatar_girl_4, 5 to R.drawable.avatar_girl_5, 6 to R.drawable.avatar_girl_6
    )

    // Helper: which index is currently selected? (reverse lookup from path)
    fun selectedBoyIndex(): Int? =
        boyAvatarPaths.entries.firstOrNull { it.value == uiState.selectedAvatar }?.key
    fun selectedGirlIndex(): Int? =
        girlAvatarPaths.entries.firstOrNull { it.value == uiState.selectedAvatar }?.key

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

                // ── 1. Purple Gradient (Top 40%) ───────────────────────────
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

                // ── 2. White Scrollable Card ───────────────────────────────
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
                                    text     = stringResource(R.string.add_child_title),
                                    style    = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center,
                                        fontSize   = 24.sp
                                    ),
                                    color    = NavyDark,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── CHILD NAME ─────────────────────────────
                                AddChildLabel(text = stringResource(R.string.label_child_name))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.childName,
                                    onValueChange = { viewModel.onNameChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_child_name),
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

                                // ── CHILD AGE ──────────────────────────────
                                AddChildLabel(text = stringResource(R.string.label_child_age))
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value         = uiState.childAge,
                                    onValueChange = { viewModel.onAgeChanged(it) },
                                    placeholder   = {
                                        Text(
                                            text      = stringResource(R.string.hint_child_age),
                                            color     = TextSecondary,
                                            modifier  = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Start
                                        )
                                    },
                                    textStyle      = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                    modifier       = Modifier.fillMaxWidth(),
                                    shape          = RoundedCornerShape(12.dp),
                                    isError        = uiState.ageError != null,
                                    supportingText = {
                                        resolveError(uiState.ageError)?.let {
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
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine      = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── LEARNING DIFFICULTIES — Yes/No toggle ──
                                AddChildLabel(text = stringResource(R.string.label_has_difficulties))
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick  = { viewModel.onHasDifficultiesChanged(true) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.hasDifficulties) NavyDark else Color.LightGray,
                                            contentColor   = if (uiState.hasDifficulties) Color.White else TextSecondary
                                        )
                                    ) { Text(text = stringResource(R.string.yes)) }

                                    Button(
                                        onClick  = { viewModel.onHasDifficultiesChanged(false) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(
                                            containerColor = if (!uiState.hasDifficulties) NavyDark else Color.LightGray,
                                            contentColor   = if (!uiState.hasDifficulties) Color.White else TextSecondary
                                        )
                                    ) { Text(text = stringResource(R.string.no)) }
                                }

                                // Description field — visible only when Yes is selected
                                if (uiState.hasDifficulties) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AddChildLabel(text = stringResource(R.string.label_difficulties_description))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value         = uiState.notes,
                                        onValueChange = { viewModel.onNotesChanged(it) },
                                        placeholder   = {
                                            Text(
                                                text      = stringResource(R.string.hint_difficulties_description),
                                                color     = TextSecondary,
                                                modifier  = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Start
                                            )
                                        },
                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                        modifier  = Modifier.fillMaxWidth().height(120.dp),
                                        shape     = RoundedCornerShape(12.dp),
                                        colors    = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor   = NavyDark,
                                            unfocusedBorderColor = BorderGray
                                        ),
                                        maxLines = 5,
                                        minLines = 3
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // ── SELECT GENDER ──────────────────────────
                                AddChildLabel(text = stringResource(R.string.label_select_gender))
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick  = { viewModel.onGenderChanged(false) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(
                                            // null = not picked yet → neither button highlighted
                                            containerColor = if (uiState.isGirlSelected == false) NavyDark else Color.LightGray,
                                            contentColor   = if (uiState.isGirlSelected == false) Color.White else TextSecondary
                                        )
                                    ) { Text(text = stringResource(R.string.boy)) }

                                    Button(
                                        onClick  = { viewModel.onGenderChanged(true) },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.isGirlSelected == true) NavyDark else Color.LightGray,
                                            contentColor   = if (uiState.isGirlSelected == true) Color.White else TextSecondary
                                        )
                                    ) { Text(text = stringResource(R.string.girl)) }
                                }
                                // Gender error shown below the buttons
                                resolveError(uiState.genderError)?.let {
                                    Text(
                                        text      = it,
                                        color     = MaterialTheme.colorScheme.error,
                                        style     = MaterialTheme.typography.bodySmall,
                                        modifier  = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textAlign = TextAlign.Start
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // ── CHOOSE AVATAR ──────────────────────────
                                Text(
                                    text     = stringResource(R.string.label_choose_avatar),
                                    style    = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        textAlign  = TextAlign.Center
                                    ),
                                    color    = NavyDark,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // ── BOY AVATARS ────────────────────────────
                                if (uiState.isGirlSelected == false) {
                                    AvatarCard {
                                        (1..6).forEach { index ->
                                            AvatarOption(
                                                avatarId   = boyDrawables[index]!!,
                                                isSelected = selectedBoyIndex() == index,
                                                onClick    = {
                                                    viewModel.onAvatarSelected(boyAvatarPaths[index]!!)
                                                }
                                            )
                                        }
                                    }
                                }

                                // ── GIRL AVATARS ───────────────────────────
                                if (uiState.isGirlSelected == true) {
                                    AvatarCard {
                                        (1..6).forEach { index ->
                                            AvatarOption(
                                                avatarId   = girlDrawables[index]!!,
                                                isSelected = selectedGirlIndex() == index,
                                                onClick    = {
                                                    viewModel.onAvatarSelected(girlAvatarPaths[index]!!)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Gender not selected yet — show hint instead of avatars
                                if (uiState.isGirlSelected == null) {
                                    Box(
                                        modifier         = Modifier.fillMaxWidth().height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text      = stringResource(R.string.hint_select_gender_first),
                                            color     = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Avatar error shown below avatar section
                                resolveError(uiState.avatarError)?.let {
                                    Text(
                                        text      = it,
                                        color     = MaterialTheme.colorScheme.error,
                                        style     = MaterialTheme.typography.bodySmall,
                                        modifier  = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textAlign = TextAlign.Start
                                    )
                                }

                                Spacer(modifier = Modifier.height(28.dp))

                                // ── Divider ────────────────────────────────
                                Row(
                                    modifier          = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier  = Modifier.weight(1f),
                                        color     = BorderGray,
                                        thickness = 1.dp
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // ── SAVE BUTTON ────────────────────────────
                                Button(
                                    onClick  = { viewModel.saveChild() },
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
                                            text  = stringResource(R.string.btn_save),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // ── Scroll indicator bar ───────────────────────
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

                // ── 3. Sun Image ───────────────────────────────────────────
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
                            .size(screenWidth * 0.75f)
                            .align(Alignment.TopCenter)
                    )
                }

                // ── 4. Heart Decoration ────────────────────────────────────
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


// ── Orange gradient card wrapping the avatar row ───────────────────────────
@Composable
private fun AvatarCard(content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(GradientOrangeLight, GradientOrangeMedium, GradientOrangeDark)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                content               = content
            )
        }
    }
}

// ── Single avatar circle ───────────────────────────────────────────────────
@Composable
private fun AvatarOption(
    avatarId  : Int,
    isSelected: Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(100.dp)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) NavyDark else Color.Transparent,
                shape = CircleShape
            ),
        shape   = CircleShape,
        colors  = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.White else Color.Transparent
        ),
        onClick = onClick
    ) {
        Image(
            painter            = painterResource(id = avatarId),
            contentDescription = "Avatar",
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )
    }
}

// ── Reusable label ─────────────────────────────────────────────────────────
@Composable
private fun AddChildLabel(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color     = NavyDark,
        textAlign = TextAlign.Start,
        modifier  = Modifier.fillMaxWidth()
    )
}

// ── Scroll indicator ───────────────────────────────────────────────────────
@Composable
private fun VerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
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
