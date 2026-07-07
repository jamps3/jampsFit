package com.labbaslabs.jampsfit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    doubleConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val slideConfirmed = !doubleConfirm || sliderValue >= 0.98f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                if (doubleConfirm) {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    Text(
                        "Slide to confirm",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..1f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = slideConfirmed,
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
