package com.stick.app.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Quick-export helpers for getting finished stickers into Telegram.
 *
 * Everything goes through the system share sheet / explicit Telegram intents, so
 * no Telegram SDK or login is required. A [FileProvider] (declared in the
 * manifest via `androidx.core`) grants Telegram temporary read access to the file.
 */
object TelegramExporter {

    private const val TELEGRAM_PACKAGE = "org.telegram.messenger"
    private const val STICKERS_BOT_URL = "https://t.me/stickers"

    /** Share one or more exported files, preferring Telegram if installed. */
    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { it.toContentUri(context) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isTelegramInstalled(context)) setPackage(TELEGRAM_PACKAGE)
        }
        context.startActivity(Intent.createChooser(intent, "Send to Telegram"))
    }

    /** Open @Stickers, the bot used to build custom Telegram sticker packs. */
    fun openStickerBot(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STICKERS_BOT_URL)).apply {
            if (isTelegramInstalled(context)) setPackage(TELEGRAM_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isTelegramInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TELEGRAM_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun File.toContentUri(context: Context): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)
}
