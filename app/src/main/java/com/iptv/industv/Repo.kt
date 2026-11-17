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
    val referrer: String? = null,
    val languages: List<String> = emptyList()
)

object Repo {
    // Default source: public iptv-org India playlist. Change in-app via the "Source" button
    // (paste the m3u URL used by the original Indian-IPTV-App's channel source).
    const val DEFAULT_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
    // "Global" = every English-language channel in the public iptv-org collection
    const val GLOBAL_URL = "https://iptv-org.github.io/iptv/languages/eng.m3u"
    private const val TTL = 6L * 3600 * 1000

    var all: List<Channel> = emptyList()
    var shown: List<Channel> = emptyList()
    var queue: List<Channel> = emptyList()
    var index = 0

    private var favs = HashSet<String>()
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")

    val LANGS = listOf(
        "Tamil", "Hindi", "English", "Telugu", "Malayalam", "Kannada", "Marathi", "Gujarati",
        "Bengali", "Punjabi", "Odia", "Rajasthani", "Assamese", "Bhojpuri", "Urdu"
    )
    private val nonAlnum = Regex("[^a-z0-9]+")
    private fun norm(s: String) = " " + s.lowercase().replace(nonAlnum, " ").trim() + " "

    private val ALIASES = mapOf(
        "bangla" to "Bengali", "oriya" to "Odia", "odisha" to "Odia", "panjabi" to "Punjabi",
        "rajasthan" to "Rajasthani", "assam" to "Assamese", "gujarat" to "Gujarati",
        "kerala" to "Malayalam", "karnataka" to "Kannada", "maharashtra" to "Marathi"
    )

    // 1) language words written in the name (regional first, Hindi/English last)
    private val explicit: List<Pair<String, String>> =
        (LANGS.filter { it != "Hindi" && it != "English" }.map { norm(it) to it } +
            ALIASES.map { norm(it.key) to it.value } +
            listOf(norm("Hindi") to "Hindi", norm("English") to "English"))

    // 2) well-known channel names
    private val HINTS: List<Pair<String, List<String>>> = listOf(
        "Tamil" to listOf("sun news", "sun music", "kalaignar", "polimer", "raj tv", "raj news",
            "thanthi tv", "thanthi one", "thanthi", "jaya tv", "vasanth", "vasantham", "mega tv", "makkal tv", "murasu", "captain tv", "vijay tv", "star vijay", "vijay takkar", "adithya", "chutti tv", "kalaignar seithigal", "news j",
            "isai aruvi", "chithiram", "dhool", "win tv", "peppers tv", "sathyam tv", "sathiyam tv", "sathiyam", "moon tv", "tamil vision", "wow kidz", "puthiya thalaimurai"),
        "Telugu" to listOf("etv", "gemini", "maa tv", "maa music", "sakshi", "ntv", "tv5", "abn andhra", "10tv", "hmtv", "sumantv", "studio n", "tv9 telugu", "bhakti tv", "raj news telugu", "v6 news"),
        "Malayalam" to listOf("asianet", "mazhavil", "surya tv", "kairali", "manorama", "mathrubhumi", "flowers tv", "reporter tv", "twentyfour", "24 news", "janam tv", "amrita tv", "media one", "mediaone", "jaihind", "kaumudi", "shalom tv", "darshana"),
        "Kannada" to listOf("udaya", "suvarna", "public tv", "kasthuri", "power tv", "janasri", "tv9 kannada", "zee kannada", "colors kannada", "news first"),
        "Marathi" to listOf("abp majha", "zee 24 taas", "jai maharashtra", "saam tv", "star pravah", "mi marathi", "lokshahi", "tv9 marathi", "sony marathi", "zee marathi", "colors marathi", "news18 lokmat", "ibn lokmat"),
        "Gujarati" to listOf("tv9 gujarati", "zee 24 kalak", "sandesh news", "abp asmita", "vtv", "gstv", "dd girnar", "news18 gujarati", "colors gujarati"),
        "Bengali" to listOf("zee bangla", "star jalsha", "jalsha", "abp ananda", "24 ghanta", "kolkata tv", "sangeet bangla", "sananda", "tv9 bangla", "news18 bangla", "r plus", "republic bangla"),
        "Odia" to listOf("tarang", "sidharth tv", "otv", "zee sarthak", "news18 odia", "kanak news", "prameya", "naxatra", "mo tv", "argus"),
        "Rajasthani" to listOf("first india", "news18 rajasthan", "zee rajasthan"),
        "Punjabi" to listOf("ptc", "chardikla", "mh one", "jus punjabi", "9x tashan", "news18 punjab", "zee punjab"),
        "Assamese" to listOf("dy 365", "pratidin", "prag news", "ndtv assam"),
        "Hindi" to listOf("aaj tak", "india tv", "abp news", "zee news", "ndtv india", "news18 india", "republic bharat", "dd national",
            "dd bharati", "star plus", "star bharat", "sony sab", "sony entertainment", "sab tv", "colors tv", "zee tv", "dangal", "big magic", "news nation", "india news",
            "tv9 bharatvarsh", "sudarshan", "zee cinema", "star gold", "sony max", "and tv", "dd kisan", "lok sabha", "rajya sabha", "sansad", "bhakti", "sanskar", "aastha", "zee hindustan"),
        "English" to listOf("ndtv 24x7", "times now", "wion", "cnbc", "bloomberg", "cnn", "bbc", "al jazeera", "india today", "republic world",
            "mirror now", "et now", "hbo", "dd india", "news x", "france 24", "dw ", "sky news", "ndtv profit")
    )
    private val hints: List<Pair<String, String>> = HINTS.flatMap { (lang, ks) -> ks.map { norm(it) to lang } }

    // Matches names written without spaces, e.g. "VasanthTV", "TamilVision" (long keywords only)
    private val compactExplicit: List<Pair<String, String>> =
        LANGS.filter { it.length >= 5 && it != "Hindi" && it != "English" }.map { it.lowercase() to it }
    private val compactHints: List<Pair<String, String>> =
        hints.map { it.first.replace(" ", "") to it.second }.filter { it.first.length >= 7 }

    // Channels that never play: hidden from the app (Sun TV) or replaced by a YouTube entry (Puthiya Thalaimurai)
    private fun isHidden(name: String): Boolean {
        val n = norm(name)
        val c = n.replace(" ", "")
        return n.contains(" sun tv ") || c.startsWith("suntv") || c.contains("puthiyathalaimurai")
    }

    fun isYoutube(u: String) = u.contains("youtube.com") || u.contains("youtu.be")

    // Extra live channels that stream on YouTube (open in the YouTube app)
    private val EXTRAS = listOf(
        Channel(
            name = "Puthiya Thalaimurai (YouTube Live)",
            url = "https://www.youtube.com/channel/UCmyKnNRH0wH-r8I-ceP-dsg/live",
            logo = null, groups = listOf("News"), languages = listOf("Tamil")
        )
    )

    private fun canonical(s: String): String? {
        val k = s.trim().lowercase()
        if (k.isEmpty()) return null
        return LANGS.firstOrNull { it.lowercase() == k } ?: ALIASES[k]
    }

    private fun detect(name: String, tvg: String?): List<String> {
        if (!tvg.isNullOrBlank()) {
            val found = tvg.split(";", ",", "/", "|").mapNotNull { canonical(it) }.distinct()
            if (found.isNotEmpty()) return found
        }
        val n = norm(name)
        for ((kw, lang) in explicit) if (n.contains(kw)) return listOf(lang)
        for ((kw, lang) in hints) if (n.contains(kw)) return listOf(lang)
        val c = n.replace(" ", "")
        for ((kw, lang) in compactExplicit) if (c.contains(kw)) return listOf(lang)
        for ((kw, lang) in compactHints) if (c.contains(kw)) return listOf(lang)
        return emptyList()
    }

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

    fun load(c: Context, force: Boolean, global: Boolean = false, cb: (List<Channel>?, String?) -> Unit) {
        val app = c.applicationContext
        io.execute {
            val f = File(app.filesDir, if (global) "playlist_global.m3u" else "playlist.m3u")
            var list: List<Channel>? = null
            var msg: String? = null
            val fresh = !force && f.exists() && System.currentTimeMillis() - f.lastModified() < TTL
            if (fresh) {
                try { list = parse(f.readText()) } catch (e: Exception) { list = null }
            }
            if (list.isNullOrEmpty()) {
                try {
                    val text = download(if (global) GLOBAL_URL else sourceUrl(app))
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
            val r = if (!global && !list.isNullOrEmpty()) (list!! + EXTRAS).sortedBy { it.name.lowercase() } else list
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
        var tvgLang: String? = null
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF")) {
                val a = HashMap<String, String>()
                for (m in attr.findAll(l)) a[m.groupValues[1].lowercase()] = m.groupValues[2]
                name = l.substringAfterLast(",").trim()
                logo = a["tvg-logo"]?.takeIf { it.isNotBlank() }
                groups = (a["group-title"] ?: "").split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }
                tvgLang = a["tvg-language"]
                ua = null; ref = null
            } else if (l.startsWith("#EXTVLCOPT:http-user-agent=")) {
                ua = l.substringAfter("=")
            } else if (l.startsWith("#EXTVLCOPT:http-referrer=")) {
                ref = l.substringAfter("=")
            } else if (!l.startsWith("#")) {
                val n = name
                if (n != null && l.startsWith("http") && !isHidden(n)) {
                    out.add(Channel(n.ifEmpty { "Channel" }, l, logo, groups.ifEmpty { listOf("Other") }, ua, ref, detect(n, tvgLang)))
                    name = null
                }
            }
        }
        return out.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }
}
