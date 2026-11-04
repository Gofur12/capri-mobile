package app.capri.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.abs

// Capri melayang di atas app lain. Ketuk = buka/tutup chat, tahan lalu geser = pindah.
class CapriService : Service() {
    private lateinit var wm: WindowManager
    private var pet: SpriteView? = null
    private var panel: LinearLayout? = null
    private var reply: TextView? = null
    private var input: EditText? = null
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (pet == null) showPet()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "capri")
            .setContentTitle("Capri aktif")
            .setContentText("Ketuk Capri di layar buat ngobrol")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun showPet() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = SpriteView(this)
        val lp = WindowManager.LayoutParams(
            dp(90), dp(144),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(16)
        lp.y = dp(240)

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > dp(8) || abs(dy) > dp(8))) { dragging = true; v.play("dangle") }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        wm.updateViewLayout(v, lp)
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) v.play("idle") else togglePanel()
            }
            true
        }
        try {
            wm.addView(v, lp)
            pet = v
        } catch (e: Exception) {
            stopSelf() // izin overlay belum diberikan
        }
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2101820"))
                setStroke(dp(2), Color.parseColor("#05D9E8"))
                cornerRadius = dp(10).toFloat()
            }
        }
        val r = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "CAPRI: Yo. Ketik sesuatu."
        }
        val inp = EditText(this).apply {
            hint = "ketik ke Capri…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = Button(this).apply { text = "Kirim" }
        val close = Button(this).apply { text = "Tutup" }
        val row = LinearLayout(this)
        row.addView(send)
        row.addView(close)
        box.addView(r)
        box.addView(inp)
        box.addView(row)
        send.setOnClickListener { send() }
        close.setOnClickListener { closePanel() }
        inp.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }

        val lp = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // fokusable, jadi keyboard bisa muncul
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(24)
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        wm.addView(box, lp)
        panel = box
        reply = r
        input = inp
    }

    private fun closePanel() {
        panel?.let { wm.removeView(it) }
        panel = null
        reply = null
        input = null
    }

    private fun send() {
        val inp = input ?: return
        val text = inp.text.toString().trim()
        if (text.isEmpty()) return
        inp.setText("")
        val p = getSharedPreferences("capri", Context.MODE_PRIVATE)
        val key = (p.getString("key", "") ?: "").trim()
        val model = (p.getString("model", "") ?: "").trim().ifEmpty { Brain.DEFAULT_MODEL }
        if (key.isEmpty()) {
            reply?.text = "CAPRI: Belum ada API key. Buka app Capri Mobile dan isi key nvapi- dulu."
            return
        }
        reply?.text = "CAPRI: …"
        pet?.play("glitch")
        thread {
            val out = Brain.ask(key, model, text)
            ui.post {
                reply?.text = "CAPRI: $out"
                pet?.play("grin", loop = false)
            }
        }
    }

    override fun onDestroy() {
        if (pet != null) {
            closePanel()
            wm.removeView(pet)
            pet = null
        }
        super.onDestroy()
    }
}
