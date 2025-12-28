package com.example.hovercraftcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.hovercraftcontroller.ui.theme.HovercraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HovercraftTheme {
                AppRoot()
            }
        }
    }
}
