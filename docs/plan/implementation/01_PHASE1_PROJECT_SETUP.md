# Phase 1: プロジェクトセットアップ

**期間目安**: 1日  
**前提条件**: Android Studio インストール済み、PlatformIO インストール済み

---

## 📋 タスク一覧

| # | タスク | 優先度 | 完了条件 |
|:-:|:-------|:------:|:---------|
| 1.1 | Gradle依存関係の設定 | 必須 | ビルド成功 |
| 1.2 | Hilt DIセットアップ | 必須 | `@HiltAndroidApp`動作 |
| 1.3 | Material 3テーマ設定 | 必須 | ダークテーマ適用 |
| 1.4 | Navigation Rail実装 | 必須 | 3画面遷移可能 |
| 1.5 | 横画面固定設定 | 必須 | Landscape強制 |
| 1.6 | ESP32 PlatformIOプロジェクト作成 | 必須 | 3プロジェクト作成 |

---

## 1.1 Gradle依存関係の設定

### 1.1.1 gradle/libs.versions.toml

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"

# Navigation
navigationCompose = "2.8.5"

# Hilt
hilt = "2.53.1"
hiltNavigationCompose = "1.2.0"

# Media3 (ExoPlayer)
media3 = "1.5.1"

# Coroutines
coroutines = "1.9.0"

# DataStore
datastorePreferences = "1.1.2"

# Serialization
kotlinxSerializationJson = "1.7.3"

[libraries]
# Core
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Media3
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }

# Coroutines
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# DataStore
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version = "1.2.1" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version = "3.6.1" }
androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.28" }
```

### 1.1.2 build.gradle.kts (Project)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

### 1.1.3 app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wildcard.fourd_at_home"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wildcard.fourd_at_home"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

---

## 1.2 Hilt DIセットアップ

### 1.2.1 FourdAtHomeApplication.kt

```kotlin
package com.wildcard.fourd_at_home

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FourdAtHomeApplication : Application()
```

### 1.2.2 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- BLE権限 -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- BLE機能を必須とする -->
    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <application
        android:name=".FourdAtHomeApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.FourdAtHome"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:theme="@style/Theme.FourdAtHome">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## 1.3 Material 3テーマ設定

### 1.3.1 ui/theme/Color.kt

```kotlin
package com.wildcard.fourd_at_home.ui.theme

import androidx.compose.ui.graphics.Color

// === 基本カラー ===
val DeepBlack = Color(0xFF050505)      // 背景色 (Main)
val DarkGrey = Color(0xFF1E1E1E)       // 背景色 (Surface)
val NeonRed = Color(0xFFFF0033)        // ブランドカラー
val PureWhite = Color(0xFFFFFFFF)      // テキスト (Main)
val LightGrey = Color(0xFFB3B3B3)      // テキスト (Sub)

// === エフェクトカラー ===
val EffectOrange = Color(0xFFD97706)   // 衝撃・振動
val EffectCyan = Color(0xFF06B6D4)     // 水
val EffectGreen = Color(0xFF10B981)    // 風
val EffectWhite = Color(0xFFF3F4F6)    // 光

// === 状態カラー ===
val StatusConnected = Color(0xFF22C55E)     // 接続済み
val StatusDisconnected = Color(0xFFEF4444)  // 未接続
val StatusConnecting = Color(0xFFFACC15)    // 接続中

// === Material 3 Dark Scheme ===
val md_theme_dark_primary = NeonRed
val md_theme_dark_onPrimary = PureWhite
val md_theme_dark_primaryContainer = Color(0xFF5C0011)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFDAD9)

val md_theme_dark_secondary = Color(0xFFE6BDBC)
val md_theme_dark_onSecondary = Color(0xFF44292A)
val md_theme_dark_secondaryContainer = Color(0xFF5D3F40)
val md_theme_dark_onSecondaryContainer = Color(0xFFFFDAD9)

val md_theme_dark_tertiary = Color(0xFFE5C18D)
val md_theme_dark_onTertiary = Color(0xFF422C05)
val md_theme_dark_tertiaryContainer = Color(0xFF5B4219)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFDDB1)

val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_theme_dark_background = DeepBlack
val md_theme_dark_onBackground = PureWhite
val md_theme_dark_surface = DarkGrey
val md_theme_dark_onSurface = PureWhite
val md_theme_dark_surfaceVariant = Color(0xFF524344)
val md_theme_dark_onSurfaceVariant = LightGrey

val md_theme_dark_outline = Color(0xFF9F8C8C)
val md_theme_dark_outlineVariant = Color(0xFF524344)
val md_theme_dark_inverseSurface = PureWhite
val md_theme_dark_inverseOnSurface = Color(0xFF362F2F)
val md_theme_dark_inversePrimary = Color(0xFFBA1A2F)
```

### 1.3.2 ui/theme/Type.kt

```kotlin
package com.wildcard.fourd_at_home.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### 1.3.3 ui/theme/Theme.kt

```kotlin
package com.wildcard.fourd_at_home.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary
)

@Composable
fun FourdAtHomeTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## 1.4 Navigation Rail実装

### 1.4.1 ui/navigation/AppNavigation.kt

```kotlin
package com.wildcard.fourd_at_home.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wildcard.fourd_at_home.ui.control.ControlScreen
import com.wildcard.fourd_at_home.ui.playback.PlaybackScreen
import com.wildcard.fourd_at_home.ui.settings.SettingsScreen

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Playback : Screen(
        route = "playback",
        title = "再生",
        selectedIcon = Icons.Filled.PlayArrow,
        unselectedIcon = Icons.Outlined.PlayArrow
    )
    
    data object Control : Screen(
        route = "control",
        title = "制御",
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    )
    
    data object Settings : Screen(
        route = "settings",
        title = "設定",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

val screens = listOf(
    Screen.Playback,
    Screen.Control,
    Screen.Settings
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    Scaffold { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavigationRail(navController = navController)
            
            NavHost(
                navController = navController,
                startDestination = Screen.Playback.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(Screen.Playback.route) {
                    PlaybackScreen()
                }
                composable(Screen.Control.route) {
                    ControlScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun AppNavigationRail(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    NavigationRail {
        screens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                        contentDescription = screen.title
                    )
                },
                label = { Text(screen.title) }
            )
        }
    }
}
```

### 1.4.2 MainActivity.kt

```kotlin
package com.wildcard.fourd_at_home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.wildcard.fourd_at_home.ui.navigation.AppNavigation
import com.wildcard.fourd_at_home.ui.theme.FourdAtHomeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            FourdAtHomeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
```

### 1.4.3 プレースホルダー画面

**ui/playback/PlaybackScreen.kt**
```kotlin
package com.wildcard.fourd_at_home.ui.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaybackScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("再生画面 (Phase 5で実装)")
    }
}
```

**ui/control/ControlScreen.kt**
```kotlin
package com.wildcard.fourd_at_home.ui.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ControlScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("制御画面 (Phase 3-4で実装)")
    }
}
```

**ui/settings/SettingsScreen.kt**
```kotlin
package com.wildcard.fourd_at_home.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("設定画面 (Phase 2で実装)")
    }
}
```

---

## 1.5 横画面固定設定

AndroidManifest.xmlの`<activity>`タグで設定済み:

```xml
android:screenOrientation="sensorLandscape"
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
```

---

## 1.6 ESP32 PlatformIOプロジェクト作成

### 1.6.1 ディレクトリ構造

```
docs/plan/esp32_firmware/
├── effect_station/
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
├── action_drive_motor1/
│   ├── platformio.ini
│   └── src/
│       └── main.cpp
└── action_drive_motor2/
    ├── platformio.ini
    └── src/
        └── main.cpp
```

### 1.6.2 共通 platformio.ini テンプレート

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino

; シリアルモニタ設定
monitor_speed = 115200

; ビルドフラグ
build_flags = 
    -D LED_BUILTIN=2

; アップロード設定
upload_speed = 921600

; ライブラリ依存
lib_deps =
    ; BLE用 (ESP32組み込み)
```

### 1.6.3 EffectStation用追加設定

```ini
; effect_station/platformio.ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino

monitor_speed = 115200
upload_speed = 921600

build_flags = 
    -D LED_BUILTIN=2
    -D DEVICE_TYPE_EFFECT_STATION

lib_deps =
    adafruit/Adafruit NeoPixel@^1.12.0
```

---

## ✅ Phase 1 完了チェックリスト

- [ ] Gradleビルドが成功する
- [ ] アプリが起動する
- [ ] ダークテーマが適用されている
- [ ] Navigation Railで3画面に遷移できる
- [ ] 横画面で固定されている
- [ ] ESP32用PlatformIOプロジェクトが作成されている

---

## 📝 次のPhase

[Phase 2: BLE通信レイヤー](./02_PHASE2_BLE_LAYER.md) へ進む
