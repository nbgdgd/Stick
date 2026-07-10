package com.stick.app.domain.converter

import com.stick.core.model.MediaInfo
import com.stick.core.model.StickerFormat
import org.junit.Assert.assertTrue
import org.junit.Test

class SizeEstimatorTest {

    private val info = MediaInfo(
        widthPx = 512, heightPx = 512, fps = 30f, durationMs = 2_000L, frameCount = 60,
    )

    @Test
    fun `estimate is positive and grows with resolution`() {
        val small = SizeEstimator.estimate(info, ExportOptions(StickerFormat.GIF, 128, 128))
        val large = SizeEstimator.estimate(info, ExportOptions(StickerFormat.GIF, 512, 512))
        assertTrue(small > 0)
        assertTrue(large > small)
    }

    @Test
    fun `optimize flag reduces frame-format size`() {
        val on = SizeEstimator.estimate(info, ExportOptions(StickerFormat.WEBP_ANIMATED, optimizeSize = true))
        val off = SizeEstimator.estimate(info, ExportOptions(StickerFormat.WEBP_ANIMATED, optimizeSize = false))
        assertTrue(on < off)
    }

    @Test
    fun `webm sized by bitrate and duration`() {
        val estimate = SizeEstimator.estimate(
            info,
            ExportOptions(StickerFormat.TELEGRAM_WEBM, bitrateKbps = 256),
        )
        // 256 kbps * 2s / 8 = 64000 bytes, order of magnitude check.
        assertTrue(estimate in 32_000..96_000)
    }
}
