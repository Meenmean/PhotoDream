package com.aminu.photodream

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.VideoView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val FADE_MS = 1500L

/**
 * The slideshow engine. Used by both the full-screen activity and the screensaver.
 * Two ImageViews cross-fade between photos; a VideoView plays videos underneath.
 */
class SlideshowView(context: Context) : FrameLayout(context) {

    private val prefs = Prefs(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val geoWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val locator = PhotoLocator(context)
    private val place = TextView(context)

    private val video = VideoView(context)
    private val imageA = makeImageView()
    private val imageB = makeImageView()
    private var front: ImageView = imageA
    private val message = TextView(context)
    private val clockBox = LinearLayout(context)

    private var items: List<MediaItem> = emptyList()
    private var index = -1
    private var running = false
    private var paused = false
    private var token = 0          // bumps on every change so stale background work is ignored
    private var failures = 0
    private var zoom: ObjectAnimator? = null

    private val advance = Runnable { next() }
    private val intervalMs get() = prefs.intervalSec.coerceAtLeast(3) * 1000L

    init {
        setBackgroundColor(Color.BLACK)
        video.visibility = View.GONE
        addView(video, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        addView(imageA, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(imageB, FrameLayout.LayoutParams(MATCH, MATCH))

        message.setTextColor(Color.WHITE)
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        message.gravity = Gravity.CENTER
        message.setPadding(dp(32), dp(32), dp(32), dp(32))
        message.setShadowLayer(8f, 0f, 0f, Color.BLACK)
        addView(message, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))

        buildClock()
        buildPlaceLabel()
    }

    // ---------- public controls ----------

    fun start() {
        if (running) return
        running = true
        paused = false
        val scale = if (prefs.fillScreen) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        imageA.scaleType = scale
        imageB.scaleType = scale
        clockBox.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        showMessage("Loading your photos…")

        val t = ++token
        worker.execute {
            var error: String? = null
            val found = try {
                MediaScanner.scan(context, prefs)
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                emptyList()
            }
            main.post {
                if (!running || t != token) return@post
                items = found
                index = -1
                failures = 0
                when {
                    error != null -> showMessage("Couldn't read your photos:\n$error\n\nOpen Photo Dream and choose the folder again.")
                    items.isEmpty() -> showMessage("No photos or videos found.\nOpen Photo Dream and choose a folder.")
                    else -> { showMessage(null); next() }
                }
            }
        }
    }

    fun stop() {
        running = false
        token++
        main.removeCallbacksAndMessages(null)
        zoom?.cancel()
        stopVideo()
    }

    fun release() {
        stop()
        worker.shutdownNow()
        geoWorker.shutdownNow()
        imageA.setImageDrawable(null)
        imageB.setImageDrawable(null)
    }

    /** +1 = next item, -1 = previous item. */
    fun skip(delta: Int) {
        if (!running || items.isEmpty()) return
        if (paused) { paused = false; showMessage(null) }
        if (delta < 0) index = (index - 2 + items.size * 2) % items.size
        next()
    }

    fun togglePause() {
        if (!running || items.isEmpty()) return
        paused = !paused
        if (paused) {
            main.removeCallbacks(advance)
            if (video.isPlaying) video.pause()
            zoom?.pause()
            showMessage("Paused")
        } else {
            showMessage(null)
            zoom?.resume()
            if (video.visibility == View.VISIBLE) video.start() else main.postDelayed(advance, intervalMs)
        }
    }

    // ---------- slideshow loop ----------

    private fun next() {
        main.removeCallbacks(advance)
        if (!running || paused || items.isEmpty()) return
        index++
        if (index >= items.size) {
            index = 0
            if (prefs.shuffle) items = items.shuffled()
        }
        val item = items[index]
        if (item.isVideo) playVideo(item) else loadImage(item)
    }

    private fun loadImage(item: MediaItem) {
        val t = ++token
        val w = if (width > 0) width else resources.displayMetrics.widthPixels
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        worker.execute {
            val bmp = decode(item.uri, w, h)
            main.post {
                if (!running || t != token) return@post
                if (bmp == null) onFailure() else { failures = 0; showImage(bmp); updateLocation(item) }
            }
        }
    }

    private fun showImage(bmp: Bitmap) {
        val incoming = if (front === imageA) imageB else imageA
        val outgoing = front
        zoom?.cancel()
        incoming.animate().cancel()
        outgoing.animate().cancel()

        incoming.setImageBitmap(bmp)
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(FADE_MS).start()
        outgoing.animate().alpha(0f).setDuration(FADE_MS)
            .withEndAction { outgoing.setImageDrawable(null) }.start()
        front = incoming

        if (prefs.kenBurns) {
            zoom = ObjectAnimator.ofPropertyValuesHolder(
                incoming,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f)
            ).apply { duration = intervalMs + FADE_MS; start() }
        }

        if (video.visibility == View.VISIBLE) {
            val t = token
            main.postDelayed({ if (t == token) stopVideo() }, FADE_MS)
        }
        main.postDelayed(advance, intervalMs)
    }

    private fun playVideo(item: MediaItem) {
        val t = ++token
        zoom?.cancel()
        video.visibility = View.VISIBLE
        video.setOnPreparedListener { player ->
            if (!running || t != token) return@setOnPreparedListener
            failures = 0
            if (prefs.muteVideos) player.setVolume(0f, 0f)
            video.start()
            front.animate().cancel()
            front.animate().alpha(0f).setDuration(FADE_MS).start()
            val max = prefs.maxVideoSec
            if (max > 0) main.postDelayed(advance, max * 1000L)
        }
        video.setOnCompletionListener { if (running && t == token) next() }
        video.setOnErrorListener { _, _, _ ->
            if (running && t == token) onFailure()
            true
        }
        video.setVideoURI(item.uri)
        updateLocation(item)
    }

    private fun stopVideo() {
        video.stopPlayback()
        video.visibility = View.GONE
    }

    private fun onFailure() {
        failures++
        if (failures >= items.size) {
            showMessage("Couldn't open any of the files in this folder.")
        } else {
            main.post(advance)   // skip the bad file
        }
    }

    // ---------- helpers ----------

    private fun decode(uri: Uri, reqW: Int, reqH: Int): Bitmap? = try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Handles HEIC and phone-photo rotation automatically
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, reqW, reqH))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Shrinks huge photos to roughly screen size so memory stays low. */
    private fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var s = 1
        while (w / (s * 2) >= reqW && h / (s * 2) >= reqH) s *= 2
        return s
    }

    private fun showMessage(text: String?) {
        message.text = text ?: ""
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    /** Fades out the old caption, then looks up and fades in this item's location (if it has one). */
    private fun updateLocation(item: MediaItem) {
        place.animate().cancel()
        place.animate().alpha(0f).setDuration(400).start()
        if (!prefs.showLocation) return
        val t = token
        geoWorker.execute {
            val text = locator.describe(item)
            main.post {
                if (!running || t != token || text == null) return@post
                place.text = "📍  $text"
                place.animate().cancel()
                place.animate().alpha(1f).setDuration(FADE_MS).start()
            }
        }
    }

    private fun buildPlaceLabel() {
        place.setTextColor(Color.WHITE)
        place.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        place.setShadowLayer(10f, 0f, 0f, Color.BLACK)
        place.maxLines = 2
        place.alpha = 0f
        // Bottom-left, opposite the clock; leave room so long names don't run under it.
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START)
        val m = dp(40)
        lp.setMargins(m, m, dp(260), m)
        addView(place, lp)
    }

    private fun buildClock() {
        clockBox.orientation = LinearLayout.VERTICAL
        clockBox.gravity = Gravity.END
        val time = TextClock(context).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
        val date = TextClock(context).apply {
            format12Hour = "EEEE d MMMM"
            format24Hour = "EEEE d MMMM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
        clockBox.addView(time)
        clockBox.addView(date)
        val lp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        val m = dp(40)
        lp.setMargins(m, m, m, m)
        addView(clockBox, lp)
    }

    private fun makeImageView() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        alpha = 0f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
