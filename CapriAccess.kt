package app.capri.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Kontrol HP untuk Capri: geser layar, ketuk teks, kembali, beranda, dan mencari app dengan menggulir.
// Aktif hanya kalau abang menyalakannya sendiri di Pengaturan > Aksesibilitas.
class CapriAccess : AccessibilityService() {
    companion object {
        @Volatile var instance: CapriAccess? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.add("KONTROL", "aksesibilitas tersambung")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // up = true: jari bergerak ke atas, isi layar bergulir ke bawah.
    fun swipe(up: Boolean, span: Float = 0.42f): Boolean {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val from = dm.heightPixels * (if (up) 0.72f else 0.28f)
        val to = dm.heightPixels * (if (up) 0.72f - span else 0.28f + span)
        return gesture(x, from, x, to, 380L)
    }

    fun tap(x: Float, y: Float): Boolean = gesture(x, y, x, y, 60L)

    // Harus dipanggil dari thread latar, bukan thread utama.
    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val path = Path()
        path.moveTo(x1, y1)
        if (x1 != x2 || y1 != y2) path.lineTo(x2, y2)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, ms))
            .build()
        val latch = CountDownLatch(1)
        var ok = false
        val sent = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
        }, null)
        if (!sent) return false
        latch.await(3, TimeUnit.SECONDS)
        return ok
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var c = n
        var depth = 0
        while (c != null && depth < 6) {
            if (c.isClickable) return c
            c = c.parent
            depth++
        }
        return null
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val hits = root.findAccessibilityNodeInfosByText(text) ?: return false
        for (h in hits) {
            val c = clickableAncestor(h)
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        for (h in hits) {
            val r = Rect()
            h.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) return tap(r.exactCenterX(), r.exactCenterY())
        }
        return false
    }

    fun findAndClick(text: String, maxScrolls: Int): Boolean {
        for (i in 0..maxScrolls) {
            if (clickText(text)) return true
            if (i == maxScrolls) break
            swipe(true)
            Thread.sleep(700L)
        }
        return false
    }

    // Cadangan kalau app tidak ketemu lewat daftar app: ke beranda, buka laci app, lalu gulir sambil mencari namanya.
    fun openByScrolling(label: String): Boolean {
        home()
        Thread.sleep(700L)
        swipe(true, 0.5f)
        Thread.sleep(800L)
        return findAndClick(label, 8)
    }

    // Tutup app lewat daftar app terbaru: kartunya digeser ke atas. label null = tutup app yang paling depan.
    fun closeApp(label: String?): Boolean {
        recents()
        Thread.sleep(1100L)
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        if (label.isNullOrBlank()) {
            val ok = gesture(w / 2f, h * 0.55f, w / 2f, h * 0.05f, 280L)
            Thread.sleep(500L)
            home()
            return ok
        }
        for (attempt in 0..3) {
            val hits = rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
            if (hits != null && hits.isNotEmpty()) {
                val r = Rect()
                hits[0].getBoundsInScreen(r)
                val x = r.exactCenterX().coerceIn(w * 0.1f, w * 0.9f)
                val y0 = (r.bottom + h * 0.25f).coerceAtMost(h * 0.9f)
                val ok = gesture(x, y0, x, h * 0.04f, 280L)
                Thread.sleep(500L)
                home()
                return ok
            }
            gesture(w * 0.2f, h * 0.5f, w * 0.85f, h * 0.5f, 300L)
            Thread.sleep(600L)
        }
        home()
        return false
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 25) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    // Isi kolom pencarian di app yang sedang terbuka, lalu kirim.
    fun searchInApp(query: String): Boolean {
        var field = findEditable(rootInActiveWindow)
        if (field == null) {
            for (word in listOf("Search", "Cari", "Telusuri", "Find")) {
                if (clickText(word)) {
                    Thread.sleep(900L)
                    field = findEditable(rootInActiveWindow)
                    if (field != null) break
                }
            }
        }
        val f = field ?: return false
        f.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        if (!f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        Thread.sleep(500L)
        var sent = false
        if (Build.VERSION.SDK_INT >= 30) sent = f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        if (!sent) sent = clickText(query)
        Thread.sleep(1800L)
        return sent
    }

    // Ketuk hasil pencarian pertama: kartu lebar paling atas di daftar hasil.
    fun clickFirstResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        var best: AccessibilityNodeInfo? = null
        var bestTop = Int.MAX_VALUE
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30) return
            if (n.isClickable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() >= w * 0.5f && r.height() >= h * 0.08f && r.height() <= h * 0.45f && r.top >= h * 0.10f && r.top < bestTop) {
                    best = n
                    bestTop = r.top
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val b = best
        if (b != null && b.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tap(w / 2f, h * 0.30f)
    }
}
