package com.wayy.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

enum class SpringStiffness(val value: Float) {
    LOW(200f),
    MEDIUM(400f),
    HIGH(600f)
}

enum class SpringDamping(val value: Float) {
    BOUNCY(0.5f),
    MEDIUM(0.8f),
    STIFF(1.0f)
}

@Composable
fun animateWithSpring(
    targetValue: Float,
    stiffness: SpringStiffness = SpringStiffness.MEDIUM,
    damping: SpringDamping = SpringDamping.MEDIUM
): State<Float> {
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = damping.value,
            stiffness = stiffness.value
        ),
        label = "spring"
    )
}

@Composable
fun rememberScaleState(
    isActive: Boolean,
    activeScale: Float = 1.05f,
    inactiveScale: Float = 1f
): State<Float> {
    return animateFloatAsState(
        targetValue = if (isActive) activeScale else inactiveScale,
        animationSpec = spring(
            dampingRatio = SpringDamping.BOUNCY.value,
            stiffness = SpringStiffness.LOW.value
        ),
        label = "scale"
    )
}

@Composable
fun rememberAlphaState(
    isVisible: Boolean
): State<Float> {
    return animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = SpringDamping.MEDIUM.value,
            stiffness = SpringStiffness.MEDIUM.value
        ),
        label = "alpha"
    )
}
