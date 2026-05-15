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

data class TabSpec(val label: String, val icon: ImageVector)

@Composable
fun SleekNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit, tabs: List<TabSpec>) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 8.dp,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.dp, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent, Color.White.copy(alpha = 0.1f))), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
