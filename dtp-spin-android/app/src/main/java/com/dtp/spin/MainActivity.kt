package com.dtp.spin

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dtp.spin.ui.DJScreen
import com.dtp.spin.ui.DJTheme
import com.dtp.spin.ui.DJViewModel
import com.dtp.spin.ui.DJViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: DJViewModel by viewModels { DJViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Practice sessions run long with the screen on: keep it awake while mixing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DJTheme {
                DJScreen(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Positions + settings persist on pause so a killed process restores cleanly.
        // (Debounced save already covers play/pause/seek; nothing extra to do here.)
    }
}
