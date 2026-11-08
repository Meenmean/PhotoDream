package com.aminu.photodream

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen: choose where the photos are, tune the slideshow, start it, set as screensaver. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var folderLabel: TextView
    private lateinit var folderSection: View
    private lateinit var librarySection: View
    private lateinit var filterInput: EditText
    private var startAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        folderLabel = findViewById(R.id.folder_label)
        folderSection = findViewById(R.id.folder_section)
        librarySection = findViewById(R.id.library_section)
        filterInput = findViewById(R.id.filter_input)
        filterInput.setText(prefs.folderFilter)

        val group = findViewById<RadioGroup>(R.id.source_group)
        group.check(if (prefs.source == Prefs.SOURCE_FOLDER) R.id.radio_folder else R.id.radio_library)
        group.setOnCheckedChangeListener { _, id ->
            prefs.source = if (id == R.id.radio_folder) Prefs.SOURCE_FOLDER else Prefs.SOURCE_LIBRARY
            updateSourceUi()
            if (prefs.source == Prefs.SOURCE_LIBRARY) requestMediaPermission()
        }

        findViewById<Button>(R.id.choose_folder).setOnClickListener { pickFolder() }

        bindSwitch(R.id.switch_shuffle, prefs.shuffle) { prefs.shuffle = it }
        bindSwitch(R.id.switch_videos, prefs.includeVideos) { prefs.includeVideos = it }
        bindSwitch(R.id.switch_mute, prefs.muteVideos) { prefs.muteVideos = it }
        bindSwitch(R.id.switch_clock, prefs.showClock) { prefs.showClock = it }
        bindSwitch(R.id.switch_location, prefs.showLocation) { prefs.showLocation = it }
        bindSwitch(R.id.switch_zoom, prefs.kenBurns) { prefs.kenBurns = it }
        bindSwitch(R.id.switch_fill, prefs.fillScreen) { prefs.fillScreen = it }

        bindSeek(R.id.seek_interval, R.id.label_interval, 3, 60, prefs.intervalSec,
            { "Show each photo for: $it seconds" }) { prefs.intervalSec = it }
        bindSeek(R.id.seek_video, R.id.label_video, 0, 120, prefs.maxVideoSec,
            { if (it == 0) "Video length: play to the end" else "Video length: stop after $it seconds" }
        ) { prefs.maxVideoSec = it }

        findViewById<Button>(R.id.start_button).setOnClickListener { startSlideshow() }
        findViewById<Button>(R.id.screensaver_button).setOnClickListener { openScreensaverSettings() }

        updateSourceUi()
    }

    override fun onPause() {
        prefs.folderFilter = filterInput.text.toString().trim()
        super.onPause()
    }

    // ---------- source ----------

    private fun updateSourceUi() {
        val folderMode = prefs.source == Prefs.SOURCE_FOLDER
        folderSection.visibility = if (folderMode) View.VISIBLE else View.GONE
        librarySection.visibility = if (folderMode) View.GONE else View.VISIBLE
        folderLabel.text = prefs.treeUri?.let { "Folder: " + prettyFolder(Uri.parse(it)) }
            ?: "No folder chosen yet"
    }

    private fun prettyFolder(uri: Uri): String = try {
        val id = DocumentsContract.getTreeDocumentId(uri)
        if (id.startsWith("primary:")) "Internal storage/" + id.removePrefix("primary:")
        else id.replace(":", " / ")
    } catch (e: Exception) {
        uri.toString()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQ_FOLDER)
        } catch (e: ActivityNotFoundException) {
            // Many Android TV boxes have no folder picker - fall back to the media library.
            Toast.makeText(this, "This device has no folder picker. Switching to media library mode.", Toast.LENGTH_LONG).show()
            findViewById<RadioGroup>(R.id.source_group).check(R.id.radio_library)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && uri != null) {
            // Keep access after restarts so the screensaver can read the folder.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.treeUri = uri.toString()
            updateSourceUi()
        }
    }

    // ---------- permissions ----------

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasMediaPermission() = mediaPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Core photo/video access, plus (when captions are on) access to the GPS data in them. */
    private fun missingPermissions(): Array<String> {
        val wanted = mediaPermissions().toMutableList()
        if (prefs.showLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wanted.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        return wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    private fun requestMediaPermission() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing, REQ_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSION) return
        if (hasMediaPermission()) {
            if (startAfterPermission) launchSlideshow()
        } else {
            Toast.makeText(this, "Photo Dream needs access to your photos and videos.", Toast.LENGTH_LONG).show()
        }
        startAfterPermission = false
    }

    // ---------- actions ----------

    private fun startSlideshow() {
        prefs.folderFilter = filterInput.text.toString().trim()
        if (prefs.source == Prefs.SOURCE_FOLDER && prefs.treeUri == null) {
            Toast.makeText(this, "Choose a folder first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (prefs.source == Prefs.SOURCE_LIBRARY && missingPermissions().isNotEmpty()) {
            startAfterPermission = true
            requestMediaPermission()
            return
        }
        launchSlideshow()
    }

    private fun launchSlideshow() = startActivity(Intent(this, SlideshowActivity::class.java))

    private fun openScreensaverSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            showAdbHelp()
        }
    }

    private fun showAdbHelp() {
        AlertDialog.Builder(this)
            .setTitle("Set the screensaver from a computer")
            .setMessage(
                "This device has no screensaver picker (common on Google TV).\n\n" +
                "From a PC with ADB, run:\n\n" +
                "adb shell settings put secure screensaver_components " +
                "com.aminu.photodream/.SlideshowDreamService\n\n" +
                "See the README for the full steps."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- small binding helpers ----------

    @Suppress("DEPRECATION")
    private fun bindSwitch(id: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    private fun bindSeek(
        seekId: Int, labelId: Int, min: Int, max: Int, value: Int,
        label: (Int) -> String, onChange: (Int) -> Unit
    ) {
        val seek = findViewById<SeekBar>(seekId)
        val text = findViewById<TextView>(labelId)
        seek.min = min
        seek.max = max
        seek.progress = value.coerceIn(min, max)
        text.text = label(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                text.text = label(progress)
                onChange(progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }

    companion object {
        private const val REQ_FOLDER = 1
        private const val REQ_PERMISSION = 2
    }
}
