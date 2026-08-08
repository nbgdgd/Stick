package com.stick.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A single sticker tile.
 *
 * Selection is drawn as a real, visible check badge plus a tinted border, so
 * multi-select is obvious at a glance. The favourite heart is optional
 * ([showFavorite]) because it is only meaningful in the library — screens that
 * have no favourite state hide it instead of showing a heart that never changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerCard(
    model: Any?,
    contentDescription: String?,
    isFavorite: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = true,
    showSelection: Boolean = true,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedStickerImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )

            // Selected state: tinted border around the whole tile…
            if (isSelected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, selectedColor, RoundedCornerShape(16.dp)),
                )
            }

            // …plus an explicit check badge in the corner.
            if (showSelection) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) selectedColor
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) selectedColor else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (showFavorite) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        // Outline uses a theme colour so it stays visible on both
                        // light and dark tiles (the old hard-coded white vanished).
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
