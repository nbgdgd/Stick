package com.vpet.waifu.ui.character

import androidx.compose.ui.graphics.Color

/**
 * Character colours, in one place so a later "outfits" phase can ship a new
 * look by passing a different palette rather than editing the renderer.
 */
data class PetPalette(
    val hair: Color = Color(0xFFD6D3DC),
    val hairShade: Color = Color(0xFFA9A5B4),
    val hairLight: Color = Color(0xFFF2F0F6),
    val skin: Color = Color(0xFFFFE7D6),
    val skinShade: Color = Color(0xFFF0C3AB),
    val uniform: Color = Color(0xFFF7F2EA),
    val uniformShade: Color = Color(0xFFE0D6C8),
    val skirt: Color = Color(0xFFC0485E),
    val collar: Color = Color(0xFFFFFDF8),
    val ribbon: Color = Color(0xFFC0485E),
    val sock: Color = Color(0xFFFBF8F2),
    val shoe: Color = Color(0xFF3A3550),
    val eyeDark: Color = Color(0xFF241E32),
    val iris: Color = Color(0xFFF3B01F),
    val irisDeep: Color = Color(0xFFB87708),
    val irisLight: Color = Color(0xFFFFDF8C),
    val mouth: Color = Color(0xFFBE4B5F),
    val mouthInner: Color = Color(0xFF8E2F46),
    val blush: Color = Color(0xFFF98FAE),
    val white: Color = Color.White,
    val prop: Color = Color(0xFF6B5CA5),
    val propDark: Color = Color(0xFF463A78),
    val accent: Color = Color(0xFFFFC46B),
    /**
     * The ink every form is drawn with.
     *
     * The single biggest reason the old character read as a sticker for
     * toddlers: it had no line at all. Flat vector shapes with no outline is
     * the visual language of a children's app; a dark, consistent contour is
     * the visual language of anime.
     */
    val line: Color = Color(0xFF2B2438),
) {
    companion object {
        val Default = PetPalette()

        /**
         * The outfits, by the id [com.vpet.waifu.domain.Upgrades] sells them
         * under.
         *
         * Each changes the uniform, the ribbon and the accent rather than her
         * hair or skin: she should still read as the same character in a
         * different set of clothes, not as a recolour.
         */
        private val BY_NAME: Map<String, PetPalette> = mapOf(
            "uniform" to Default,
            "cocoa" to Default.copy(
                uniform = Color(0xFF6E4A34),
                uniformShade = Color(0xFF553725),
                skirt = Color(0xFF7C563E),
                ribbon = Color(0xFFE9A23B),
                collar = Color(0xFFFFF3E4),
                sock = Color(0xFFFFF3E4),
                accent = Color(0xFFFFD489),
            ),
            "mint" to Default.copy(
                uniform = Color(0xFF2F6B60),
                uniformShade = Color(0xFF24534A),
                skirt = Color(0xFF357569),
                ribbon = Color(0xFFF06E8E),
                collar = Color(0xFFEFFBF6),
                sock = Color(0xFFEFFBF6),
                accent = Color(0xFF7FE3C4),
            ),
            "sakura" to Default.copy(
                uniform = Color(0xFF8E4A6B),
                uniformShade = Color(0xFF6F3853),
                skirt = Color(0xFF9C5375),
                ribbon = Color(0xFFFFD1E0),
                collar = Color(0xFFFFF1F6),
                sock = Color(0xFFFFF1F6),
                accent = Color(0xFFFF9CC0),
            ),
            "midnight" to Default.copy(
                uniform = Color(0xFF1D1B33),
                uniformShade = Color(0xFF131226),
                skirt = Color(0xFF232041),
                ribbon = Color(0xFF6FD3FF),
                collar = Color(0xFFDDE6FF),
                sock = Color(0xFFDDE6FF),
                shoe = Color(0xFF0E0D1B),
                accent = Color(0xFF9CE0FF),
            ),
            "gold" to Default.copy(
                uniform = Color(0xFF4A3B18),
                uniformShade = Color(0xFF362B10),
                skirt = Color(0xFF5C4A1F),
                ribbon = Color(0xFFFFD35C),
                collar = Color(0xFFFFF8E3),
                sock = Color(0xFFFFF8E3),
                shoe = Color(0xFF2B2208),
                accent = Color(0xFFFFE9A8),
            ),
        )

        /** The palette for an outfit id, falling back to what she starts in. */
        fun forOutfit(outfitId: String?): PetPalette {
            val name = outfitId?.removePrefix("outfit_")
            return BY_NAME[name] ?: Default
        }
    }
}
