package com.wayy.ui.components.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayy.ui.theme.WayyColors

data class LaneConfig(
    val id: String,
    val isActive: Boolean
)

@Composable
fun LaneGuidanceOverlay(
    lanes: List<LaneConfig>,
    modifier: Modifier = Modifier
) {
    if (lanes.isEmpty()) return
    Row(
        modifier = modifier
            .background(
                color = WayyColors.BgSecondary.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lanes.forEach { lane ->
            val color by animateColorAsState(
                targetValue = if (lane.isActive) WayyColors.PrimaryLime else Color.White.copy(alpha = 0.4f),
                label = "lane_color"
            )
            Spacer(
                modifier = Modifier
                    .width(16.dp)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(8.dp))
            )
        }
        Text(
            text = "Lanes",
            color = WayyColors.TextSecondary,
            fontSize = 10.sp
        )
    }
}
