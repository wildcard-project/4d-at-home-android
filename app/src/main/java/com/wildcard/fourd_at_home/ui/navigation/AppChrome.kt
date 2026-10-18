package com.wildcard.fourd_at_home.ui.navigation

import androidx.compose.runtime.mutableStateOf

/**
 * アプリ全体の枠の表示状態。
 * 再生画面が全画面になっている間は、左のナビゲーションを隠す。
 */
object AppChrome {
    val fullscreen = mutableStateOf(false)
}
