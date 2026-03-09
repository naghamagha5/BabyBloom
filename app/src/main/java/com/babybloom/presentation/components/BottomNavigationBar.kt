package com.babybloom.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babybloom.ui.theme.*

data class BottomNavItem(
    val label: String,
    val iconRes: Int,
    val route: String
)

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomNavItem> = defaultNavItems()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(White)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isActive = currentRoute == item.route

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isActive) NavyDark else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .padding(vertical = 8.dp)
                    .clickable { onNavigate(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) NavyDark else androidx.compose.ui.graphics.Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(
                            if (isActive) White else HeaderGreetingColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.label,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) White else HeaderGreetingColor
                    )
                )
            }
        }
    }
}

@Composable
private fun defaultNavItems(): List<BottomNavItem> {
    return listOf(
        BottomNavItem(
            label = stringResource(com.babybloom.R.string.nav_home),
            iconRes = com.babybloom.R.drawable.ic_nav_home,
            route = "home"
        ),
        BottomNavItem(
            label = stringResource(com.babybloom.R.string.nav_children),
            iconRes = com.babybloom.R.drawable.ic_nav_children,
            route = "children"
        ),
        BottomNavItem(
            label = stringResource(com.babybloom.R.string.nav_settings),
            iconRes = com.babybloom.R.drawable.ic_nav_settings,
            route = "settings"
        )
    )
}