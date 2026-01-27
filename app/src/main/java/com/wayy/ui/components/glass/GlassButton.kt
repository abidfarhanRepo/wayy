package com.wayy.ui.components.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors
import com.wayy.ui.theme.SpringDamping
import com.wayy.ui.theme.SpringStiffness
import com.wayy.ui.theme.animateWithSpring

/**
 * Glass morphic button with blur effect and spring animations
 *
 * @param onClick Callback when button is clicked
 * @param icon Icon to display
 * @param label Text label below icon
 * @param active Whether button is in active state
 * @param activeColor Color to use for active state
 * @param modifier Modifier for the button
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    activeColor: Color = WayyColors.PrimaryLime,
    modifier: Modifier = Modifier
) {
    val scale = animateWithSpring(
        targetValue = if (active) 1.05f else 1f,
        stiffness = SpringStiffness.LOW,
        damping = SpringDamping.BOUNCY
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active)
                activeColor.copy(alpha = 0.3f)
            else
                WayyColors.GlassLight,
            contentColor = if (active) activeColor else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (active)
                activeColor.copy(alpha = 0.5f)
            else
                WayyColors.GlassBorder
        ),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Icon-only glass button variant
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean = false,
    activeColor: Color = WayyColors.PrimaryLime,
    modifier: Modifier = Modifier
) {
    val scale = animateWithSpring(
        targetValue = if (active) 1.1f else 1f,
        stiffness = SpringStiffness.LOW,
        damping = SpringDamping.BOUNCY
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active)
                activeColor.copy(alpha = 0.3f)
            else
                WayyColors.GlassLight,
            contentColor = if (active) activeColor else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (active)
                activeColor.copy(alpha = 0.5f)
            else
                WayyColors.GlassBorder
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}
