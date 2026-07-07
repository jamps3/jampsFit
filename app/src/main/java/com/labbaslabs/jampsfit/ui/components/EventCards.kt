package com.labbaslabs.jampsfit.ui.components

import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.labbaslabs.jampsfit.WatchState
import com.labbaslabs.jampsfit.database.EVENT_TYPE_DANCING
import com.labbaslabs.jampsfit.database.EventEntity
import com.labbaslabs.jampsfit.database.FestivalEntity
import com.labbaslabs.jampsfit.gamification.ACHIEVEMENT_SCOPE_FESTIVAL
import com.labbaslabs.jampsfit.gamification.Achievement
import com.labbaslabs.jampsfit.gamification.calculateGamificationSummary
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun DancingEventControlCard(
    state: WatchState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val activeEvent = state.activeEvent
    val latestDancingEvent = state.recentEvents.firstOrNull { it.type == EVENT_TYPE_DANCING }
    var now by remember(activeEvent?.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmStop by remember { mutableStateOf(false) }

    LaunchedEffect(activeEvent?.id) {
        while (activeEvent != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    SleekCard(borderColor = Color(0xFFE91E63)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE91E63).copy(alpha = 0.14f)) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.padding(8.dp).size(26.dp)
                    )
                }
                Column {
                    Text("Dancing Event", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activeEvent == null) "Ready" else "Recording",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeEvent == null) Color.Gray else Color(0xFFE91E63)
                    )
                }
            }
            if (activeEvent == null) {
                Button(onClick = onStart) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Start")
                }
            } else {
                OutlinedButton(onClick = { confirmStop = true }) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Stop")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        val eventForStats = activeEvent ?: latestDancingEvent
        if (eventForStats == null) {
            Text("No dancing events yet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            EventStats(event = eventForStats, now = now)
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop dancing event?") },
            text = { Text("Finish this Dancing Event and save it to the current festival.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStop = false
                        onStop()
                    }
                ) {
                    Text("Stop")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text("Keep dancing")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FestivalProgressCard(
    state: WatchState,
    onCreateFestival: () -> Unit,
    onSelectFestival: (Long) -> Unit,
    onRenameFestival: (Long, String) -> Unit,
    onFestivalImageChange: (Long, String?) -> Unit,
    onActivateFestival: (Long) -> Unit,
    onDeleteFestival: (Long) -> Unit,
    onAttachEventToFestival: (Long) -> Unit,
    onMoveEventToFestival: (Long, Long) -> Unit,
    onDeleteEvent: (Long) -> Unit
) {
    val selectedFestival = state.selectedFestival()
    val selectedFestivalId = selectedFestival?.id
    val festivalEvents = remember(state.recentEvents, selectedFestivalId) {
        state.recentEvents.filter { event -> selectedFestivalId == null || event.festivalId == selectedFestivalId }
    }
    val festivalState = state.copy(recentEvents = festivalEvents)
    val summary = remember(festivalState) { calculateGamificationSummary(festivalState) }
    val festivalAchievements = summary.achievements.filter { it.scope == ACHIEVEMENT_SCOPE_FESTIVAL }
    val unlocked = festivalAchievements.filter { it.unlocked }
    val nextUp = festivalAchievements
        .filter { !it.unlocked && it.progressTarget != null }
        .sortedByDescending { (it.progressValue ?: 0).toFloat() / (it.progressTarget ?: 1) }
        .take(3)
    val groupedAchievements = festivalAchievements.groupBy { it.group }
    val recentCompleted = festivalEvents
        .filter { it.endTime != null }
        .take(8)
    val attachableEvents = remember(state.recentEvents, selectedFestivalId) {
        state.recentEvents
            .filter { event ->
                event.type == EVENT_TYPE_DANCING &&
                    event.endTime != null &&
                    selectedFestivalId != null &&
                    event.festivalId != selectedFestivalId
            }
            .take(3)
    }
    val selectedIndex = state.festivals.indexOfFirst { it.id == selectedFestivalId }.takeIf { it >= 0 } ?: 0
    var editingName by remember(selectedFestivalId) { mutableStateOf(false) }
    var confirmDeleteFestival by remember(selectedFestivalId) { mutableStateOf(false) }
    var draftName by remember(selectedFestival?.name) { mutableStateOf(selectedFestival?.name ?: "Festival Progress") }
    var choosingImage by remember(selectedFestivalId) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedFestivalId?.let { id -> onFestivalImageChange(id, it.toString()) }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { uri -> selectedFestivalId?.let { id -> onFestivalImageChange(id, uri.toString()) } }
        }
    }

    SleekCard(borderColor = Color(0xFF03A9F4)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                FestivalIcon(
                    festival = selectedFestival,
                    onClick = { if (selectedFestival != null) choosingImage = true }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedFestival?.name ?: "Festival Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = selectedFestival != null) {
                            draftName = selectedFestival?.name ?: ""
                            editingName = true
                        }
                    )
                    Text(
                        "${if (selectedFestival?.isActive == true) "Active" else "Inactive"} • ${unlocked.size}/${festivalAchievements.size} unlocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedFestival?.isActive == true) Color(0xFF8BC34A) else Color.Gray
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { state.festivals.getOrNull(selectedIndex - 1)?.let { onSelectFestival(it.id) } },
                    enabled = selectedIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous festival")
                }
                IconButton(
                    onClick = { state.festivals.getOrNull(selectedIndex + 1)?.let { onSelectFestival(it.id) } },
                    enabled = selectedIndex < state.festivals.lastIndex
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next festival")
                }
                IconButton(onClick = onCreateFestival) {
                    Icon(Icons.Default.Add, contentDescription = "New festival")
                }
            }
        }
        if (selectedFestival != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectedFestival.isActive) "This festival is active" else "This festival is inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedFestival.isActive) Color(0xFF8BC34A) else Color.Gray
                )
                OutlinedButton(
                    onClick = { onActivateFestival(selectedFestival.id) },
                    enabled = !selectedFestival.isActive
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (selectedFestival.isActive) "Active" else "Activate")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (recentCompleted.isEmpty()) {
            Text("No completed festival events", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Festival Timeline", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { shareFestivalSummary(context, selectedFestival, festivalEvents) }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Share")
                    }
                }
                recentCompleted.forEach { event ->
                    EventSummaryRow(
                        event = event,
                        festivals = state.festivals,
                        currentFestivalId = selectedFestivalId,
                        onMoveToFestival = { festivalId -> onMoveEventToFestival(event.id, festivalId) },
                        doubleConfirm = state.doubleConfirmationsEnabled,
                        onDelete = { onDeleteEvent(event.id) }
                    )
                }
            }
        }

        if (attachableEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Attach to Festival", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                attachableEvents.forEach { event ->
                    AttachableEventRow(
                        event = event,
                        onAttach = { onAttachEventToFestival(event.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (nextUp.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Next Up", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                nextUp.forEach { FestivalAchievementProgressRow(it) }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedAchievements.forEach { (group, achievements) ->
                val groupUnlocked = achievements.count { it.unlocked }
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "$group $groupUnlocked/${achievements.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            achievements.forEach { achievement ->
                                FestivalAchievementChip(achievement)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingName && selectedFestival != null) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Name this festival") },
            text = {
                TextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameFestival(selectedFestival.id, draftName)
                        editingName = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmDeleteFestival = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF7043))
                        Text("Delete", color = Color(0xFFFF7043))
                    }
                    TextButton(onClick = { editingName = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (confirmDeleteFestival && selectedFestival != null) {
        ConfirmActionDialog(
            title = "Delete festival?",
            text = "This deletes the festival. Events, candies, and meals are kept but detached from it.",
            confirmLabel = "Delete",
            doubleConfirm = state.doubleConfirmationsEnabled,
            onConfirm = {
                onDeleteFestival(selectedFestival.id)
                editingName = false
            },
            onDismiss = { confirmDeleteFestival = false }
        )
    }

    if (choosingImage && selectedFestival != null) {
        AlertDialog(
            onDismissRequest = { choosingImage = false },
            title = { Text("Festival image") },
            text = { Text("Life is a festival anyway.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            choosingImage = false
                            galleryLauncher.launch(arrayOf("image/*"))
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Gallery")
                    }
                    TextButton(
                        onClick = {
                            choosingImage = false
                            val dir = File(context.filesDir, "festival-images").apply { mkdirs() }
                            val file = File(dir, "festival-${selectedFestival.id}-${System.currentTimeMillis()}.jpg")
                            val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            cameraUri = photoUri
                            cameraLauncher.launch(photoUri)
                        }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Camera")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onFestivalImageChange(selectedFestival.id, null)
                        choosingImage = false
                    }
                ) {
                    Text("Clear")
                }
            }
        )
    }
}

@Composable
private fun FestivalIcon(festival: FestivalEntity?, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(festival?.imageUri) {
        festival?.imageUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF03A9F4).copy(alpha = 0.14f),
        modifier = Modifier.size(42.dp).clickable(enabled = festival != null, onClick = onClick)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFF03A9F4),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun EventStats(event: EventEntity, now: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventStat("Time", event.durationLabel(now), Icons.Default.Timer, Color(0xFF03A9F4), Modifier.weight(1f))
            EventStat("Steps", event.stepDelta.toString(), Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFF8BC34A), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EventStat("BPM", event.averageBpm?.toString() ?: "--", Icons.Default.Favorite, Color(0xFFE91E63), Modifier.weight(1f))
            EventStat("kcal", event.activeCalories.toString(), Icons.Default.LocalFireDepartment, Color(0xFFFF9800), Modifier.weight(1f))
        }
    }
}

@Composable
private fun EventSummaryRow(
    event: EventEntity,
    festivals: List<FestivalEntity>,
    currentFestivalId: Long?,
    onMoveToFestival: (Long) -> Unit,
    doubleConfirm: Boolean,
    onDelete: () -> Unit
) {
    var confirmDelete by remember(event.id) { mutableStateOf(false) }
    var showDetails by remember(event.id) { mutableStateOf(false) }
    var choosingFestival by remember(event.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDetails = true }.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${event.type} • ${event.durationLabel(event.endTime ?: event.lastUpdatedTime)} • ${event.qualityLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Text("${event.stepDelta} steps  ${event.activeCalories} kcal", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
            IconButton(onClick = { choosingFestival = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Move event", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete event", tint = Color.Gray, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(event.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${event.type}")
                    Text("Duration: ${event.durationLabel(event.endTime ?: event.lastUpdatedTime)}")
                    Text("Steps: ${event.stepDelta}")
                    Text("Distance: ${event.distanceDelta} m")
                    Text("Calories: ${event.activeCalories}")
                    Text("BPM: ${event.averageBpm ?: "--"} (${event.minBpm ?: "--"}-${event.maxBpm ?: "--"})")
                    Text("Samples: ${event.heartRateSamples}")
                    Text("Quality: ${event.qualityLabel()}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (choosingFestival) {
        AlertDialog(
            onDismissRequest = { choosingFestival = false },
            title = { Text("Move to festival") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    festivals.forEach { festival ->
                        OutlinedButton(
                            onClick = {
                                choosingFestival = false
                                onMoveToFestival(festival.id)
                            },
                            enabled = festival.id != currentFestivalId,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(festival.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingFestival = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmDelete) {
        ConfirmActionDialog(
            title = "Delete dancing event?",
            text = "This removes the event from the current festival. The raw watch health history stays untouched.",
            confirmLabel = "Delete",
            doubleConfirm = doubleConfirm,
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false }
        )
    }
}

private fun EventEntity.qualityLabel(): String {
    val metrics = buildList {
        if (stepDelta > 0) add("steps")
        if (activeCalories > 0) add("kcal")
        if (heartRateSamples > 0) add("HR")
        if (distanceDelta > 0) add("distance")
    }
    return if (metrics.isEmpty()) "metadata only" else metrics.joinToString("+")
}

@Composable
private fun AttachableEventRow(event: EventEntity, onAttach: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${event.durationLabel(event.endTime ?: event.lastUpdatedTime)}  ${event.stepDelta} steps  ${event.activeCalories} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            OutlinedButton(onClick = onAttach) {
                Text("Attach")
            }
        }
    }
}

@Composable
private fun FestivalAchievementChip(achievement: Achievement) {
    val color = if (achievement.unlocked) Color(0xFF4CAF50) else Color.Gray
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = if (achievement.unlocked) 0.12f else 0.08f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (achievement.unlocked) Icons.Default.CheckCircle else Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(achievement.title, style = MaterialTheme.typography.labelSmall, color = if (achievement.unlocked) Color.LightGray else Color.Gray)
        }
    }
}

@Composable
private fun FestivalAchievementProgressRow(achievement: Achievement) {
    val value = achievement.progressValue ?: 0
    val target = achievement.progressTarget ?: 1
    val progress = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(achievement.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${value.coerceAtMost(target)}/$target ${achievement.progressUnit}".trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Color(0xFF03A9F4),
                trackColor = Color.DarkGray.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EventStat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

private fun EventEntity.durationLabel(now: Long): String {
    val seconds = if (isActive) {
        ((now - startTime) / 1000L).coerceAtLeast(durationSeconds.toLong()).toInt()
    } else {
        durationSeconds
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%d:%02d".format(minutes, remainder)
}

private fun WatchState.selectedFestival(): FestivalEntity? {
    return festivals.firstOrNull { it.id == selectedFestivalId } ?: festivals.maxByOrNull { it.createdAt }
}

private fun shareFestivalSummary(context: android.content.Context, festival: FestivalEntity?, events: List<EventEntity>) {
    val completed = events.filter { it.endTime != null }
    val text = buildString {
        appendLine(festival?.name ?: "Festival Summary")
        appendLine("${completed.size} events")
        appendLine("${completed.sumOf { it.stepDelta }} steps")
        appendLine("${completed.sumOf { it.activeCalories }} kcal")
        appendLine()
        completed.take(20).forEach { event ->
            appendLine("${event.type}: ${event.durationLabel(event.endTime ?: event.lastUpdatedTime)}, ${event.stepDelta} steps, ${event.activeCalories} kcal, ${event.qualityLabel()}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, festival?.name ?: "Festival Summary")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share festival summary"))
}
