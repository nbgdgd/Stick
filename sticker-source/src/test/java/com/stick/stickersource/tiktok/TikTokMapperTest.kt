package com.stick.stickersource.tiktok

import com.stick.stickersource.tiktok.dto.CommentDto
import com.stick.stickersource.tiktok.dto.CommentImageDto
import com.stick.stickersource.tiktok.dto.StickerDto
import com.stick.stickersource.tiktok.dto.UrlListDto
import com.stick.stickersource.tiktok.dto.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Guards the one place TikTok DTOs are mapped to the domain model. */
class TikTokMapperTest {

    @Test
    fun `maps explicit sticker object`() {
        val comment = CommentDto(
            id = "c1",
            user = UserDto(nickname = "alice"),
            sticker = StickerDto(
                id = "s1",
                name = "cat",
                animateUrl = UrlListDto(urlList = listOf("https://cdn/animate.webp")),
                staticUrl = UrlListDto(urlList = listOf("https://cdn/static.webp")),
                width = 128, height = 128,
                keywords = listOf("cat", "cute"),
            ),
        )

        val sticker = TikTokMapper.stickerFromComment(comment, "https://video")

        assertNotNull(sticker)
        assertEquals("s1", sticker!!.id)
        assertEquals("https://cdn/animate.webp", sticker.downloadUrl)
        assertEquals("https://cdn/static.webp", sticker.previewUrl)
        assertEquals(listOf("cat", "cute"), sticker.keywords)
    }

    @Test
    fun `falls back to image_list gif`() {
        val comment = CommentDto(
            id = "c2",
            imageList = listOf(
                CommentImageDto(gifUrl = UrlListDto(urlList = listOf("https://cdn/x.gif"))),
            ),
        )

        val sticker = TikTokMapper.stickerFromComment(comment, "https://video")

        assertNotNull(sticker)
        assertEquals("https://cdn/x.gif", sticker!!.downloadUrl)
    }

    @Test
    fun `returns null when no sticker present`() {
        assertNull(TikTokMapper.stickerFromComment(CommentDto(id = "c3", text = "hi"), "https://video"))
    }
}
