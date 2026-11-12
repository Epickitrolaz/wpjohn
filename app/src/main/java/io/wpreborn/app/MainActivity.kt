package io.wpreborn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.wpreborn.app.data.AppContextHolder
import io.wpreborn.app.ui.AppRoot
import io.wpreborn.app.ui.WpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        enableEdgeToEdge()
        setContent {
            WpTheme(dark = true) {
                val vm: WorldViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}
