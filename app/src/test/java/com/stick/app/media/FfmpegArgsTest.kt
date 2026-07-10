package com.stick.app.media

import com.stick.app.domain.converter.EditOperation
import com.stick.app.domain.converter.EditPipeline
import com.stick.app.domain.converter.ExportOptions
import com.stick.core.model.StickerFormat
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegArgsTest {

    private val converter = FfmpegFrameFormatConverter(FfmpegRunner.Unavailable, LottiePacker.Unavailable)

    @Test
    fun `gif filtergraph includes palette and scale`() {
        val args = converter.buildArgs(
            EditPipeline(sourcePath = "/in.webp"),
            ExportOptions(StickerFormat.GIF, widthPx = 320, heightPx = 320, fps = 24),
            "/out.gif",
        )
        val filter = args[args.indexOf("-vf") + 1]
        assertTrue(filter.contains("fps=24"))
        assertTrue(filter.contains("scale=320:320"))
        assertTrue(filter.contains("palettegen"))
        assertTrue(args.last() == "/out.gif")
    }

    @Test
    fun `speed operation maps to setpts before fps`() {
        val args = converter.buildArgs(
            EditPipeline(sourcePath = "/in.webp", operations = listOf(EditOperation.Speed(2f))),
            ExportOptions(StickerFormat.GIF),
            "/out.gif",
        )
        val filter = args[args.indexOf("-vf") + 1]
        assertTrue(filter.indexOf("setpts") < filter.indexOf("fps="))
    }
}
