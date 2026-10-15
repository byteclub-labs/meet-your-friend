package com.byteclub.meetyourfriend

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import com.byteclub.meetyourfriend.ui.MeetScreen
import com.byteclub.meetyourfriend.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF175B52),
                    secondary = Color(0xFFB65B3C),
                    background = Color(0xFFF5F4EF),
                    surface = Color(0xFFFFFEFA),
                ),
            ) {
                val viewModel: MainViewModel = hiltViewModel()
                MeetScreen(
                    viewModel = viewModel,
                    sharedText = sharedText,
                    onSharedHandled = { sharedText = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
    }
}
