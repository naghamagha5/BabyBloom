package com.babybloom.presentation.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.babybloom.R
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.presentation.viewmodels.MyChildrenViewModel
import com.babybloom.ui.theme.*

// ─────────────────────────────────────────────
//  Color aliases (all from Color.kt)
// ─────────────────────────────────────────────
private val colorBackground    = ScreenBackground
private val colorCard          = ChildCardBackground
private val colorButton        = AddChildButton
private val colorButtonText    = MyChildrenTextDark
private val colorSearchIcon    = SearchBarIcon
private val colorProgressFill  = ProgressBarFill
private val colorProgressTrack = ProgressBarTrack
private val colorHeaderCard    = ChildCardBackground
val colorAvatarBorder = Brush.linearGradient(
    colors = listOf(Gradient3, Gradient2, Gradient1)
)
private val colorChildName     = ChildName

// ─────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────
enum class ChildStatus { ACTIVE, CALM, NEEDS_SUPPORT }

data class ChildUiModel(
    val id: Long = 0,
    val name: String,
    val ageYears: Int,
    val progressPercent: Int,
    val status: ChildStatus,
    val avatarRes: Int
)

// ─────────────────────────────────────────────
//  Fragment
// ─────────────────────────────────────────────
class MyChildrenScreen : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MyChildrenContent()
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MyChildrenScreenPreview() {
    MyChildrenContent()
}

// ─────────────────────────────────────────────
//  Root composable
// ─────────────────────────────────────────────
@Composable
fun MyChildrenContent(
    onAddChildClick: () -> Unit = {},
    viewModel: MyChildrenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val children = uiState.children

    var searchQuery by remember { mutableStateOf("") }

    val filteredChildren = remember(searchQuery, children) {
        if (searchQuery.isBlank()) children
        else children.filter { it.name.contains(searchQuery) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.End
        ) {
            HeaderCard(query = searchQuery, onQueryChange = { searchQuery = it })
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Spacer(Modifier.height(16.dp))
                AddChildButton(onClick = onAddChildClick)
                Spacer(Modifier.height(16.dp))
                ChildrenGrid(children = filteredChildren)
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Header card — title + search bar
// ─────────────────────────────────────────────
@Composable
fun HeaderCard(query: String, onQueryChange: (String) -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
            )
            .background(
                color = colorHeaderCard,
                shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
            ),
        horizontalAlignment = Alignment.End
    ) {
        // ── Leaf chain — no padding, full width ──
        Image(
            painter = painterResource(id = R.drawable.ic_leaf_horizontal),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            contentScale = ContentScale.Crop
        )

        // ── Rest of content with padding ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = stringResource(R.string.my_children_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colorButtonText,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.height(10.dp))
            ChildSearchBar(query = query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────
//  Search bar
// ─────────────────────────────────────────────
@Composable
fun ChildSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, colorProgressTrack, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_children_hint),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(textDirection = TextDirection.Rtl)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = TextOnLight,
                        textAlign = TextAlign.Right,
                        textDirection = TextDirection.Rtl
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_search),
                contentDescription = stringResource(R.string.search_icon_desc),
                tint = colorSearchIcon,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Add child button
// ─────────────────────────────────────────────
@Composable
fun AddChildButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .wrapContentWidth()
                .height(52.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.action_add_child),
                color = ChildCardBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.btn_add_child_icon),
                color = ChildCardBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))

        }
    }
}

// ─────────────────────────────────────────────
//  2-column grid
// ─────────────────────────────────────────────
@Composable
fun ChildrenGrid(children: List<ChildUiModel>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(children) { child ->
            ChildCard(child = child)
        }
    }
}

// ─────────────────────────────────────────────
//  Single child card
// ─────────────────────────────────────────────
@Composable
fun ChildCard(child: ChildUiModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            ChildAvatarAndName(child = child)
            Spacer(Modifier.height(8.dp))
            ChildStatusBadge(status = child.status)
            Spacer(Modifier.height(10.dp))
            ChildProgressBar(progressPercent = child.progressPercent)
        }
    }
}

// ─────────────────────────────────────────────
//  Avatar + Name
// ─────────────────────────────────────────────
@Composable
fun ChildAvatarAndName(child: ChildUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name + age
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = child.name,
                color = colorChildName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
            Text(
                text = "${child.ageYears} ${stringResource(R.string.label_years)}",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Right,
                style = TextStyle(textDirection = TextDirection.Rtl)
            )
        }

        Spacer(Modifier.width(8.dp))

        // Avatar
        Box(
            modifier = Modifier.size(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    brush = colorAvatarBorder,
                    startAngle = -140f,
                    sweepAngle = 240f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Image(
                painter = painterResource(id = child.avatarRes),
                contentDescription = child.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Progress
// ─────────────────────────────────────────────
@Composable
fun ChildProgressBar(progressPercent: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${progressPercent}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextOnLight
        )
        Text(
            text = stringResource(R.string.label_overall_progress),
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = TextAlign.Right,
            style = TextStyle(textDirection = TextDirection.Rtl)
        )
    }

    Spacer(Modifier.height(4.dp))

    LinearProgressIndicator(
        progress = { progressPercent / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = colorProgressFill,
        trackColor = colorProgressTrack
    )
}

// ─────────────────────────────────────────────
//  Status
// ─────────────────────────────────────────────
@Composable
fun ChildStatusBadge(status: ChildStatus) {
    val bgColor = when (status) {
        ChildStatus.ACTIVE        -> StatusActiveBackground
        ChildStatus.CALM          -> StatusCalmBackground
        ChildStatus.NEEDS_SUPPORT -> StatusNeedsSupportBackground
    }
    val dotColor = when (status) {
        ChildStatus.ACTIVE        -> StatusActiveDot
        ChildStatus.CALM          -> StatusCalmDot
        ChildStatus.NEEDS_SUPPORT -> StatusNeedsSupportDot
    }
    val labelRes = when (status) {
        ChildStatus.ACTIVE        -> R.string.status_active
        ChildStatus.CALM          -> R.string.status_calm
        ChildStatus.NEEDS_SUPPORT -> R.string.status_needs_support
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 11.sp,
                    color = dotColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

            }
        }
    }
}