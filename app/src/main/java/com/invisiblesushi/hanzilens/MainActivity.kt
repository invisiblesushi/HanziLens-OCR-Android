package com.invisiblesushi.hanzilens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.invisiblesushi.hanzilens.ui.navigation.AppNavigation
import com.invisiblesushi.hanzilens.ui.theme.HanziLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HanziLensTheme {
                AppNavigation()
            }
        }
    }
}
