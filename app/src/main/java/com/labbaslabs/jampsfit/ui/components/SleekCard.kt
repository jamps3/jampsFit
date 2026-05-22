package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import com.labbaslabs.jampsfit.LocalWatchState

@Composable
fun SleekCard(
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = LocalWatchState.current
    val finalBorderColor = borderColor ?: Color(state.borderColor)
    val thickness = state.borderThickness.dp
    val alphaStartEnd = state.borderAlpha
    val alphaMid = (state.borderAlpha * 0.375f).coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = thickness,
                brush = Brush.linearGradient(
                    colors = listOf(
                        finalBorderColor.copy(alpha = alphaStartEnd),
                        finalBorderColor.copy(alpha = alphaMid),
                        finalBorderColor.copy(alpha = alphaStartEnd)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun DataCard(
    label: String,
    value: String,
    supportingText: String? = null,
    icon: ImageVector,
    color: Color,
    action: @Composable (() -> Unit)? = null
) {
    SleekCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp).size(24.dp),
                        tint = color
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    if (supportingText != null) {
                        Text(
                            text = supportingText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = color
                )
                if (action != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    action()
                }
            }
        }
    }
}
