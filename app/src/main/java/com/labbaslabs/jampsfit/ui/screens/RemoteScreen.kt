package com.labbaslabs.jampsfit.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbaslabs.jampsfit.LocalMainViewModel
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.ui.components.SleekCard

@Composable
fun RemoteScreen(state: WatchState, scrollState: ScrollState = rememberScrollState()) {
    val viewModel = LocalMainViewModel.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Remote Controls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        
        SleekCard {
            Text(text = "Shutter Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteActionOption(label = "Camera Shutter", selected = state.shutterAction == "Camera") { viewModel.updateShutterAction("Camera") }
            RemoteActionOption(label = "Find My Phone", selected = state.shutterAction == "FindMyPhone") { viewModel.updateShutterAction("FindMyPhone") }
            RemoteActionOption(label = "Play/Pause Media", selected = state.shutterAction == "Media") { viewModel.updateShutterAction("Media") }
            RemoteActionOption(label = "None", selected = state.shutterAction == "None") { viewModel.updateShutterAction("None") }
        }

        SleekCard {
            Text(text = "Music Buttons Action", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteActionOption(label = "Control Phone Media", selected = state.musicAction == "Media") { viewModel.updateMusicAction("Media") }
            RemoteActionOption(label = "Control System Volume", selected = state.musicAction == "Volume") { viewModel.updateMusicAction("Volume") }
            RemoteActionOption(label = "Utility (Torch/Assistant)", selected = state.musicAction == "Utility") { viewModel.updateMusicAction("Utility") }
            RemoteActionOption(label = "Custom Actions", selected = state.musicAction == "Custom") { viewModel.updateMusicAction("Custom") }
            RemoteActionOption(label = "None", selected = state.musicAction == "None") { viewModel.updateMusicAction("None") }

            if (state.musicAction == "Custom") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "Custom Button Mapping", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                
                CustomActionDropdown(label = "Play/Pause Button", currentAction = state.playPauseAction) { viewModel.updateCustomAction("Play/Pause", it) }
                CustomActionDropdown(label = "Next Button", currentAction = state.nextAction) { viewModel.updateCustomAction("Next", it) }
                CustomActionDropdown(label = "Previous Button", currentAction = state.prevAction) { viewModel.updateCustomAction("Previous", it) }
            }

            if (state.musicAction == "Volume") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Volume Steps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(text = "${state.volumeSteps}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = state.volumeSteps.toFloat(),
                    onValueChange = { viewModel.updateVolumeSteps(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    text = "Controls how many volume steps are changed per button press.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SleekCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (state.isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (state.isConnected) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.lastRemoteEvent ?: "No events detected",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (state.lastRemoteEvent != null) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }

        Text(text = "Active Listeners", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        RemoteInfoItem(icon = Icons.Default.MusicNote, title = "Music Player", desc = "Buttons: Play, Pause, Skip")
        RemoteInfoItem(icon = Icons.Default.CameraAlt, title = "Shutter", desc = "Watch Shutter screen")
    }
}

@Composable
fun CustomActionDropdown(label: String, currentAction: String, onActionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }
    val actions = listOf("Play/Pause", "Next Track", "Previous Track", "Volume Up", "Volume Down", "Mute", "Flashlight", "Assistant", "Screenshot", "None")

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = currentAction)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action) },
                        onClick = {
                            onActionSelected(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteActionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RemoteInfoItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
