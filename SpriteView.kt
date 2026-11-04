package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View

// Memutar sprite strip Capri (frame berjajar horizontal, tiap frame 240x384).
// Tiap frame dipotong jadi bitmap sendiri supaya aman dari batas ukuran tekstur GPU.
class SpriteView(context: Context) : View(context) {
    private class Clip(val frames: List<Bitmap>, val fps: Float)

    private val clips = HashMap<String, Clip>()
    private lateinit var cur: Clip
    private var loop = true
    private var started = SystemClock.uptimeMillis()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()

    init {
        load("idle", 16, 8f)
        load("glitch", 10, 15f)
        load("grin", 16, 8f)
        load("dangle", 24, 10f)
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
        this.loop = loop
        started = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - started) / 1000f
        var i = (t * cur.fps).toInt()
        if (i >= cur.frames.size) {
            if (loop) i %= cur.frames.size else { play("idle"); return }
        }
        dst.set(0, 0, width, height)
        canvas.drawBitmap(cur.frames[i], null, dst, paint)
        postInvalidateOnAnimation()
    }
}
