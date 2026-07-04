package com.alexis.tvtracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexis.tvtracker.R

@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        actions()
    }
}

@Composable
fun MoreVertButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        MoreVertIcon(color = color)
    }
}

@Composable
private fun MoreVertIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val x = size.width / 2f
        val radius = 2.2.dp.toPx()
        drawCircle(color = color, radius = radius, center = Offset(x, size.height * 0.25f))
        drawCircle(color = color, radius = radius, center = Offset(x, size.height * 0.50f))
        drawCircle(color = color, radius = radius, center = Offset(x, size.height * 0.75f))
    }
}
