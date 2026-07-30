package com.vpet.waifu.ui.character

import androidx.compose.ui.graphics.Color

/**
 * Character colours, in one place so a later "outfits" phase can ship a new
 * look by passing a different palette rather than editing the renderer.
 */
data class PetPalette(
    val hair: Color = Color(0xFF7B5EA7),
    val hairShade: Color = Color(0xFF5E4483),
    val hairLight: Color = Color(0xFFA98BD6),
    val skin: Color = Color(0xFFFFE1D0),
    val skinShade: Color = Color(0xFFEEBFA8),
    val uniform: Color = Color(0xFF44396E),
    val uniformShade: Color = Color(0xFF332A57),
    val skirt: Color = Color(0xFF3B3163),
    val collar: Color = Color(0xFFF5EFFD),
    val ribbon: Color = Color(0xFFE8577E),
    val sock: Color = Color(0xFFF5EFFD),
    val shoe: Color = Color(0xFF2B2447),
    val eyeDark: Color = Color(0xFF2E2044),
    val iris: Color = Color(0xFF8B5FD6),
    val irisDeep: Color = Color(0xFF573A8F),
    val irisLight: Color = Color(0xFFCBAEF7),
    val mouth: Color = Color(0xFFB84A63),
    val mouthInner: Color = Color(0xFF8E2F46),
    val blush: Color = Color(0xFFF98FAE),
    val white: Color = Color.White,
    val prop: Color = Color(0xFF6B5CA5),
    val propDark: Color = Color(0xFF463A78),
    val accent: Color = Color(0xFFFFC46B),
) {
    companion object {
        val Default = PetPalette()
    }
}
