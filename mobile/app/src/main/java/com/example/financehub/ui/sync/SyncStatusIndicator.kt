package com.example.financehub.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financehub.sync.ConnectivityState
import com.example.financehub.sync.SyncStatus

/**
 * Composable that displays the current sync and connectivity status
 */
@Composable
fun SyncStatusIndicator(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isConnectedToWifi by ConnectivityState.isConnectedToHomeWifi.collectAsStateWithLifecycle()
    val isServerReachable by ConnectivityState.isServerReachable.collectAsStateWithLifecycle()
    val syncStatus by ConnectivityState.syncStatus.collectAsStateWithLifecycle()
    val lastSyncTimestamp by ConnectivityState.lastSyncTimestamp.collectAsStateWithLifecycle()
    val pendingSyncCount by ConnectivityState.pendingSyncCount.collectAsStateWithLifecycle()

    if (compact) {
        CompactSyncIndicator(
            isConnectedToWifi = isConnectedToWifi,
            isServerReachable = isServerReachable,
            syncStatus = syncStatus,
            modifier = modifier
        )
    } else {
        DetailedSyncIndicator(
            isConnectedToWifi = isConnectedToWifi,
            isServerReachable = isServerReachable,
            syncStatus = syncStatus,
            lastSyncTimestamp = lastSyncTimestamp,
            pendingSyncCount = pendingSyncCount,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactSyncIndicator(
    isConnectedToWifi: Boolean,
    isServerReachable: Boolean,
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (icon, color, text) = when {
        syncStatus == SyncStatus.SYNCING -> Triple(Icons.Default.Sync, Color(0xFF2196F3), "Syncing...")
        isServerReachable -> Triple(Icons.Default.CloudDone, Color(0xFF4CAF50), "Online")
        isConnectedToWifi -> Triple(Icons.Default.Wifi, Color(0xFFFF9800), "WiFi Only")
        else -> Triple(Icons.Default.CloudOff, Color(0xFF757575), "Offline")
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DetailedSyncIndicator(
    isConnectedToWifi: Boolean,
    isServerReachable: Boolean,
    syncStatus: SyncStatus,
    lastSyncTimestamp: Long?,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sync Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // WiFi Status
            StatusRow(
                icon = if (isConnectedToWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                text = if (isConnectedToWifi) "Connected to Home WiFi" else "Not on Home WiFi",
                isPositive = isConnectedToWifi
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Server Status
            StatusRow(
                icon = if (isServerReachable) Icons.Default.Cloud else Icons.Default.CloudOff,
                text = if (isServerReachable) "Server Reachable" else "Server Unreachable",
                isPositive = isServerReachable
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sync Status
            val (syncIcon, syncText, syncColor) = when (syncStatus) {
                SyncStatus.IDLE -> Triple(Icons.Default.Schedule, "Ready to Sync", Color(0xFF757575))
                SyncStatus.SYNCING -> Triple(Icons.Default.Sync, "Syncing...", Color(0xFF2196F3))
                SyncStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, "Last Sync Successful", Color(0xFF4CAF50))
                SyncStatus.ERROR -> Triple(Icons.Default.Error, "Sync Failed", Color(0xFFF44336))
                SyncStatus.PENDING -> Triple(Icons.Default.Schedule, "Sync Pending", Color(0xFFFF9800))
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = syncIcon,
                    contentDescription = syncText,
                    tint = syncColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = syncText,
                    color = syncColor,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Last sync time
            if (lastSyncTimestamp != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last sync: ${formatTimestamp(lastSyncTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Pending sync count
            if (pendingSyncCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PendingActions,
                        contentDescription = "Pending syncs",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$pendingSyncCount items pending sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isPositive: Boolean
) {
    val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFF757575)
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} minutes ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "${diff / 86400_000} days ago"
    }
}