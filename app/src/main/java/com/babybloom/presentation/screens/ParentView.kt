package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.ui.theme.DarkNavy
import com.babybloom.ui.theme.judson
import com.babybloom.ui.theme.Jomhuriaregular
import com.babybloom.ui.theme.arimo_regular
import com.babybloom.ui.theme.rakkas
import com.babybloom.presentation.viewmodels.ParentViewModel
import com.babybloom.ui.theme.arimo_regular

// ── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun ParentView(
    modifier: Modifier = Modifier,
    viewModel: ParentViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val createdAt by viewModel.createdAt.collectAsStateWithLifecycle()
    val greeting = viewModel.greeting

    Box(modifier = modifier.fillMaxSize()) {

        // ── Main scrollable content ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F6F9))
                .zIndex(0f)
                .verticalScroll(rememberScrollState())
        ) {

            // Greeting + Name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp, end = 100.dp, start = 24.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "$greeting،",
                    fontSize = 30.sp,
                    fontFamily = judson,
                    color = DarkNavy
                )
                Text(
                    text = userName,
                    fontSize = 25.sp,
                    fontFamily = rakkas,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }

            // ── First Blue Card: Profile ──────────────────────────────────
            ProfileCard(
                userName = userName,
                userEmail = userEmail,
                createdAt = createdAt,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Second Blue Card: Settings ────────────────────────────────
            SettingsCard(viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            // ── Third Blue Card: About ────────────────────────────────────
            AboutCard()

            Spacer(modifier = Modifier.height(24.dp))

        } // end Column

        // ── Leaf image ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 35.dp)
                .graphicsLayer(scaleX = -1f)
                .paint(
                    painterResource(id = R.drawable.ic_leaf_corner_tl),
                    contentScale = ContentScale.Fit
                )
        )

        // ── Notification button ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
                .offset(y = 60.dp)
        ) {
            IconButton(onClick = {
                viewModel.playButtonSound()
            }) {
                Image(
                    painter = painterResource(id = R.drawable.notification_button),
                    contentDescription = "Notifications",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    } // end Box
}

// ── Profile Card ─────────────────────────────────────────────────────────────
@Composable
fun ProfileCard(
    userName: String,
    userEmail: String,
    createdAt: Long,
    viewModel: ParentViewModel
) {
    Box(
        modifier = Modifier
            .offset(y = 10.dp)
            .padding(horizontal = 17.dp)
            .fillMaxWidth()
            .height(330.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF2E2645).copy(alpha = 0.1f),
                spotColor = Color(0xFF2E2645).copy(alpha = 0.1f)
            )
            .background(color = Color(0xFFEAEFFF), shape = RoundedCornerShape(24.dp))
            .padding(top = 25.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Profile row: name + image side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = userName,
                        fontSize = 22.sp,
                        fontFamily = rakkas,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        text = "عدد اطفالك 4",
                        fontSize = 14.sp,
                        fontFamily = rakkas,
                        color = Color(0xFF7B6FE8)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(id = R.drawable.family_image),
                    contentDescription = "Family",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Email card
            InfoCard(
                label = "البريد الإلكتروني",
                value = userEmail,
                iconRes = R.drawable.email_icon
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Join date card
            InfoCard(
                label = "تاريخ الانضمام",
                value = viewModel.formatJoinDate(createdAt),
                iconRes = R.drawable.calender_icon
            )
        }
    }
}

// ── Settings Card ─────────────────────────────────────────────────────────────
@Composable
fun SettingsCard(viewModel: ParentViewModel) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val musicEnabled by viewModel.musicEnabled.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .padding(horizontal = 17.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF2E2645).copy(alpha = 0.1f),
                spotColor = Color(0xFF2E2645).copy(alpha = 0.1f)
            )
            .background(color = Color(0xFFEAEFFF), shape = RoundedCornerShape(24.dp))
            .padding(top = 20.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Title
            Text(
                text = "إعدادات التطبيق",
                fontSize = 20.sp,
                fontFamily = Jomhuriaregular,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = DarkNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Notifications toggle
            SettingsToggleItem(
                label = "الإشعارات",
                subLabel = "إشعارات التطبيق",
                iconRes = R.drawable.notification_button,
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sound toggle
            SettingsToggleItem(
                label = "الأصوات",
                subLabel = "مؤثرات صوتية للتطبيق",
                iconRes = R.drawable.speaker_icon,
                checked = soundEnabled,
                onCheckedChange = { viewModel.toggleSound(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Music toggle
            SettingsToggleItem(
                label = "موسيقى الخلفية",
                subLabel = "موسيقى هادئة",
                iconRes = R.drawable.music_icon,
                checked = musicEnabled,
                onCheckedChange = { viewModel.toggleMusic(it) }
            )
        }
    }
}

// ── Settings Toggle Item ──────────────────────────────────────────────────────
@Composable
fun SettingsToggleItem(
    label: String,
    subLabel: String,
    iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Toggle on the FAR LEFT
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF7B6FE8),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray
                )
            )

            // Text + Icon together on the RIGHT
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontFamily = Jomhuriaregular,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        text = subLabel,
                        fontSize = 12.sp,
                        fontFamily = Jomhuriaregular,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Icon on FAR RIGHT
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFEAEFFF), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        tint = Color(0xFF7B6FE8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ── About Card ────────────────────────────────────────────────────────────────
@Composable
fun AboutCard() {
    Box(
        modifier = Modifier
            .padding(horizontal = 17.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF2E2645).copy(alpha = 0.1f),
                spotColor = Color(0xFF2E2645).copy(alpha = 0.1f)
            )
            .background(color = Color(0xFFEAEFFF), shape = RoundedCornerShape(24.dp))
            .padding(top = 20.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Title
            Text(
                text = "حول",
                fontSize = 20.sp,
                fontFamily = arimo_regular,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = DarkNavy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            AboutItem(label = "سياسة الخصوصية")
            Spacer(modifier = Modifier.height(12.dp))
            AboutItem(label = "شروط الخدمة")
            Spacer(modifier = Modifier.height(12.dp))
            AboutItem(label = "المساعدة والدعم")
            Spacer(modifier = Modifier.height(12.dp))
            AboutItem(label = "حول BabyBloom")

            Spacer(modifier = Modifier.height(16.dp))

            // Version
            Text(
                text = "BabyBloom v1.0.0",
                fontSize = 13.sp,
                fontFamily = arimo_regular,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── About Item ────────────────────────────────────────────────────────────────
@Composable
fun AboutItem(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontFamily = arimo_regular,
            color = DarkNavy,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Info Card ─────────────────────────────────────────────────────────────────
@Composable
fun InfoCard(
    label: String,
    value: String,
    iconRes: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontFamily = rakkas,
                    color = Color.Gray
                )
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontFamily = judson,
                    color = DarkNavy
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color(0xFF7B6FE8),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}