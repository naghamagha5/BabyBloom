package com.babybloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.babybloom.ui.theme.BabyBloomTheme
import com.babybloom.presentation.screens.MyChildrenContent
import dagger.hilt.android.AndroidEntryPoint
import com.babybloom.di.SessionManager
import com.babybloom.navigation.BabyBloomNavGraph
import javax.inject.Inject

/*@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                false
            }
        }
        //enableEdgeToEdge()
        setContent {
            BabyBloomTheme {
                MyChildrenContent()
            }
        }
    }
}*/

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                false
            }
        }
        //enableEdgeToEdge()
        setContent {
            BabyBloomTheme {
                BabyBloomNavGraph(sessionManager = sessionManager)
            }
        }
    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BabyBloomTheme {
        Greeting("Android")
    }
}