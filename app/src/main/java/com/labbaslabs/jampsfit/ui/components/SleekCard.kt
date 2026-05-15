package com.labbaslabs.jampsfit.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SleekCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffset"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.2f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        start = Offset(shineOffset, 0f),
                        end = Offset(shineOffset + 300f, 300f)
                    )
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
    color: Color
) {
    SleekCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = color
            )
        }
    }
}
