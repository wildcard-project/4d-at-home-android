package com.wildcard.fourd_at_home.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wildcard.fourd_at_home.ble.BleConnection
import com.wildcard.fourd_at_home.ble.CommandDirection
import com.wildcard.fourd_at_home.ble.CommandLogEntry
import com.wildcard.fourd_at_home.ble.CommandStatus
import com.wildcard.fourd_at_home.ble.ConnectionState
import com.wildcard.fourd_at_home.ble.DeviceType
import com.wildcard.fourd_at_home.ble.ScanState
import com.wildcard.fourd_at_home.ble.ScannedDevice
import com.wildcard.fourd_at_home.ble.SignalStrength
import com.wildcard.fourd_at_home.ui.theme.NeonRed
import com.wildcard.fourd_at_home.ui.theme.StatusConnected
import com.wildcard.fourd_at_home.ui.theme.StatusDisconnected
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 権限リクエストランチャー
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
        }
    }

    // 権限ダイアログ
    if (uiState.showPermissionDialog) {
        PermissionDialog(
            onDismiss = { viewModel.dismissPermissionDialog() },
            onRequestPermission = {
                permissionLauncher.launch(viewModel.getRequiredPermissions().toTypedArray())
            }
        )
    }

    // エラーダイアログ
    uiState.error?.let { error ->
        ErrorDialog(
            message = error.message,
            onDismiss = { viewModel.clearError() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ヘッダー
        Text(
            text = "BLE デバイス設定",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // スキャンセクション
        ScanSection(
            scanState = uiState.scanState,
            scannedDevices = uiState.scannedDevices,
            connections = uiState.connections,
            onStartScan = { viewModel.startScan() },
            onStopScan = { viewModel.stopScan() },
            onConnectDevice = { viewModel.connectDevice(it) },
            onDisconnectDevice = { viewModel.disconnectDevice(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 接続済みデバイスセクション
        ConnectedDevicesSection(
            connections = uiState.connections,
            onDisconnect = { viewModel.disconnectDevice(it) },
            onDisconnectAll = { viewModel.disconnectAll() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // コマンドログセクション
        CommandLogSection(
            commandLog = uiState.commandLog,
            onClearLog = { viewModel.clearCommandLog() }
        )
    }
}

@Composable
private fun ScanSection(
    scanState: ScanState,
    scannedDevices: List<ScannedDevice>,
    connections: Map<String, BleConnection>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (ScannedDevice) -> Unit,
    onDisconnectDevice: (String) -> Unit
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (scanState) {
                            ScanState.SCANNING -> Icons.Default.BluetoothSearching
                            else -> Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        tint = NeonRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "デバイススキャン",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                when (scanState) {
                    ScanState.SCANNING -> {
                        OutlinedButton(onClick = onStopScan) {
                            Text("停止")
                        }
                    }
                    else -> {
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonRed
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("スキャン")
                        }
                    }
                }
            }

            if (scanState == ScanState.SCANNING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonRed
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (scannedDevices.isEmpty() && scanState != ScanState.SCANNING) {
                Text(
                    text = "デバイスが見つかりません。スキャンを開始してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                scannedDevices.forEach { device ->
                    val connection = connections[device.address]
                    ScannedDeviceItem(
                        device = device,
                        connectionState = connection?.state,
                        onConnect = { onConnectDevice(device) },
                        onDisconnect = { onDisconnectDevice(device.address) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScannedDeviceItem(
    device: ScannedDevice,
    connectionState: ConnectionState?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // デバイスタイプアイコン
                DeviceTypeIndicator(deviceType = device.deviceType)

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SignalStrengthIndicator(signalStrength = device.signalStrength)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${device.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 接続/切断ボタン
            when (connectionState) {
                ConnectionState.READY -> {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("切断")
                    }
                }
                ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonRed,
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonRed
                        )
                    ) {
                        Text("接続")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceTypeIndicator(deviceType: DeviceType) {
    val (color, letter) = when (deviceType) {
        DeviceType.EFFECT_STATION -> StatusConnected to "ES"
        DeviceType.ACTION_DRIVE_1 -> Color(0xFF2196F3) to "M1"
        DeviceType.ACTION_DRIVE_2 -> Color(0xFFFF9800) to "M2"
        DeviceType.UNKNOWN -> MaterialTheme.colorScheme.outline to "?"
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SignalStrengthIndicator(signalStrength: SignalStrength) {
    val color = when (signalStrength) {
        SignalStrength.EXCELLENT -> StatusConnected
        SignalStrength.GOOD -> StatusConnected
        SignalStrength.FAIR -> Color(0xFFFF9800)
        SignalStrength.WEAK -> StatusDisconnected
    }

    Icon(
        imageVector = when (signalStrength) {
            SignalStrength.EXCELLENT, SignalStrength.GOOD -> Icons.Default.SignalCellular4Bar
            else -> Icons.Default.SignalCellularAlt
        },
        contentDescription = signalStrength.displayName,
        modifier = Modifier.size(16.dp),
        tint = color
    )
}

@Composable
private fun ConnectedDevicesSection(
    connections: Map<String, BleConnection>,
    onDisconnect: (String) -> Unit,
    onDisconnectAll: () -> Unit
) {
    val connectedDevices = connections.values.filter { 
        it.state == ConnectionState.READY 
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = StatusConnected
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "接続済み (${connectedDevices.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (connectedDevices.isNotEmpty()) {
                    TextButton(onClick = onDisconnectAll) {
                        Text("全て切断", color = StatusDisconnected)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (connectedDevices.isEmpty()) {
                Text(
                    text = "接続されているデバイスはありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                connectedDevices.forEach { connection ->
                    ConnectedDeviceItem(
                        connection = connection,
                        onDisconnect = { onDisconnect(connection.address) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceItem(
    connection: BleConnection,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            DeviceTypeIndicator(deviceType = connection.deviceType)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = connection.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = connection.deviceType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onDisconnect) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "切断",
                tint = StatusDisconnected
            )
        }
    }
}

@Composable
private fun ColumnScope.CommandLogSection(
    commandLog: List<CommandLogEntry>,
    onClearLog: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
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
                    text = "コマンドログ",
                    style = MaterialTheme.typography.titleMedium
                )

                if (commandLog.isNotEmpty()) {
                    TextButton(onClick = onClearLog) {
                        Text("クリア")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (commandLog.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ログはありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(commandLog.reversed()) { entry ->
                        CommandLogItem(entry = entry)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandLogItem(entry: CommandLogEntry) {
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 方向インジケータ
        Text(
            text = when (entry.direction) {
                CommandDirection.TX -> "→"
                CommandDirection.RX -> "←"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when (entry.direction) {
                CommandDirection.TX -> NeonRed
                CommandDirection.RX -> StatusConnected
            },
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = entry.command,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.deviceType.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ステータスインジケータ
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (entry.status) {
                        CommandStatus.SUCCESS -> StatusConnected
                        CommandStatus.PENDING -> Color(0xFFFF9800)
                        CommandStatus.FAILED -> StatusDisconnected
                        CommandStatus.TIMEOUT -> StatusDisconnected
                    }
                )
        )
    }
}

@Composable
private fun PermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("権限が必要です")
        },
        text = {
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "BLEデバイスをスキャン・接続するには、Bluetooth権限が必要です。"
                } else {
                    "BLEデバイスをスキャンするには、位置情報の権限が必要です。"
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonRed
                )
            ) {
                Text("許可する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = StatusDisconnected
            )
        },
        title = {
            Text("エラー")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
