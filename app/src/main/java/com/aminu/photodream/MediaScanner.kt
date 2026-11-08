package com.aminu.photodream

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

data class MediaItem(val uri: Uri, val isVideo: Boolean, val name: String)

/** Finds the photos and videos to show. Runs on a background thread. */
object MediaScanner {

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov")

    fun scan(context: Context, prefs: Prefs): List<MediaItem> {
        val tree = prefs.treeUri
        val found = if (prefs.source == Prefs.SOURCE_FOLDER && tree != null) {
            scanFolder(context, Uri.parse(tree), prefs.includeVideos)
        } else {
            scanLibrary(context, prefs.folderFilter.trim(), prefs.includeVideos)
        }
        return if (prefs.shuffle) found.shuffled() else found.sortedBy { it.name.lowercase() }
    }

    /** Walks the picked folder and all its sub-folders. */
    private fun scanFolder(context: Context, treeUri: Uri, videos: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val resolver = context.contentResolver
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        while (pending.isNotEmpty()) {
            val parentId = pending.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val c = resolver.query(children, projection, null, null, null) ?: continue
            try {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(id)
                        continue
                    }
                    if (name.startsWith(".")) continue          // skip hidden files
                    val isVideo = classify(name, mime) ?: continue
                    if (isVideo && !videos) continue
                    out.add(MediaItem(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), isVideo, name))
                }
            } finally {
                c.close()
            }
        }
        return out
    }

    /** Searches the device media library (internal storage, SD cards and USB drives Android has indexed). */
    private fun scanLibrary(context: Context, filter: String, videos: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            pathCol
        )
        val types = if (videos) "(1,3)" else "(1)"   // 1 = image, 3 = video
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN $types"
        val imagesBase = MediaStore.Images.Media.getContentUri("external")
        val videosBase = MediaStore.Video.Media.getContentUri("external")

        val c = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"), projection, selection, null, null
        ) ?: return out
        try {
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: ""
                val isVideo = c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val path = c.getString(3) ?: ""
                if (filter.isNotEmpty() && !path.contains(filter, ignoreCase = true)) continue
                val uri = ContentUris.withAppendedId(if (isVideo) videosBase else imagesBase, id)
                out.add(MediaItem(uri, isVideo, name))
            }
        } finally {
            c.close()
        }
        return out
    }

    /** true = video, false = image, null = not media. */
    private fun classify(name: String, mime: String): Boolean? {
        if (mime.startsWith("image/")) return false
        if (mime.startsWith("video/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in IMAGE_EXT -> false
            in VIDEO_EXT -> true
            else -> null
        }
    }
}
