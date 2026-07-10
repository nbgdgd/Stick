package com.stick.app.data.repository

import com.stick.app.data.database.dao.CollectionDao
import com.stick.app.data.database.dao.HistoryDao
import com.stick.app.data.database.dao.StickerDao
import com.stick.app.data.database.entity.CollectionEntity
import com.stick.app.data.database.entity.HistoryEntity
import com.stick.app.data.database.entity.StickerEntity
import com.stick.stickersource.model.DownloadedAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Single source of truth for the sticker library. Wraps the DAOs and owns the
 * rules that don't belong to any one DAO: content-hash based de-duplication, file
 * cleanup on delete, and mapping the acquisition module's [DownloadedAsset] into a
 * persisted [StickerEntity].
 *
 * ViewModels depend only on this class, never on Room directly.
 */
class StickerRepository(
    private val stickerDao: StickerDao,
    private val collectionDao: CollectionDao,
    private val historyDao: HistoryDao,
) {
    // ---- Library queries ---------------------------------------------------

    fun observeAll(): Flow<List<StickerEntity>> = stickerDao.observeAll()
    fun observeFavorites(): Flow<List<StickerEntity>> = stickerDao.observeFavorites()
    fun observeByCollection(id: String): Flow<List<StickerEntity>> = stickerDao.observeByCollection(id)
    fun search(query: String): Flow<List<StickerEntity>> = stickerDao.search(query)
    fun observeCollections(): Flow<List<CollectionEntity>> = collectionDao.observeAll()
    fun observeHistory(): Flow<List<HistoryEntity>> = historyDao.observeRecent()

    suspend fun findById(id: String): StickerEntity? = stickerDao.findById(id)

    // ---- Saving ------------------------------------------------------------

    /**
     * Persist a freshly downloaded asset. If an identical file (same content hash)
     * already exists, the existing entry is returned and the new file discarded —
     * so repeated imports never litter the library.
     */
    suspend fun save(asset: DownloadedAsset): StickerEntity = withContext(Dispatchers.IO) {
        val hash = hashFile(asset.localPath)
        stickerDao.findByContentHash(hash)?.let { existing ->
            // Duplicate: drop the redundant copy we just downloaded.
            if (existing.localPath != asset.localPath) File(asset.localPath).delete()
            return@withContext existing
        }

        val entity = asset.toEntity(hash)
        stickerDao.upsert(entity)
        entity
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = stickerDao.setFavorite(id, favorite)

    suspend fun assignToCollection(id: String, collectionId: String?) =
        stickerDao.setCollection(id, collectionId)

    suspend fun createCollection(name: String): CollectionEntity {
        val c = CollectionEntity(id = UUID.randomUUID().toString(), name = name)
        collectionDao.upsert(c)
        return c
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        stickerDao.findById(id)?.let { File(it.localPath).delete() }
        stickerDao.delete(id)
    }

    /** Remove exact duplicates, returning how many rows were removed. */
    suspend fun removeDuplicates(): Int = stickerDao.removeDuplicates()

    suspend fun recordHistory(rawInput: String, canonicalUrl: String, found: Int) =
        historyDao.insert(HistoryEntity(rawInput = rawInput, canonicalUrl = canonicalUrl, stickersFound = found))

    // ---- Helpers -----------------------------------------------------------

    private fun DownloadedAsset.toEntity(hash: String): StickerEntity {
        val s = source
        val origin = s.origin
        val (videoUrl, author) = when (origin) {
            is com.stick.core.model.StickerOrigin.Comment -> origin.videoUrl to origin.authorName
            else -> null to null
        }
        return StickerEntity(
            id = if (s.id.isBlank()) UUID.randomUUID().toString() else "${s.sourceId}:${s.id}",
            name = s.name,
            localPath = localPath,
            format = info.format.name,
            widthPx = info.widthPx,
            heightPx = info.heightPx,
            fps = info.fps,
            durationMs = info.durationMs,
            fileSizeBytes = info.fileSizeBytes,
            sourceId = s.sourceId,
            originVideoUrl = videoUrl,
            originAuthor = author,
            keywords = s.keywords.joinToString(","),
            contentHash = hash,
        )
    }

    private fun hashFile(path: String): String {
        val file = File(path)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
