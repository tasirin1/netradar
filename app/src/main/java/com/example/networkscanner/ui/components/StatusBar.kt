package com.example.networkscanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networkscanner.ui.theme.StatusGreen
import com.example.networkscanner.ui.theme.StatusRed

@Composable
fun StatusBar(
    text: String,
    isOk: Boolean,
    isScanning: Boolean,
    progress: String,
    progressPercent: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            // Animated dot
            val infiniteTransition = rememberInfiniteTransition(label = "dot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600), repeatMode = RepeatMode.Reverse
                ), label = "dotAlpha"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background((if (isOk) StatusGreen else StatusRed).copy(
                        alpha = if (isScanning) dotAlpha else 1f
                    ))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
        }

        if (isScanning) {
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = progress,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}
