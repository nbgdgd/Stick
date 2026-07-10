package com.stick.stickersource.tiktok

import com.stick.core.model.StickerFormat
import com.stick.stickersource.tiktok.dto.CommentDto
import com.stick.stickersource.tiktok.dto.CommentImageDto
import com.stick.stickersource.tiktok.dto.CommentStickerStruct
import com.stick.stickersource.tiktok.dto.CommentUserDto
import com.stick.stickersource.tiktok.dto.StickerUrlSet
import com.stick.stickersource.tiktok.dto.UrlListDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the one place TikTok comment DTOs are mapped to the domain model. */
class TikTokMapperTest {

    private fun url(u: String) = UrlListDto(urlList = listOf(u))

    @Test
    fun `maps animated sticker-pack sticker from cmt_sticker_struct`() {
        val comment = CommentDto(
            id = "c1",
            text = "peak",
            user = CommentUserDto(nickname = "alice"),
            commentSticker = CommentStickerStruct(
                name = "eris",
                animatedUrl = StickerUrlSet(high = url("https://cdn/anim.awebp")),
                staticUrl = StickerUrlSet(high = url("https://cdn/static.webp")),
            ),
        )

        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")

        assertEquals(1, stickers.size)
        assertEquals("https://cdn/anim.awebp", stickers[0].downloadUrl)
        assertEquals("https://cdn/static.webp", stickers[0].previewUrl)
        assertEquals(StickerFormat.WEBP_ANIMATED, stickers[0].format)
    }

    @Test
    fun `maps image comments`() {
        val comment = CommentDto(
            id = "c2",
            imageList = listOf(CommentImageDto(originUrl = url("https://cdn/a.image"))),
        )
        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")
        assertEquals(1, stickers.size)
        assertEquals(StickerFormat.JPEG, stickers[0].format)
    }

    @Test
    fun `includes stickers from inlined replies`() {
        val comment = CommentDto(
            id = "top",
            replyComment = listOf(
                CommentDto(id = "r1", commentSticker = CommentStickerStruct(animatedUrl = StickerUrlSet(high = url("https://cdn/r.awebp")))),
            ),
        )
        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")
        assertEquals(1, stickers.size)
        assertEquals("https://cdn/r.awebp", stickers[0].downloadUrl)
    }

    @Test
    fun `text-only comment yields nothing`() {
        assertTrue(TikTokMapper.stickersFromComment(CommentDto(id = "c3", text = "hi"), "https://video").isEmpty())
    }
}
