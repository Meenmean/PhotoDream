package com.aminu.photodream

import android.content.Context

/** All user settings, stored in SharedPreferences and shared by the app and the screensaver. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("photo_dream", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun setBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun setInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    /** SOURCE_FOLDER = a folder picked with the system picker; SOURCE_LIBRARY = media library search. */
    var source: Int
        get() = int("source", SOURCE_FOLDER)
        set(v) = setInt("source", v)

    /** Persisted URI of the picked folder (SOURCE_FOLDER mode). */
    var treeUri: String?
        get() = sp.getString("tree_uri", null)
        set(v) = sp.edit().putString("tree_uri", v).apply()

    /** Only show media whose folder path contains this text (SOURCE_LIBRARY mode). Blank = everything. */
    var folderFilter: String
        get() = sp.getString("folder_filter", "") ?: ""
        set(v) = sp.edit().putString("folder_filter", v).apply()

    var intervalSec: Int
        get() = int("interval", 10)
        set(v) = setInt("interval", v)

    /** 0 = play each video to the end. */
    var maxVideoSec: Int
        get() = int("max_video", 0)
        set(v) = setInt("max_video", v)

    var shuffle: Boolean
        get() = bool("shuffle", true)
        set(v) = setBool("shuffle", v)

    var includeVideos: Boolean
        get() = bool("videos", true)
        set(v) = setBool("videos", v)

    var muteVideos: Boolean
        get() = bool("mute", true)
        set(v) = setBool("mute", v)

    var showClock: Boolean
        get() = bool("clock", true)
        set(v) = setBool("clock", v)

    /** Show where each photo/video was taken, when the file contains GPS data. */
    var showLocation: Boolean
        get() = bool("location", true)
        set(v) = setBool("location", v)

    /** Slow zoom on photos ("Ken Burns" effect). */
    var kenBurns: Boolean
        get() = bool("ken_burns", true)
        set(v) = setBool("ken_burns", v)

    /** true = crop to fill the screen; false = show the whole photo with black bars. */
    var fillScreen: Boolean
        get() = bool("fill", false)
        set(v) = setBool("fill", v)

    companion object {
        const val SOURCE_FOLDER = 0
        const val SOURCE_LIBRARY = 1
    }
}
