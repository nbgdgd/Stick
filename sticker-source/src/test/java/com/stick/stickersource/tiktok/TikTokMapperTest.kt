package com.stick.stickersource.tiktok

import com.stick.core.model.StickerFormat
import com.stick.stickersource.tiktok.dto.CommentDto
import com.stick.stickersource.tiktok.dto.CommentImageDto
import com.stick.stickersource.tiktok.dto.CommentUserDto
import com.stick.stickersource.tiktok.dto.UrlListDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the one place TikTok comment DTOs are mapped to the domain model. */
class TikTokMapperTest {

    private fun url(u: String) = UrlListDto(urlList = listOf(u))

    @Test
    fun `maps each comment image to a sticker, preferring origin_url`() {
        val comment = CommentDto(
            id = "c1",
            text = "so cute",
            user = CommentUserDto(nickname = "alice"),
            imageList = listOf(
                CommentImageDto(originUrl = url("https://cdn/a.image"), cropUrl = url("https://cdn/a_small.image")),
                CommentImageDto(originUrl = url("https://cdn/b.webp")),
            ),
        )

        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")

        assertEquals(2, stickers.size)
        assertEquals("https://cdn/a.image", stickers[0].downloadUrl)
        assertEquals("https://cdn/a_small.image", stickers[0].previewUrl)
        assertEquals(StickerFormat.JPEG, stickers[0].format)
        assertEquals(StickerFormat.WEBP_ANIMATED, stickers[1].format)
        assertEquals("c1_1", stickers[1].id)
        assertTrue(stickers.all { it.sourceId == TikTokMapper.SOURCE_ID })
    }

    @Test
    fun `text-only comment yields nothing`() {
        val comment = CommentDto(id = "c2", text = "hello")
        assertTrue(TikTokMapper.stickersFromComment(comment, "https://video").isEmpty())
    }

    @Test
    fun `single image keeps the plain comment id`() {
        val comment = CommentDto(id = "c3", imageList = listOf(CommentImageDto(originUrl = url("https://cdn/x.gif"))))
        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")
        assertEquals(1, stickers.size)
        assertEquals("c3", stickers[0].id)
        assertEquals(StickerFormat.GIF, stickers[0].format)
    }
}
