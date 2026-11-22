package com.aminu.photodream

import java.net.HttpURLConnection
import java.net.URL

/**
 * Plays a YouTube playlist or a channel's uploads using YouTube's own embedded player.
 * Public and unlisted videos play; private videos can't be played outside YouTube itself.
 */
object YouTube {

    private val PLAYLIST_PARAM = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
    private val CHANNEL_ID = Regex("""(UC[A-Za-z0-9_-]{22})""")
    private val BARE_PLAYLIST = Regex("""^(PL|UU|OL|FL|LL)[A-Za-z0-9_-]{10,}$""")

    /**
     * Turns what the user typed into a playlist ID. Accepts:
     *  - a playlist link (…?list=PL…)            -> that playlist
     *  - a channel link (/channel/UC…) or UC… ID  -> the channel's uploads
     *  - @handle or youtube.com/@handle           -> looked up online, then the channel's uploads
     * Runs on a background thread. Returns null if nothing usable was found.
     */
    fun resolvePlaylistId(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        PLAYLIST_PARAM.find(text)?.let { return it.groupValues[1] }
        if (BARE_PLAYLIST.matches(text)) return text
        if (text.contains("/channel/") || text.startsWith("UC")) {
            CHANNEL_ID.find(text)?.let { return uploadsOf(it.groupValues[1]) }
        }
        val pageUrl = when {
            text.startsWith("@") -> "https://www.youtube.com/$text"
            text.contains("youtube.com/", ignoreCase = true) ->
                if (text.startsWith("http")) text else "https://$text"
            else -> "https://www.youtube.com/@$text"
        }
        val html = fetch(pageUrl) ?: return null
        val canonical = Regex("""youtube\.com/channel/(UC[A-Za-z0-9_-]{22})""").find(html)
            ?: Regex(""""(?:externalId|channelId|browseId)":"(UC[A-Za-z0-9_-]{22})"""").find(html)
            ?: return null
        return uploadsOf(canonical.groupValues[1])
    }

    /** A channel's "uploads" playlist is its channel ID with UC swapped for UU. */
    private fun uploadsOf(channelId: String) = "UU" + channelId.substring(2)

    private fun fetch(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        conn.setRequestProperty("Accept-Language", "en-GB,en;q=0.8")
        // Skips the EU/UK cookie-consent interstitial so the channel page itself comes back.
        conn.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
        try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** The page loaded into the WebView. `Android` is the bridge back to the app. */
    fun playerHtml(playlistId: String, shuffle: Boolean, mute: Boolean, origin: String): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
</head><body>
<div id="p"></div>
<script>
var player, errors = 0;
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(tag);
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {
    width: '100%', height: '100%',
    playerVars: {
      listType: 'playlist', list: '$playlistId',
      autoplay: 1, controls: 0, rel: 0, playsinline: 1, iv_load_policy: 3,
      mute: ${if (mute) 1 else 0}, origin: '$origin'
    },
    events: {
      onReady: function (e) {
        e.target.setLoop(true);
        if ($mute) { e.target.mute(); } else { e.target.unMute(); e.target.setVolume(100); }
        if ($shuffle) { e.target.setShuffle(true); e.target.playVideoAt(0); } else { e.target.playVideo(); }
      },
      onStateChange: function (e) {
        if (e.data == YT.PlayerState.PLAYING) { errors = 0; if (window.Android) Android.playing(); }
      },
      onError: function (e) {
        errors++;
        if (errors > 25) { if (window.Android) Android.failed(e.data); return; }
        setTimeout(function () { player.nextVideo(); }, 1500);   // skip videos that won't play
      }
    }
  });
}
function pd(cmd) {
  if (!player) return;
  if (cmd == 'next') player.nextVideo();
  else if (cmd == 'prev') player.previousVideo();
  else if (cmd == 'pause') player.pauseVideo();
  else if (cmd == 'play') player.playVideo();
}
</script>
</body></html>
""".trimIndent()
}
