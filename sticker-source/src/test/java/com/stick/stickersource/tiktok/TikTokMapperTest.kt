package com.stick.stickersource.tiktok

import com.stick.stickersource.tiktok.dto.TikwmComment
import com.stick.stickersource.tiktok.dto.TikwmCommentUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the one place tikwm comments are mapped to the domain model. */
class TikTokMapperTest {

    @Test
    fun `maps each comment image to a sticker`() {
        val comment = TikwmComment(
            id = "c1",
            text = "so cute",
            images = listOf("https://cdn/a.webp", "https://cdn/b.webp"),
            user = TikwmCommentUser(nickname = "alice"),
        )

        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")

        assertEquals(2, stickers.size)
        assertEquals("https://cdn/a.webp", stickers[0].downloadUrl)
        assertEquals("c1_1", stickers[1].id)
        assertTrue(stickers.all { it.sourceId == TikTokMapper.SOURCE_ID })
    }

    @Test
    fun `text-only comment yields nothing`() {
        val comment = TikwmComment(id = "c2", text = "hello", images = emptyList())
        assertTrue(TikTokMapper.stickersFromComment(comment, "https://video").isEmpty())
    }

    @Test
    fun `single image keeps the plain comment id`() {
        val comment = TikwmComment(id = "c3", images = listOf("https://cdn/x.gif"))
        val stickers = TikTokMapper.stickersFromComment(comment, "https://video")
        assertEquals(1, stickers.size)
        assertEquals("c3", stickers[0].id)
    }
}
