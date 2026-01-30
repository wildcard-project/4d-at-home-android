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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
                onFanChange = { viewModel.setFanIntensity(it) },
                onWaterChange = { viewModel.setWaterIntensity(it) },
                onMistChange = { viewModel.setMistIntensity(it) },
                onLedColorChange = { r, g, b -> viewModel.setLedColor(r, g, b) },
                onLedBrightnessChange = { viewModel.setLedBrightness(it) },
                onPresetSelect = { viewModel.setPresetColor(it) },
                onStopEffectStation = { viewModel.stopEffectStation() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ActionDrive制御
            ActionDriveCard(
                isMotor1Connected = uiState.isMotor1Connected,
                isMotor2Connected = uiState.isMotor2Connected,
                motor1Intensity = uiState.effectState.motor1Intensity,
                motor2Intensity = uiState.effectState.motor2Intensity,
                onMotor1Change = { viewModel.setMotor1Intensity(it) },
                onMotor2Change = { viewModel.setMotor2Intensity(it) },
                onBothMotorsChange = { viewModel.setBothMotorsIntensity(it) },
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
        ConnectionChip(
            label = "EffectStation",
            isConnected = isEffectStationConnected
        )
        ConnectionChip(
            label = "Motor1",
            isConnected = isMotor1Connected
        )
        ConnectionChip(
            label = "Motor2",
            isConnected = isMotor2Connected
        )
    }
}

@Composable
private fun ConnectionChip(
    label: String,
    isConnected: Boolean
) {
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
            color = if (isConnected) StatusConnected
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EffectStationCard(
    isConnected: Boolean,
    effectState: EffectState,
    onFanChange: (Int) -> Unit,
    onWaterChange: (Int) -> Unit,
    onMistChange: (Int) -> Unit,
    onLedColorChange: (Int, Int, Int) -> Unit,
    onLedBrightnessChange: (Int) -> Unit,
    onPresetSelect: (LedPreset) -> Unit,
    onStopEffectStation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                TextButton(
                    onClick = onStopEffectStation,
                    enabled = isConnected
                ) {
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

                // ファン
                EffectSlider(
                    icon = Icons.Default.Air,
                    label = "ファン",
                    value = effectState.fanIntensity,
                    onValueChange = onFanChange,
                    color = Color(0xFF42A5F5)
                )

                // 水噴射
                EffectSlider(
                    icon = Icons.Default.WaterDrop,
                    label = "水噴射",
                    value = effectState.waterIntensity,
                    onValueChange = onWaterChange,
                    color = Color(0xFF29B6F6)
                )

                // ミスト
                EffectSlider(
                    icon = Icons.Default.Cloud,
                    label = "ミスト",
                    value = effectState.mistIntensity,
                    onValueChange = onMistChange,
                    color = Color(0xFF78909C)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // LED制御
                LedControl(
                    r = effectState.ledR,
                    g = effectState.ledG,
                    b = effectState.ledB,
                    brightness = effectState.ledBrightness,
                    onColorChange = onLedColorChange,
                    onBrightnessChange = onLedBrightnessChange,
                    onPresetSelect = onPresetSelect
                )
            }
        }
    }
}

@Composable
private fun EffectSlider(
    icon: ImageVector,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(value * 100 / 255)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedControl(
    r: Int,
    g: Int,
    b: Int,
    brightness: Int,
    onColorChange: (Int, Int, Int) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onPresetSelect: (LedPreset) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Color(0xFFFFEB3B),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LED",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            // 現在の色プレビュー
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(r, g, b))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // プリセットカラー
        Text(
            text = "プリセット",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedPreset.entries.forEach { preset ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(preset.r, preset.g, preset.b))
                        .border(
                            width = if (r == preset.r && g == preset.g && b == preset.b) 3.dp else 1.dp,
                            color = if (r == preset.r && g == preset.g && b == preset.b)
                                NeonRed
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onPresetSelect(preset) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 明るさスライダー
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "明るさ",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(60.dp)
            )
            Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightnessChange(it.toInt()) },
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFEB3B),
                    activeTrackColor = Color(0xFFFFEB3B)
                )
            )
            Text(
                text = "${(brightness * 100 / 255)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

@Composable
private fun ActionDriveCard(
    isMotor1Connected: Boolean,
    isMotor2Connected: Boolean,
    motor1Intensity: Int,
    motor2Intensity: Int,
    onMotor1Change: (Int) -> Unit,
    onMotor2Change: (Int) -> Unit,
    onBothMotorsChange: (Int) -> Unit,
    onStopMotors: () -> Unit
) {
    val anyMotorConnected = isMotor1Connected || isMotor2Connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                TextButton(
                    onClick = onStopMotors,
                    enabled = anyMotorConnected
                ) {
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
                    EffectSlider(
                        icon = Icons.Default.Vibration,
                        label = "Motor 1",
                        value = motor1Intensity,
                        onValueChange = onMotor1Change,
                        color = Color(0xFF2196F3)
                    )
                }

                // Motor2
                if (isMotor2Connected) {
                    EffectSlider(
                        icon = Icons.Default.Vibration,
                        label = "Motor 2",
                        value = motor2Intensity,
                        onValueChange = onMotor2Change,
                        color = Color(0xFFFF9800)
                    )
                }

                // 両方接続されている場合、同時制御
                if (isMotor1Connected && isMotor2Connected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onBothMotorsChange(128) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonRed
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("両モーター 50%")
                    }
                }
            }
        }
    }
}
