package com.wayy.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * MapPulse Ultimate Animation System
 * Spring physics for fluid, natural motion
 */

// Spring animation configurations
enum class SpringStiffness(val value: Float) {
    LOW(200f),
    MEDIUM(400f),
    HIGH(600f)
}

enum class SpringDamping(val value: Float) {
    BOUNCY(0.5f),
    MEDIUM(0.8f),
    STIFF(1.0f);
}

@Composable
fun animateWithSpring(
    targetValue: Float,
    stiffness: SpringStiffness = SpringStiffness.MEDIUM,
    damping: SpringDamping = SpringDamping.MEDIUM
): Float {
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = SpringSpec(
            dampingRatio = damping.value,
            stiffness = stiffness.value
        )
    ).value
}

@Composable
fun rememberScaleState(
    isActive: Boolean,
    activeScale: Float = 1.05f,
    inactiveScale: Float = 1f
): Float {
    var scale by remember { mutableStateOf(inactiveScale) }
    val targetScale = if (isActive) activeScale else inactiveScale

    scale = animateWithSpring(
        targetValue = targetScale,
        stiffness = SpringStiffness.LOW,
        damping = SpringDamping.BOUNCY
    )

    return scale
}

@Composable
fun rememberAlphaState(
    isVisible: Boolean
): Float {
    return animateWithSpring(
        targetValue = if (isVisible) 1f else 0f,
        stiffness = SpringStiffness.MEDIUM,
        damping = SpringDamping.MEDIUM
    )
}
