package parent_view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babybloom.R
import java.util.Calendar

@Composable
fun ParentView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2E2645))
    ) {
        Image(
            painter = painterResource(id = R.drawable.flower_collection_2),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .offset(y = 73.dp)  // move down 5dp
        )

        Image(
            painter = painterResource(id = R.drawable.flower_collection_1),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(y = 73.dp)  // move down 5dp
        )
        Text(
            text = getGreeting(),
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = 35.dp)
        )
        Text(
            text = "هدى", // placeholder for now
            fontSize = 40.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(Font(R.font.rakkas)),
            fontWeight = FontWeight.Normal,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter)
                .offset(y = 60.dp) // adjust to sit below greeting
        )
    }

}


fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "صباح الخير"
        hour < 17 -> "مساء الخير"
        else -> "مساء النور"
    }
}
