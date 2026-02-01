package com.wildcard.fourd_at_home.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wildcard.fourd_at_home.ui.playback.PlaybackScreen
import com.wildcard.fourd_at_home.ui.settings.SettingsScreen
import com.wildcard.fourd_at_home.ui.videoselect.VideoSelectScreen

/**
 * 画面ルート定義
 */
sealed class Screen(val route: String) {
    data object VideoSelect : Screen("video_select")
    data object Settings : Screen("settings/{videoId}") {
        fun createRoute(videoId: String) = "settings/$videoId"
    }
    data object Playback : Screen("playback/{videoId}") {
        fun createRoute(videoId: String) = "playback/$videoId"
    }
}

/**
 * アプリのナビゲーション
 * 直線フロー: VideoSelect → Settings → Playback
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.VideoSelect.route,
        modifier = Modifier.fillMaxSize()
    ) {
        // 動画選択画面
        composable(Screen.VideoSelect.route) {
            VideoSelectScreen(
                onVideoSelected = { content ->
                    navController.navigate(Screen.Settings.createRoute(content.id))
                }
            )
        }

        // セッティング画面（BLE接続）
        composable(
            route = Screen.Settings.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            SettingsScreen(
                videoId = videoId,
                onNavigateToPlayback = {
                    navController.navigate(Screen.Playback.createRoute(videoId)) {
                        // Settingsは戻れるように残す
                    }
                }
            )
        }

        // 再生画面
        composable(
            route = Screen.Playback.route,
            arguments = listOf(
                navArgument("videoId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            PlaybackScreen(
                videoId = videoId,
                onNavigateToVideoSelect = {
                    navController.navigate(Screen.VideoSelect.route) {
                        popUpTo(Screen.VideoSelect.route) { inclusive = false }
                    }
                }
            )
        }
    }
}
