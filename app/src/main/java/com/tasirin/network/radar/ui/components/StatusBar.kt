package com.tasirin.network.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            val dotColor = if (isOk) StatusGreen else StatusRed
            // Animasi dot hanya dijalankan saat scanning. Animasi infinite yang
            // berjalan terus di dalam item LazyColumn membuat item ter-invalidasi
            // tiap frame dan bisa memicu crash Compose "pending composition has
            // not been applied" saat daftar diukur ulang (mis. saat deep scan).
            val dotAlpha = if (isScanning) {
                val infiniteTransition = rememberInfiniteTransition(label = "dot")
                infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "dotAlpha"
                ).value
            } else 1f
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = dotAlpha))
                    .shadow(if (isScanning) 2.dp else 0.dp, CircleShape)
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
