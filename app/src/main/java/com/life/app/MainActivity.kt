package com.life.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.life.app.data.ThemeOptions
import com.life.app.needle.CaptureRuntime
import com.life.app.ui.LifeViewModel
import com.life.app.ui.SettingsScreen
import com.life.app.ui.TimelineScreen
import com.life.app.ui.theme.LifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureRuntime.initContext(this)
        enableEdgeToEdge()
        setContent {
            val vm: LifeViewModel = viewModel(factory = LifeViewModel.factory(applicationContext))
            val data by vm.appData.collectAsStateWithLifecycle()
            val bg = ThemeOptions.background(data.backgroundMode)
            SideEffect {
                val insets = WindowCompat.getInsetsController(window, window.decorView)
                insets.isAppearanceLightStatusBars = bg.isLight
                insets.isAppearanceLightNavigationBars = bg.isLight
            }

            var settings by remember { mutableStateOf(false) }

            LifeTheme(
                backgroundId = data.backgroundMode,
                accentId = data.colorScheme
            ) {
                Surface(Modifier.fillMaxSize()) {
                    if (settings) {
                        SettingsScreen(data = data, vm = vm, onBack = { settings = false })
                    } else {
                        TimelineScreen(
                            data = data,
                            vm = vm,
                            onOpenSettings = { settings = true }
                        )
                    }
                }
            }
        }
    }
}
