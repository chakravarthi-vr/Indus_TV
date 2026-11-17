package com.iptv.industv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private val UA = "Mozilla/5.0 (Linux; Android 9; MiTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110 Safari/537.36"
    private var player: ExoPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    private var retries = 0
    private var current: Channel? = null

    private lateinit var view: PlayerView
    private lateinit var spin: View
    private lateinit var info: View
    private lateinit var chName: TextView
    private lateinit var chSub: TextView
    private lateinit var err: TextView

    private val hideInfo = Runnable { info.visibility = View.GONE }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        view = findViewById(R.id.player)
        spin = findViewById(R.id.spin)
        info = findViewById(R.id.info)
        chName = findViewById(R.id.chName)
        chSub = findViewById(R.id.chSub)
        err = findViewById(R.id.err)
        findViewById<View>(R.id.root).setOnClickListener { showInfo() }
        if (Repo.queue.isEmpty()) finish()
    }

    override fun onStart() {
        super.onStart()
        if (Repo.queue.isEmpty()) return
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(15000, 40000, 1500, 3000).build()
        val rf = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, rf).setLoadControl(load).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                spin.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) { retries = 0; err.visibility = View.GONE }
            }
            override fun onPlayerError(error: PlaybackException) {
                val ch = current ?: return
                if (retries < 3) {
                    retries++
                    h.postDelayed({ start(ch) }, 1500)
                } else {
                    spin.visibility = View.GONE
                    err.text = "“${ch.name}” is unavailable right now.\nPress ▲ / ▼ to switch channel, Back to exit."
                    err.visibility = View.VISIBLE
                }
            }
        })
        view.player = p
        player = p
        play(Repo.index)
    }

    override fun onStop() {
        super.onStop()
        h.removeCallbacksAndMessages(null)
        view.player = null
        player?.release()
        player = null
    }

    private fun play(i: Int, step: Int = 1) {
        val q = Repo.queue
        if (q.isEmpty()) { finish(); return }
        h.removeCallbacksAndMessages(null)
        var idx = ((i % q.size) + q.size) % q.size
        var guard = 0
        while (Repo.isYoutube(q[idx].url) && guard < q.size) { idx = ((idx + step) % q.size + q.size) % q.size; guard++ }
        if (Repo.isYoutube(q[idx].url)) { finish(); return }
        Repo.index = idx
        retries = 0
        val ch = q[idx]
        current = ch
        chName.text = ch.name
        chSub.text = "${idx + 1} / ${q.size}    ▲ next   ▼ previous   Back to list"
        err.visibility = View.GONE
        showInfo()
        start(ch)
    }

    private fun start(ch: Channel) {
        val p = player ?: return
        val props = HashMap<String, String>()
        ch.referrer?.let { props["Referer"] = it }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setUserAgent(ch.userAgent ?: UA)
            .setDefaultRequestProperties(props)
        p.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(ch.url)))
        p.prepare()
        p.playWhenReady = true
    }

    private fun showInfo() {
        info.visibility = View.VISIBLE
        h.removeCallbacks(hideInfo)
        h.postDelayed(hideInfo, 4000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { play(Repo.index + 1); true }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { play(Repo.index - 1, -1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showInfo(); true }
        else -> super.onKeyDown(keyCode, event)
    }
}
