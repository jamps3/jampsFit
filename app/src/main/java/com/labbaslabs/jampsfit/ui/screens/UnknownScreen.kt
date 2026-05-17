package com.labbaslabs.jampsfit.ui.screens

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(state: WatchState, onResetClick: () -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Logs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        TabRow(selectedTabIndex = selectedTab, containerColor = androidx.compose.ui.graphics.Color.Transparent, divider = {}) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Unknown") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("System Log") })
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Long-press to copy all entries.", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.Gray)
                IconButton(onClick = onResetClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear all", tint = MaterialTheme.colorScheme.error)
                }
            }
            SleekCard(modifier = Modifier.weight(1f).combinedClickable(
            onClick = {},
            onLongClick = {
                val allText = state.unknownMessages.joinToString("\n")
                if (allText.isNotEmpty()) {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, allText))) }
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        )) {
            val scrollState = rememberScrollState()
            LaunchedEffect(state.unknownMessages) { scrollState.animateScrollTo(scrollState.maxValue) }
            if (state.unknownMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No unknown messages", color = androidx.compose.ui.graphics.Color.Gray)
                }
            } else {
                Text(
                    text = state.unknownMessages.joinToString("\n"),
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
        } else {
            SleekCard(modifier = Modifier.weight(1f).combinedClickable(
                onClick = {},
                onLongClick = {
                    if (state.debugLog.isNotEmpty()) {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, state.debugLog))) }
                        Toast.makeText(context, "System log copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
            )) {
                val scrollState = rememberScrollState()
                LaunchedEffect(state.debugLog) { scrollState.animateScrollTo(scrollState.maxValue) }
                Text(
                    text = state.debugLog,
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
