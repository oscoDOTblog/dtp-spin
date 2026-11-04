package org.mixxx.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private val vm: DjViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.ensureEngine()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DjScreen(vm)
            }
        }
    }
}
