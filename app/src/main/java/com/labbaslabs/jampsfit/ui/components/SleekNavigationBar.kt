package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.labbaslabs.jampsfit.LocalWatchState

data class TabSpec(val label: String, val icon: ImageVector)

@Composable
fun SleekNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit, tabs: List<TabSpec>) {
    val state = LocalWatchState.current
    val borderColor = Color(state.borderColor).copy(alpha = state.borderAlpha)
    val thickness = state.borderThickness.dp
    
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(thickness, Brush.horizontalGradient(listOf(borderColor, Color.Transparent, borderColor)), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 10.sp) }
            )
        }
    }
}
