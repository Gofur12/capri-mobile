package app.capri.mobile

import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

// Pemutar musik mini seperti di Capri PC. Tampil kalau ada app yang memutar musik (Spotify, YouTube, GoTube, dll.).
// Membaca sesi media Android, jadi butuh izin "Akses notifikasi" untuk Capri Mobile.
class MiniPlayer(
    private val ctx: Context,
    private val wm: WindowManager,
    private val ui: Handler,
    private val allowed: () -> Boolean,
    private val onTrack: (String, String) -> Unit
) {
    var nowPlaying: String = ""
        private set

    private var card: LinearLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var titleV: TextView? = null
    private var artistV: TextView? = null
    private var timeV: TextView? = null
    private var barV: ProgressBar? = null
    private var playV: TextView? = null
    private var controller: MediaController? = null
    private var lastKey = ""

    private val cyan = 0xFF05D9E8.toInt()
    private val pink = 0xFFFF2A6D.toInt()
    private val muted = 0xFF7A8AA3.toInt()

    private val tick = object : Runnable {
        override fun run() {
            try {
                refresh()
            } catch (e: Exception) {
                AppLog.add("MUSIK", "pemutar mini: ${e.javaClass.simpleName} ${e.message}")
            }
            ui.postDelayed(this, 1000L)
        }
    }

    fun start() {
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    fun stop() {
        ui.removeCallbacks(tick)
        hide()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun sessions(): List<MediaController> {
        return try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            msm.getActiveSessions(ComponentName(ctx, CapriMediaListener::class.java))
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return String.format("%d:%02d", s / 60L, s % 60L)
    }

    private fun refresh() {
        val list = sessions()
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        controller = c
        if (c == null) {
            nowPlaying = ""
            lastKey = ""
            hide()
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        if (title.isBlank()) {
            nowPlaying = ""
            hide()
            return
        }
        val app = appLabel(c.packageName)
        nowPlaying = if (playing) title + (if (artist.isNotBlank()) " - $artist" else "") + " ($app)" else ""
        val key = title + "|" + artist + "|" + c.packageName
        if (key != lastKey) {
            lastKey = key
            if (playing) onTrack(title, artist)
        }
        if (!allowed()) {
            hide()
            return
        }
        show()
        titleV?.text = title
        artistV?.text = if (artist.isNotBlank()) "$artist · $app" else app
        playV?.text = if (playing) "⏸" else "▶"
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st?.position ?: 0L
        if (playing && st != null) pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        if (dur > 0L) {
            barV?.progress = (pos * 1000L / dur).toInt().coerceIn(0, 1000)
            timeV?.text = fmt(pos) + " / " + fmt(dur)
        } else {
            barV?.progress = 0
            timeV?.text = fmt(pos)
        }
    }

    private fun hide() {
        card?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        card = null
        lp = null
        titleV = null
        artistV = null
        timeV = null
        barV = null
        playV = null
    }

    private fun act(block: (MediaController.TransportControls) -> Unit) {
        controller?.transportControls?.let(block)
    }

    private fun togglePlay() {
        val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        act { if (playing) it.pause() else it.play() }
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
        text = label
        textSize = 24f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(4), dp(18), dp(4))
        setOnClickListener { onClick() }
    }

    private fun show() {
        if (card != null) return
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2101826.toInt())
                setStroke(dp(2), pink)
                cornerRadius = dp(16).toFloat()
            }
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val note = TextView(ctx).apply {
            text = "♪"
            setTextColor(cyan)
            textSize = 18f
            setPadding(0, 0, dp(8), 0)
        }
        val title = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        val close = TextView(ctx).apply {
            text = "✕"
            setTextColor(pink)
            textSize = 16f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener {
                Cfg.set(ctx, "mini_player", "off")
                hide()
            }
        }
        head.addView(note)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(close)
        val artist = TextView(ctx).apply {
            setTextColor(muted)
            textSize = 11f
            isSingleLine = true
        }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(cyan)
        }
        val time = TextView(ctx).apply {
            setTextColor(muted)
            textSize = 10f
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val prev = button("⏮") { act { it.skipToPrevious() } }
        val play = button("⏸") { togglePlay() }
        val next = button("⏭") { act { it.skipToNext() } }
        row.addView(prev)
        row.addView(play)
        row.addView(next)
        box.addView(head)
        box.addView(artist)
        box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        box.addView(time)
        box.addView(row)

        // Ketuk bilah progres untuk loncat ke bagian lagu itu.
        bar.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val dur = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                if (dur > 0L && v.width > 0) act { it.seekTo((e.x / v.width * dur).toLong()) }
            }
            true
        }

        val w = dp(270)
        val params = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        val sw = ctx.resources.displayMetrics.widthPixels
        val saved = Cfg.int(ctx, "mp_x", -1)
        params.x = if (saved >= 0) saved else (sw - w - dp(8)).coerceAtLeast(0)
        params.y = Cfg.int(ctx, "mp_y", dp(90))

        // Geser pemutar dengan menyeret bagian atasnya.
        var dX = 0f
        var dY = 0f
        var sX = 0
        var sY = 0
        head.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX; dY = e.rawY
                    sX = params.x; sY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (sX + (e.rawX - dX)).toInt().coerceIn(0, (sw - w).coerceAtLeast(0))
                    params.y = (sY + (e.rawY - dY)).toInt().coerceAtLeast(0)
                    try { wm.updateViewLayout(box, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Cfg.set(ctx, "mp_x", params.x.toString())
                    Cfg.set(ctx, "mp_y", params.y.toString())
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(box, params)
        } catch (e: Exception) {
            AppLog.add("MUSIK", "tampil gagal: ${e.javaClass.simpleName} ${e.message}")
            return
        }
        card = box
        lp = params
        titleV = title
        artistV = artist
        timeV = time
        barV = bar
        playV = play
    }
}
