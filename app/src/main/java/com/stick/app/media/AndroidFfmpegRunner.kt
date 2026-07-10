package com.stick.app.media

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.stick.core.result.StickError
import com.stick.core.result.StickResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [FfmpegRunner] backed by the bundled FFmpeg native library.
 *
 * Runs the argument list produced by [FfmpegFrameFormatConverter.buildArgs]
 * synchronously on the IO dispatcher and maps the session return code to a
 * [StickResult]. This is the piece that makes GIF / animated WebP / APNG export
 * actually work; everything above it is unchanged.
 */
class AndroidFfmpegRunner : FfmpegRunner {

    override suspend fun run(
        args: List<String>,
        onProgress: (Float) -> Unit,
    ): StickResult<Unit> = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        val returnCode = session.returnCode
        if (ReturnCode.isSuccess(returnCode)) {
            onProgress(1f)
            StickResult.Success(Unit)
        } else {
            StickResult.Failure(
                StickError.Conversion(
                    "FFmpeg failed (code ${returnCode?.value}): " +
                        (session.failStackTrace ?: session.allLogsAsString?.takeLast(500) ?: "unknown"),
                ),
            )
        }
    }
}
