package com.trialtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trialtracker.app.ui.theme.TT

/** The card shell used everywhere: one tone above the background, 1px border, no shadow. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    color: Color = TT.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, TT.Border, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TT.TextPrimary,
        )
        if (actionLabel != null && onAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TT.Accent,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TT.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Small pill used for counts, offer badges and price footers. */
@Composable
fun Pill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    background: Color = tint.copy(alpha = 0.16f),
    bold: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun IconTile(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Int = 52,
    corner: Int = 16,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TT.TextPrimary)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TT.TextSecondary,
        )
    }
}

/** "2026-07-18" -> "18.07.2026". Falls back to the raw string for odd input. */
fun formatVerified(raw: String): String {
    val parts = raw.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else raw
}
