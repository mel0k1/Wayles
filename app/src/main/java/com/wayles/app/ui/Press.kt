package com.wayles.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberPressRadius(source: MutableInteractionSource, rest: Dp, pressed: Dp): Dp {
    val down by source.collectIsPressedAsState()
    val radius by animateDpAsState(
        targetValue = if (down) pressed else rest,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "pressRadius"
    )
    return radius
}

@Composable
fun WaylesButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val radius = rememberPressRadius(source, 28.dp, 14.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = RoundedCornerShape(radius),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
