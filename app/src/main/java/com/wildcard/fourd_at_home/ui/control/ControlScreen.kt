package com.wildcard.fourd_at_home.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import com.wildcard.fourd_at_home.ui.theme.StatusDisconnected

@Composable
fun ControlScreen(
    viewModel: ControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "エフェクト制御",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // 緊急停止ボタン
                Button(
                    onClick = { viewModel.stopAllEffects() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusDisconnected
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("全停止")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 接続状態表示
            ConnectionStatusRow(
                isEffectStationConnected = uiState.isEffectStationConnected,
                isMotor1Connected = uiState.isMotor1Connected,
                isMotor2Connected = uiState.isMotor2Connected
            )

            Spacer(modifier = Modifier.height(16.dp))

            // EffectStation制御
            EffectStationCard(
                isConnected = uiState.isEffectStationConnected,
                effectState = uiState.effectState,
                onFanToggle = { viewModel.toggleFan() },
                onSplash = { viewModel.triggerSplash() },
                onMistMode = { viewModel.setMistMode(it) },
                onLedColor = { viewModel.setLedColor(it) },
                onLedBrightness = { viewModel.setLedBrightness(it) },
                onLedEffect = { viewModel.setLedEffect(it) },
                onLedTransition = { viewModel.setLedTransition(it) },
                onLedOff = { viewModel.ledOff() },
                onStopEffectStation = { viewModel.stopEffectStation() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ActionDrive制御
            ActionDriveCard(
                isMotor1Connected = uiState.isMotor1Connected,
                isMotor2Connected = uiState.isMotor2Connected,
                motor1Level = uiState.effectState.motor1Level,
                motor2Level = uiState.effectState.motor2Level,
                onMotor1Level = { viewModel.setMotor1Level(it) },
                onMotor2Level = { viewModel.setMotor2Level(it) },
                onBothMotorsLevel = { viewModel.setBothMotorsLevel(it) },
                onStopMotors = { viewModel.stopMotors() }
            )
        }

        // エラー表示
        uiState.lastError?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(
    isEffectStationConnected: Boolean,
    isMotor1Connected: Boolean,
    isMotor2Connected: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionChip(label = "EffectStation", isConnected = isEffectStationConnected)
        ConnectionChip(label = "Motor1", isConnected = isMotor1Connected)
        ConnectionChip(label = "Motor2", isConnected = isMotor2Connected)
    }
}

@Composable
private fun ConnectionChip(label: String, isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = if (isConnected) StatusConnected.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isConnected) StatusConnected else StatusDisconnected)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isConnected) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffectStationCard(
    isConnected: Boolean,
    effectState: EffectState,
    onFanToggle: () -> Unit,
    onSplash: () -> Unit,
    onMistMode: (MistMode) -> Unit,
    onLedColor: (LedColorPreset) -> Unit,
    onLedBrightness: (LedBrightnessLevel) -> Unit,
    onLedEffect: (LedEffectMode) -> Unit,
    onLedTransition: (LedTransitionMode) -> Unit,
    onLedOff: () -> Unit,
    onStopEffectStation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EffectStation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onStopEffectStation, enabled = isConnected) {
                    Text("停止", color = StatusDisconnected)
                }
            }

            if (!isConnected) {
                Text(
                    text = "デバイスが接続されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // === ファン ===
                EffectSectionHeader(icon = Icons.Default.Air, label = "ファン", color = Color(0xFF42A5F5))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = if (effectState.fanOn) "ON" else "OFF",
                        isSelected = effectState.fanOn,
                        onClick = onFanToggle,
                        selectedColor = Color(0xFF42A5F5)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === 水噴射 ===
                EffectSectionHeader(icon = Icons.Default.WaterDrop, label = "水噴射", color = Color(0xFF29B6F6))
                Button(
                    onClick = onSplash,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6))
                ) {
                    Text("SPLASH!")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === ミスト ===
                EffectSectionHeader(icon = Icons.Default.Cloud, label = "ミスト", color = Color(0xFF78909C))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MistMode.entries.forEach { mode ->
                        SelectableButton(
                            text = mode.displayName,
                            isSelected = effectState.mistMode == mode,
                            onClick = { onMistMode(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === LED ===
                EffectSectionHeader(icon = Icons.Default.Lightbulb, label = "LED", color = Color(0xFFFFEB3B))
                
                // 色選択
                Text(
                    text = "色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LedColorPreset.entries.filter { it != LedColorPreset.OFF }.forEach { color ->
                        ColorButton(
                            color = color,
                            isSelected = effectState.ledColor == color,
                            onClick = { onLedColor(color) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 明るさ
                Text(
                    text = "明るさ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedBrightnessLevel.entries.forEach { level ->
                        SelectableButton(
                            text = level.displayName,
                            isSelected = effectState.ledBrightness == level,
                            onClick = { onLedBrightness(level) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // エフェクト
                Text(
                    text = "エフェクト",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedEffectMode.entries.forEach { effect ->
                        SelectableButton(
                            text = effect.displayName,
                            isSelected = effectState.ledEffect == effect,
                            onClick = { onLedEffect(effect) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // トランジション
                Text(
                    text = "切り替え",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedTransitionMode.entries.forEach { transition ->
                        SelectableButton(
                            text = transition.displayName,
                            isSelected = effectState.ledTransition == transition,
                            onClick = { onLedTransition(transition) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // LED消灯ボタン
                OutlinedButton(
                    onClick = onLedOff,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("LED消灯")
                }
            }
        }
    }
}

@Composable
private fun EffectSectionHeader(icon: ImageVector, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = NeonRed
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text)
    }
}

@Composable
private fun SelectableButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NeonRed,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun ColorButton(
    color: LedColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when (color) {
        LedColorPreset.PINK -> Color(0xFFFF69B4)
        LedColorPreset.RED -> Color(0xFFFF0000)
        LedColorPreset.ORANGE -> Color(0xFFFF6400)
        LedColorPreset.YELLOW -> Color(0xFFFFFF00)
        LedColorPreset.YELLOW_GREEN -> Color(0xFF96FF00)
        LedColorPreset.GREEN -> Color(0xFF00FF00)
        LedColorPreset.DARK_GREEN -> Color(0xFF006400)
        LedColorPreset.CYAN -> Color(0xFF00FFFF)
        LedColorPreset.BLUE -> Color(0xFF0000FF)
        LedColorPreset.PURPLE -> Color(0xFF9600FF)
        LedColorPreset.WHITE -> Color(0xFFFFFFFF)
        LedColorPreset.OFF -> Color(0xFF333333)
    }
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) NeonRed else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (color == LedColorPreset.WHITE || color == LedColorPreset.YELLOW) {
            // 明るい色には暗いテキスト
            Text(
                text = color.displayName.take(1),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionDriveCard(
    isMotor1Connected: Boolean,
    isMotor2Connected: Boolean,
    motor1Level: VibrationLevel,
    motor2Level: VibrationLevel,
    onMotor1Level: (VibrationLevel) -> Unit,
    onMotor2Level: (VibrationLevel) -> Unit,
    onBothMotorsLevel: (VibrationLevel) -> Unit,
    onStopMotors: () -> Unit
) {
    val anyMotorConnected = isMotor1Connected || isMotor2Connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ActionDrive (振動)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onStopMotors, enabled = anyMotorConnected) {
                    Text("停止", color = StatusDisconnected)
                }
            }

            if (!anyMotorConnected) {
                Text(
                    text = "モーターが接続されていません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // Motor1
                if (isMotor1Connected) {
                    EffectSectionHeader(
                        icon = Icons.Default.Vibration,
                        label = "Motor 1 (背中)",
                        color = Color(0xFF2196F3)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VibrationLevel.entries.forEach { level ->
                            SelectableButton(
                                text = level.displayName,
                                isSelected = motor1Level == level,
                                onClick = { onMotor1Level(level) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Motor2
                if (isMotor2Connected) {
                    EffectSectionHeader(
                        icon = Icons.Default.Vibration,
                        label = "Motor 2 (お尻)",
                        color = Color(0xFFFF9800)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VibrationLevel.entries.forEach { level ->
                            SelectableButton(
                                text = level.displayName,
                                isSelected = motor2Level == level,
                                onClick = { onMotor2Level(level) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 両モーター同時制御
                if (isMotor1Connected && isMotor2Connected) {
                    Text(
                        text = "両モーター同時",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VibrationLevel.entries.forEach { level ->
                            OutlinedButton(
                                onClick = { onBothMotorsLevel(level) }
                            ) {
                                Text(level.displayName)
                            }
                        }
                    }
                }
            }
        }
    }
}
