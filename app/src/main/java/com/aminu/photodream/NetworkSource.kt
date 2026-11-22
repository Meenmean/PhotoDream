package com.aminu.photodream

import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import com.hierynomus.smbj.share.File as SmbFile

/**
 * Reads photos and videos from a shared folder on a Windows PC, Mac or NAS (SMB / "network share").
 * Everything here runs on background threads.
 */
object NetworkSource {

    /** Parsed from settings, e.g. \\MYPC\Photos\Slideshow -> host MYPC, share Photos, folder Slideshow. */
    data class Target(
        val host: String,
        val share: String,
        val folder: String,
        val user: String,
        val password: String,
        val domain: String
    )

    private const val MAX_FILES = 20000
    private const val MAX_PHOTO_BYTES = 80L * 1024 * 1024

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var disk: DiskShare? = null
    private var current: Target? = null

    /** Accepts \\PC\Share\Folder, //PC/Share/Folder, smb://PC/Share/Folder or PC\Share\Folder. */
    fun parse(prefs: Prefs): Target? {
        var path = prefs.netPath.trim()
        if (path.startsWith("smb://", ignoreCase = true)) path = path.substring(6)
        val parts = path.replace('/', '\\').split('\\').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        var user = prefs.netUser.trim()
        var domain = ""
        if (user.contains('\\')) {
            domain = user.substringBefore('\\')
            user = user.substringAfter('\\')
        }
        return Target(
            host = parts[0],
            share = parts[1],
            folder = parts.drop(2).joinToString("\\"),
            user = user,
            password = prefs.netPassword,
            domain = domain
        )
    }

    /** Lists every photo/video in the folder and its sub-folders. */
    fun scan(prefs: Prefs): List<MediaItem> {
        val target = parse(prefs)
            ?: throw IllegalStateException("No network folder set. Enter it like \\\\MYPC\\Photos")
        val share = share(target)
        val out = ArrayList<MediaItem>()
        val pending = ArrayDeque<String>()
        pending.add(target.folder)
        while (pending.isNotEmpty() && out.size < MAX_FILES) {
            val dir = pending.removeLast()
            for (info in share.list(dir)) {
                val name = info.fileName ?: continue
                if (name == "." || name == ".." || name.startsWith(".") || name.startsWith("$")) continue
                val attrs = info.fileAttributes
                if (attrs and FileAttributes.FILE_ATTRIBUTE_HIDDEN.value != 0L) continue
                if (attrs and FileAttributes.FILE_ATTRIBUTE_SYSTEM.value != 0L) continue
                val full = if (dir.isEmpty()) name else "$dir\\$name"
                if (attrs and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L) {
                    pending.add(full)
                    continue
                }
                val isVideo = MediaScanner.classify(name, "") ?: continue
                if (isVideo && !prefs.includeVideos) continue
                out.add(MediaItem(Uri.parse(StreamServer.url(full, name)), isVideo, name, full))
            }
        }
        return out
    }

    /** Used by the "Test connection" button. */
    fun test(prefs: Prefs): String = try {
        close()
        val items = scan(prefs)
        val videos = items.count { it.isVideo }
        "Connected. Found ${items.size - videos} photos and $videos videos."
    } catch (e: Exception) {
        "Couldn't connect: " + friendly(e)
    }

    fun friendly(e: Throwable): String {
        val msg = (e.message ?: e.javaClass.simpleName)
        return when {
            msg.contains("STATUS_LOGON_FAILURE") -> "wrong username or password."
            msg.contains("STATUS_BAD_NETWORK_NAME") -> "the PC was found, but not that shared folder name."
            msg.contains("STATUS_OBJECT_NAME_NOT_FOUND") || msg.contains("STATUS_OBJECT_PATH_NOT_FOUND") ->
                "the shared folder was found, but not that sub-folder."
            msg.contains("STATUS_ACCESS_DENIED") -> "access denied. Check the folder's sharing permissions."
            e is java.net.UnknownHostException -> "couldn't find that PC on the network. Try its IP address instead of its name."
            e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
                "the PC didn't answer. Check it's switched on and file sharing is enabled."
            else -> msg
        }
    }

    /** Whole file in memory (photos only - they are a few MB). Null if too big. */
    fun readAll(path: String): ByteArray? {
        val f = openFile(path)
        try {
            if (size(f) > MAX_PHOTO_BYTES) return null
            return f.inputStream.use { it.readBytes() }
        } finally {
            closeQuietly(f)
        }
    }

    /** A stream that also closes the remote file when closed. */
    fun openStream(path: String): InputStream {
        val f = openFile(path)
        return object : FilterInputStream(f.inputStream) {
            override fun close() {
                try { super.close() } finally { closeQuietly(f) }
            }
        }
    }

    fun openFile(path: String): SmbFile {
        val target = current ?: throw IllegalStateException("Network folder not connected")
        return share(target).openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    fun size(f: SmbFile): Long = f.length

    /** Connects (or reuses the connection) and returns the share. */
    @Synchronized
    private fun share(target: Target): DiskShare {
        val existing = disk
        if (existing != null && target == current && existing.isConnected) return existing
        close()
        val config = SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())   // Android has no MD4, needed for passwords
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()
        val c = SMBClient(config)
        client = c
        val conn = c.connect(target.host)
        connection = conn
        val auth = if (target.user.isEmpty()) AuthenticationContext.guest()
        else AuthenticationContext(target.user, target.password.toCharArray(), target.domain.ifEmpty { null })
        val s = conn.authenticate(auth)
        session = s
        val share = s.connectShare(target.share) as? DiskShare
            ?: throw IllegalStateException("'${target.share}' is not a shared folder")
        disk = share
        current = target
        return share
    }

    @Synchronized
    fun close() {
        closeQuietly(disk)
        closeQuietly(session)
        closeQuietly(connection)
        closeQuietly(client)
        disk = null; session = null; connection = null; client = null; current = null
    }

    private fun closeQuietly(c: AutoCloseable?) {
        try { c?.close() } catch (e: Exception) { }
    }
}

/**
 * A tiny web server on 127.0.0.1 (only reachable from this device) that streams videos from
 * the network share, so Android's normal video player can play them with seeking.
 */
object StreamServer {

    private var server: ServerSocket? = null

    @Synchronized
    private fun port(): Int {
        server?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        server = s
        Thread({ acceptLoop(s) }, "PhotoDreamStream").apply { isDaemon = true }.start()
        return s.localPort
    }

    fun url(path: String, name: String): String =
        "http://127.0.0.1:${port()}/media/" + enc(name) + "?p=" + enc(path)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val sock = try { s.accept() } catch (e: Exception) { break }
            Thread({ handle(sock) }, "PhotoDreamStreamReq").apply { isDaemon = true }.start()
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 60000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            var range: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(":").trim()
            }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: return
            val target = parts.getOrNull(1) ?: return
            val path = Uri.parse("http://localhost$target").getQueryParameter("p")
            val out = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)
            if (path == null) {
                status(out, "404 Not Found", "Content-Length: 0\r\n")
                return
            }
            val f = NetworkSource.openFile(path)
            try {
                val size = NetworkSource.size(f)
                var start = 0L
                var end = size - 1
                val m = range?.let { Regex("""bytes=(\d*)-(\d*)""").find(it) }
                if (m != null) {
                    val a = m.groupValues[1]
                    val b = m.groupValues[2]
                    if (a.isEmpty() && b.isNotEmpty()) {            // last N bytes
                        start = (size - b.toLong()).coerceAtLeast(0)
                    } else {
                        if (a.isNotEmpty()) start = a.toLong()
                        if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                    }
                }
                if (start >= size || start > end) {
                    status(out, "416 Range Not Satisfiable", "Content-Range: bytes */$size\r\nContent-Length: 0\r\n")
                    return
                }
                val length = end - start + 1
                val headers = StringBuilder()
                    .append("Content-Type: ").append(mime(path)).append("\r\n")
                    .append("Content-Length: ").append(length).append("\r\n")
                    .append("Accept-Ranges: bytes\r\n")
                if (m != null) headers.append("Content-Range: bytes $start-$end/$size\r\n")
                status(out, if (m != null) "206 Partial Content" else "200 OK", headers.toString())
                if (method.equals("HEAD", ignoreCase = true)) return

                val buf = ByteArray(256 * 1024)
                var pos = start
                while (pos <= end) {
                    val want = minOf(buf.size.toLong(), end - pos + 1).toInt()
                    val n = f.read(buf, pos, 0, want)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    pos += n
                }
                out.flush()
            } finally {
                try { f.close() } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            // Player closed the connection early (normal when seeking) or the share dropped.
        } finally {
            try { sock.close() } catch (e: Exception) { }
        }
    }

    private fun status(out: OutputStream, status: String, headers: String) {
        out.write("HTTP/1.1 $status\r\n${headers}Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "mov" -> "video/quicktime"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic", "heif" -> "image/heif"
        else -> "application/octet-stream"
    }
}
