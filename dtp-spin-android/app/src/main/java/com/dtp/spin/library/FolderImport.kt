package com.dtp.spin.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FolderImport"

/** Audio extensions accepted when a folder is scanned (mime types vary by provider). */
private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "m4a", "aac", "ogg", "oga", "opus", "flac",
    "mid", "midi", "xmf", "rtttl", "rtx", "ota", "imy",
)

/**
 * Pure filter for folder scans: true when the provider's mime type says audio,
 * or the file extension looks like audio. Kept free of framework calls so it is
 * unit-testable on plain JUnit.
 */
fun isAudioFile(displayName: String?, mimeType: String?): Boolean {
    if (mimeType?.startsWith("audio/") == true) return true
    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) return false
    val ext = displayName?.substringAfterLast('.', "")?.lowercase() ?: ""
    return ext in AUDIO_EXTENSIONS
}

/**
 * Pure ancestor check for persisted tree permissions: true when [docId] is the
 * tree root itself or a descendant (e.g. tree "primary:Music", doc
 * "primary:Music/club/set.mp3").
 */
fun isUnderTree(treeId: String, docId: String): Boolean {
    if (docId == treeId) return true
    return docId.startsWith(treeId.removeSuffix("/") + "/")
}

/** Persist read access to a tree picked via OpenDocumentTree. No-op on failure. */
fun persistTreePermission(context: Context, treeUri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        Log.w(TAG, "persist tree permission failed for $treeUri", e)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "persist tree permission failed for $treeUri", e)
    }
}

/**
 * True when [uriString] is directly persisted or lives under a persisted tree
 * (folder imports persist the tree, not each child).
 */
fun hasPersistedRead(context: Context, uriString: String): Boolean {
    val persisted = context.contentResolver.persistedUriPermissions
    if (persisted.any { it.uri.toString() == uriString && it.isReadPermission }) return true
    val docId = try {
        DocumentsContract.getDocumentId(Uri.parse(uriString))
    } catch (_: Exception) {
        return false
    } ?: return false
    return persisted.any { perm ->
        if (!perm.isReadPermission) return@any false
        try {
            val treeId = DocumentsContract.getTreeDocumentId(perm.uri)
            isUnderTree(treeId, docId)
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Recursively list audio file document URIs under [treeUri] (IO dispatcher).
 * Non-audio files are skipped; unreadable subfolders are skipped, never fatal.
 */
suspend fun listAudioUris(context: Context, treeUri: Uri): List<Uri> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<Uri>()
        val treeId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            Log.w(TAG, "not a tree uri: $treeUri", e)
            return@withContext out
        }
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pending.add(treeId)
        val resolver = context.contentResolver
        while (pending.isNotEmpty()) {
            val dirId = pending.removeFirst()
            if (!visited.add(dirId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId)
            val rows = try {
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    null, null, null,
                )
            } catch (e: Exception) {
                Log.w(TAG, "list failed for $dirId", e)
                null
            } ?: continue
            rows.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val docId = if (idIdx >= 0) c.getString(idIdx) else null ?: continue
                    val mime = if (mimeIdx >= 0) c.getString(mimeIdx) else null
                    val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(docId)
                    } else if (isAudioFile(name, mime)) {
                        try {
                            out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                        } catch (e: Exception) {
                            Log.w(TAG, "bad document id $docId", e)
                        }
                    }
                }
            }
            if (out.size >= MAX_FOLDER_FILES) {
                Log.w(TAG, "folder scan capped at $MAX_FOLDER_FILES files")
                break
            }
        }
        out
    }

private const val MAX_FOLDER_FILES = 2_000
