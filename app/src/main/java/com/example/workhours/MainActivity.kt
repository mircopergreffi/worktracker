package com.example.workhours

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.ui.AppTheme
import com.example.workhours.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark by vm.dark.collectAsStateWithLifecycle()
            val isDark = dark ?: isSystemInDarkTheme()
            AppTheme(isDark) { AppRoot(vm, isDark) }
        }
    }
}
