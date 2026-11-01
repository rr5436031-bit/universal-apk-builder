package com.universalapk.builder.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.universalapk.builder.ui.BuildScreen
import com.universalapk.builder.ui.ErrorScreen
import com.universalapk.builder.ui.HistoryScreen
import com.universalapk.builder.ui.HomeScreen
import com.universalapk.builder.ui.LogScreen
import com.universalapk.builder.ui.MainViewModel
import com.universalapk.builder.ui.SettingsScreen
import com.universalapk.builder.ui.UabTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var openRecord by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        openRecord = intent?.getStringExtra("record")
        setContent {
            UabTheme {
                val nav = rememberNavController()
                LaunchedEffect(openRecord) {
                    openRecord?.let { nav.navigate("build/$it"); openRecord = null }
                }
                NavHost(
                    navController = nav, startDestination = "home",
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 10 } }
                ) {
                    composable("home") { HomeScreen(vm, nav) }
                    composable("settings") { SettingsScreen(vm, nav) }
                    composable("history") { HistoryScreen(nav) }
                    composable("build/{id}") { BuildScreen(it.arguments!!.getString("id")!!, vm, nav) }
                    composable("error/{id}") { ErrorScreen(it.arguments!!.getString("id")!!, nav) }
                    composable("log/{id}") { LogScreen(it.arguments!!.getString("id")!!, nav) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRecord = intent.getStringExtra("record")
    }
}
