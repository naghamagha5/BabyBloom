package com.babybloom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.babybloom.data.local.seeder.DatabaseSeeder
import com.babybloom.data.local.seeder.LearningContentSeeder
import com.babybloom.navigation.BabyBloomNavGraph
import com.babybloom.ui.theme.BabyBloomTheme
import com.babybloom.presentation.screens.MyChildrenContent
import dagger.hilt.android.AndroidEntryPoint
import com.babybloom.di.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.babybloom.navigation.BabyBloomNavGraph
import javax.inject.Inject





@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var seeder: DatabaseSeeder
    @Inject lateinit var learningContentSeeder: LearningContentSeeder
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                false
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                seeder.seedIfEmpty()
                learningContentSeeder.seedIfEmpty()
                android.util.Log.d("SEEDER", "Seeding completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("SEEDER", "Seeding failed: ${e.message}", e)
            }
        }
        enableEdgeToEdge()
        setContent {
            BabyBloomTheme {
                BabyBloomNavGraph(sessionManager = sessionManager)
            }
        }
    }
}
