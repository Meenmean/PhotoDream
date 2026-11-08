package com.aminu.photodream

import android.service.dreams.DreamService

/**
 * The screensaver. Android starts this automatically when the device is idle,
 * once "Photo Dream" is chosen as the screensaver (see README).
 * Any key press or touch wakes the device and closes it.
 */
class SlideshowDreamService : DreamService() {

    private var slideshow: SlideshowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = SlideshowView(this)
        slideshow = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        slideshow?.start()
    }

    override fun onDreamingStopped() {
        slideshow?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        slideshow?.release()
        slideshow = null
        super.onDetachedFromWindow()
    }
}
