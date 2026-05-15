package com.labbaslabs.jampsfit.ui.screens

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
fun UnknownScreen(state: WatchState) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Unknown Packets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "Long-press to copy all entries.", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.Gray)

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
    }
}
