package com.aminu.photodream

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Reads when and where a photo or video was taken, and turns it into a caption such as
 *   14 March 2024
 *   📍 Leeds, United Kingdom
 * Each line only appears when the file records that information.
 * Runs on a background thread (place-name lookups use the internet).
 */
class MediaDetails(context: Context) {

    private class Raw(val taken: Date?, val lat: Double?, val lng: Double?)

    private val ctx = context.applicationContext
    /** Place names saved between runs, keyed by rounded coordinates, so each place is looked up once. */
    private val placeCache = ctx.getSharedPreferences("geo_cache", Context.MODE_PRIVATE)
    /** What was read from each file this session. */
    private val memo = HashMap<String, Raw>()

    fun caption(item: MediaItem, wantDate: Boolean, wantPlace: Boolean): String? {
        if (!wantDate && !wantPlace) return null
        val raw = memo.getOrPut(item.uri.toString()) { read(item) }
        val lines = ArrayList<String>()
        if (wantDate && raw.taken != null) {
            lines.add(SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(raw.taken))
        }
        if (wantPlace && raw.lat != null && raw.lng != null) {
            lines.add("📍  " + (placeName(raw.lat, raw.lng) ?: formatCoords(raw.lat, raw.lng)))
        }
        return if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    // ---------- reading the file ----------

    private fun read(item: MediaItem): Raw = try {
        if (item.isVideo) readVideo(item) else readPhoto(item)
    } catch (e: Exception) {
        Raw(null, null, null)
    }

    private fun readPhoto(item: MediaItem): Raw {
        val remote = item.remotePath
        if (remote != null) {
            return NetworkSource.openStream(remote).use { fromExif(ExifInterface(it)) }
        }
        for (candidate in listOf(original(item.uri), item.uri).distinct()) {
            try {
                ctx.contentResolver.openFileDescriptor(candidate, "r")?.use { pfd ->
                    return fromExif(ExifInterface(pfd.fileDescriptor))
                }
            } catch (e: Exception) {
                // e.g. no permission for the original - try the next candidate
            }
        }
        return Raw(null, null, null)
    }

    private fun fromExif(exif: ExifInterface): Raw {
        val out = FloatArray(2)
        val ll = if (exif.getLatLong(out)) valid(out[0].toDouble(), out[1].toDouble()) else null
        val dateText = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        return Raw(parseDate(dateText, "yyyy:MM:dd HH:mm:ss", null), ll?.first, ll?.second)
    }

    private fun readVideo(item: MediaItem): Raw {
        val candidates = if (item.remotePath != null) listOf(item.uri)
        else listOf(original(item.uri), item.uri).distinct()
        for (candidate in candidates) {
            val retriever = MediaMetadataRetriever()
            try {
                if (candidate.scheme == "http") {
                    retriever.setDataSource(candidate.toString(), HashMap())
                } else {
                    retriever.setDataSource(ctx, candidate)
                }
                // ISO 6709 format, e.g. "+53.7997-001.5492/"
                val ll = parseIso6709(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
                // Usually "20240314T101530.000Z" (UTC)
                val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.take(15)
                    ?.let { parseDate(it, "yyyyMMdd'T'HHmmss", TimeZone.getTimeZone("UTC")) }
                return Raw(date, ll?.first, ll?.second)
            } catch (e: Exception) {
                // try the next candidate
            } finally {
                try { retriever.release() } catch (e: Exception) { }
            }
        }
        return Raw(null, null, null)
    }

    /**
     * On Android 10+ the media library hides GPS data unless the app asks for the
     * original file (needs the "media location" permission). Folder-picker files aren't affected.
     */
    private fun original(uri: Uri): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.authority == MediaStore.AUTHORITY)
            MediaStore.setRequireOriginal(uri) else uri

    /** Parses a date and rejects obviously wrong ones (cameras with unset clocks, 1904 video dates). */
    private fun parseDate(text: String?, pattern: String, zone: TimeZone?): Date? {
        if (text.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat(pattern, Locale.US)
            fmt.isLenient = false
            if (zone != null) fmt.timeZone = zone
            val date = fmt.parse(text.trim()) ?: return null
            val year = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
            val thisYear = Calendar.getInstance().get(Calendar.YEAR)
            if (year < 1971 || year > thisYear + 1) null else date
        } catch (e: Exception) {
            null
        }
    }

    private fun parseIso6709(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(raw) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        return valid(lat, lng)
    }

    /** Rejects missing/blanked-out locations (0,0) and nonsense values. */
    private fun valid(lat: Double, lng: Double): Pair<Double, Double>? =
        if (abs(lat) > 90 || abs(lng) > 180 || (abs(lat) < 0.0001 && abs(lng) < 0.0001)) null
        else lat to lng

    // ---------- turning coordinates into a place name ----------

    private fun placeName(lat: Double, lng: Double): String? {
        val key = String.format(Locale.US, "%.3f,%.3f", lat, lng)   // ~100 m grid
        placeCache.getString(key, null)?.let { return it }
        if (!Geocoder.isPresent()) return null
        val name = try {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lng, 1)
                ?.firstOrNull()?.let { format(it) }
        } catch (e: Exception) {
            null   // offline or lookup failed - fall back to coordinates this time
        }
        if (name != null) placeCache.edit().putString(key, name).apply()
        return name
    }

    private fun format(a: Address): String? {
        val local = a.locality ?: a.subLocality ?: a.subAdminArea ?: a.adminArea
        return listOfNotNull(local, a.countryName).distinct().joinToString(", ").ifEmpty { null }
    }

    private fun formatCoords(lat: Double, lng: Double) = String.format(
        Locale.US, "%.4f° %s, %.4f° %s",
        abs(lat), if (lat >= 0) "N" else "S", abs(lng), if (lng >= 0) "E" else "W"
    )
}
