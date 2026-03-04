package parent_view

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current  // ✅ inside here

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF2E2645))
    ) {
        GreetingHeader()
        ProfileCard(
            onEditClick = {
                Toast.makeText(context, "I am edit button", Toast.LENGTH_SHORT).show()
            }
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
@Composable
fun GreetingHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color(0xFF2E2645))
    ) {
        Image(
            painter = painterResource(id = R.drawable.flower_collection_2),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .offset(y = 73.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.flower_collection_1),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(y = 73.dp)
        )

        Text(
            text = getGreeting(),
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(Font(R.font.judson_regular)),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 35.dp)
        )

        Text(
            text = "هدى",
            fontSize = 40.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(Font(R.font.rakkas)),
            fontWeight = FontWeight.Normal,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 60.dp)
        )
    }
}

@Composable
fun ProfileCard(
    modifier: Modifier = Modifier,
    onEditClick: () -> Unit
) {
    Box(
        modifier = modifier
            .width(382.dp)
            .height(395.dp)
            .offset(x = 5.dp, y = 200.dp)
            .background(
                color = Color(0xFFFFFFFF),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.edit_info_button),
            contentDescription = "Edit",
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.TopStart)
                .clickable { onEditClick() }
        )
    }
}