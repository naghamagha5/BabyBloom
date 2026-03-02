package com.babybloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.babybloom.ui.theme.BabyBloomTheme
import dagger.hilt.android.AndroidEntryPoint
import parent_view.ParentView

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                false
            }
        }
        enableEdgeToEdge()
        setContent {
            BabyBloomTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ParentView(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}