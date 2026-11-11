package com.dtp.spin.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.dtp.spin.audio.TrackDecoder
import com.dtp.spin.data.TrackEntity
import com.dtp.spin.data.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportResult(
    val imported: Int,
    val duplicates: Int,
    val failures: List<Pair<Uri, String>>,
)

/**
 * SAF import pipeline (Phase 2): persist URI permission, validate the file,
 * read metadata without copying music into app storage, insert into Room with
 * duplicate-by-URI detection, and scan waveform peaks in the background.
 */
class TrackImporter(
    private val appContext: Context,
    private val repository: TrackRepository,
    private val backgroundScope: CoroutineScope,
) {
    suspend fun importUris(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        val failures = mutableListOf<Pair<Uri, String>>()

        for (uri in uris) {
            takeReadPermission(appContext, uri)
            val uriString = uri.toString()
            try {
                if (repository.findByUri(uriString) != null) {
                    duplicates++
                    continue
                }
                val entity = readMetadata(uri, uriString)
                    ?: throw IllegalArgumentException("unsupported or unreadable audio file")
                val id = repository.insert(entity)
                if (id < 0) {
                    duplicates++ // raced another import of the same URI
                } else {
                    imported++
                    scanPeaksAsync(id, uri)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "permission lost for $uri", e)
                failures += uri to "permission denied — re-import the file"
            } catch (e: Exception) {
                Log.w(TAG, "import failed for $uri", e)
                failures += uri to (e.message ?: "import failed")
            }
        }
        ImportResult(imported, duplicates, failures)
    }

    private fun readMetadata(uri: Uri, uriString: String): TrackEntity? {
        // Stream parameters come from MediaExtractor (all minSdk-safe).
        // Text tags come from MediaMetadataRetriever (basic keys only).
        val extractor = MediaExtractor()
        val audioIndex: Int
        val format: MediaFormat
        try {
            extractor.setDataSource(appContext, uri, null)
            audioIndex = TrackDecoder.selectAudioTrack(extractor)
            if (audioIndex < 0) {
                extractor.release()
                return null
            }
            format = extractor.getTrackFormat(audioIndex)
        } catch (e: Exception) {
            Log.w(TAG, "metadata failed for $uri", e)
            return null
        }

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        extractor.release()
        val durationMs = durationUs / 1000
        if (durationMs <= 0) return null

        var displayName: String? = null
        var size = -1L
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) displayName = c.getString(nameIdx)
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        if (size == 0L) return null
        val fallback = displayName ?: uri.lastPathSegment ?: "Unknown track"

        var title = fallback
        var artist = "Unknown artist"
        var album = "Unknown album"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
        } catch (e: Exception) {
            Log.w(TAG, "tag read failed for $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return TrackEntity(
            uri = uriString,
            title = title.substringAfterLast('/').substringBeforeLast('.').ifBlank { fallback },
            artist = artist,
            album = album,
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            channels = channels,
        )
    }

    private fun scanPeaksAsync(trackId: Long, uri: Uri) {
        backgroundScope.launch(Dispatchers.IO) {
            val peaks = PeakComputer.compute(appContext, uri)
            if (peaks != null) {
                repository.updatePeaks(trackId, PeakUtils.encode(peaks))
            }
        }
    }

    companion object {
        private const val TAG = "TrackImporter"
    }
}
