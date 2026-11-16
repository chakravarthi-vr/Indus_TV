package com.iptv.industv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val groups: List<String>,
    val userAgent: String? = null,
    val referrer: String? = null
)

object Repo {
    // Default source: public iptv-org India playlist. Change in-app via the "Source" button
    // (paste the m3u URL used by the original Indian-IPTV-App's channel source).
    const val DEFAULT_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
    private const val TTL = 6L * 3600 * 1000

    var all: List<Channel> = emptyList()
    var shown: List<Channel> = emptyList()
    var queue: List<Channel> = emptyList()
    var index = 0

    private var favs = HashSet<String>()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")

    private fun prefs(c: Context) = c.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    fun init(c: Context) {
        favs = HashSet(prefs(c).getStringSet("fav", emptySet()) ?: emptySet())
    }

    fun isFav(url: String) = url in favs

    fun toggleFav(c: Context, url: String): Boolean {
        val added = favs.add(url)
        if (!added) favs.remove(url)
        prefs(c).edit().putStringSet("fav", HashSet(favs)).apply()
        return added
    }

    fun sourceUrl(c: Context): String = prefs(c).getString("src", DEFAULT_URL) ?: DEFAULT_URL
    fun setSource(c: Context, u: String) { prefs(c).edit().putString("src", u).apply() }

    fun load(c: Context, force: Boolean, cb: (List<Channel>?, String?) -> Unit) {
        val app = c.applicationContext
        io.execute {
            val f = File(app.filesDir, "playlist.m3u")
            var list: List<Channel>? = null
            var msg: String? = null
            val fresh = !force && f.exists() && System.currentTimeMillis() - f.lastModified() < TTL
            if (fresh) {
                try { list = parse(f.readText()) } catch (e: Exception) { list = null }
            }
            if (list.isNullOrEmpty()) {
                try {
                    val text = download(sourceUrl(app))
                    val l = parse(text)
                    if (l.isEmpty()) throw IOException("no channels in playlist")
                    f.writeText(text)
                    list = l
                } catch (e: Exception) {
                    msg = "Could not load playlist (${e.message})"
                    try {
                        if (f.exists()) { list = parse(f.readText()); msg = "Offline – showing saved channels" }
                    } catch (e2: Exception) { /* ignore */ }
                }
            }
            val r = list
            val m = msg
            ui.post { cb(if (r.isNullOrEmpty()) null else r, m) }
        }
    }

    private fun download(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo: String? = null
        var groups: List<String> = emptyList()
        var ua: String? = null
        var ref: String? = null
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF")) {
                val a = HashMap<String, String>()
                for (m in attr.findAll(l)) a[m.groupValues[1].lowercase()] = m.groupValues[2]
                name = l.substringAfterLast(",").trim()
                logo = a["tvg-logo"]?.takeIf { it.isNotBlank() }
                groups = (a["group-title"] ?: "").split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }
                ua = null; ref = null
            } else if (l.startsWith("#EXTVLCOPT:http-user-agent=")) {
                ua = l.substringAfter("=")
            } else if (l.startsWith("#EXTVLCOPT:http-referrer=")) {
                ref = l.substringAfter("=")
            } else if (!l.startsWith("#")) {
                val n = name
                if (n != null && l.startsWith("http")) {
                    out.add(Channel(n.ifEmpty { "Channel" }, l, logo, groups.ifEmpty { listOf("Other") }, ua, ref))
                    name = null
                }
            }
        }
        return out.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }
}
