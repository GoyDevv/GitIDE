package com.goydevv.gitide.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun FloatingVibrantShapesBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundOrbMovement")
    
    val animationOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Orb1"
    )

    val animationOffset2 by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Orb2"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = BlurEffect(90f, 90f)
            }
    ) {
        drawCircle(
            color = Color(0xFF4C1D95).copy(alpha = 0.45f),
            radius = size.width / 1.5f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width / 4f + animationOffset1,
                y = size.height / 5f + animationOffset2
            )
        )
        drawCircle(
            color = Color(0xFF1D4ED8).copy(alpha = 0.25f),
            radius = size.width / 2f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width / 1.2f - animationOffset2,
                y = size.height / 1.2f - animationOffset1
            )
        )
    }
}
