package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

// Memutar sprite strip Capri (frame berjajar horizontal, tiap frame 240x384).
// Tiap frame dipotong jadi bitmap sendiri supaya aman dari batas ukuran tekstur GPU.
class SpriteView(context: Context) : View(context) {
    private class Clip(val frames: List<Bitmap>, val fps: Float)

    private val clips = HashMap<String, Clip>()
    private lateinit var cur: Clip
    private var curName = "idle"
    private var loop = true
    private var started = SystemClock.uptimeMillis()
    private var cycle = 0
    private var blinkOn = false
    private var mouthText: String? = null
    private var mouthStart = 0L
    private var mouthHold = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    init {
        load("idle", 16, 8f)
        load("glitch", 10, 15f)
        load("grin", 16, 8f)
        load("dangle", 24, 10f)
        load("blink", 4, 8f)
        load("mouth", 5, 12f)
        cur = clips.getValue("idle")
    }

    private fun load(name: String, count: Int, fps: Float) {
        val strip = context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it) }
        val w = strip.width / count
        val list = (0 until count).map { Bitmap.createBitmap(strip, it * w, 0, w, strip.height) }
        strip.recycle()
        clips[name] = Clip(list, fps)
    }

    fun play(name: String, loop: Boolean = true) {
        cur = clips.getValue(name)
        curName = name
        this.loop = loop
        started = SystemClock.uptimeMillis()
        cycle = 0
        blinkOn = false
        invalidate()
    }

    // Mulut bergerak mengikuti huruf-huruf balasan. hold = terus bergerak sampai stopTalk() (dipakai saat suara).
    fun talk(text: String, hold: Boolean) {
        if (text.isBlank()) return
        mouthText = text
        mouthStart = SystemClock.uptimeMillis()
        mouthHold = hold
        invalidate()
    }

    fun stopTalk() {
        mouthText = null
    }

    private fun mouthFor(ch: Char, step: Int): Int {
        val l = ch.lowercaseChar()
        return when {
            l in "aeo" -> if (ch.isUpperCase()) 4 else 3
            l in "iuy" -> 2
            l in "mbp" || !ch.isLetter() -> 0
            else -> if (step % 2 == 1) 1 else 2
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val t = (now - started) / 1000f
        val raw = (t * cur.fps).toInt()
        if (!loop && raw >= cur.frames.size) { play("idle"); return }
        val i = raw % cur.frames.size
        val c = raw / cur.frames.size
        if (c != cycle) { cycle = c; blinkOn = Random.nextFloat() < 0.45f }

        dst.set(0, 0, width, height)
        val idle = curName == "idle"
        val body = if (idle && blinkOn && i in 9..12) clips.getValue("blink").frames[i - 9] else cur.frames[i]
        canvas.drawBitmap(body, null, dst, paint)

        val text = mouthText
        if (idle && text != null) {
            val letters = ((now - mouthStart) / 1000f * 14f).toInt()
            var n = letters
            if (n >= text.length) {
                if (mouthHold) n %= text.length else { mouthText = null; n = -1 }
            }
            if (n >= 0) canvas.drawBitmap(clips.getValue("mouth").frames[mouthFor(text[n], letters)], null, dst, paint)
        }
        postInvalidateOnAnimation()
    }
}
