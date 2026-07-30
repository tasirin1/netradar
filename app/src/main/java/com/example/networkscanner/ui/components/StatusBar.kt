package com.tasirin.network.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasirin.network.radar.ui.theme.StatusGreen
import com.tasirin.network.radar.ui.theme.StatusRed

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
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isOk) StatusGreen.copy(alpha = 0.08f)
                    else StatusRed.copy(alpha = 0.08f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Animated dot
            val infiniteTransition = rememberInfiniteTransition(label = "status")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(if (isScanning) 500 else 800),
                    repeatMode = RepeatMode.Reverse
                ), label = "statusDot"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background((if (isOk) StatusGreen else StatusRed).copy(
                        alpha = if (isScanning) dotAlpha else 1f
                    ))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = if (isOk) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                fontWeight = if (isScanning) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }

        if (isScanning) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (progress.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = progress,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
