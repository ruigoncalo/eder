package com.ruigoncalo.eder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ruigoncalo.eder.ui.EditorScreen
import com.ruigoncalo.eder.ui.theme.EderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EderTheme {
                EditorScreen()
            }
        }
    }
}