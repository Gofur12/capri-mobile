package app.capri.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

// Capri melayang di atas app lain. Ketuk = buka/tutup chat, tahan lalu geser = pindah.
// Suara: Capri bicara lewat Text-to-Speech Android dan mendengar lewat pengenal suara Google.
class CapriService : Service() {
    private lateinit var wm: WindowManager
    private var pet: SpriteView? = null
    private var panel: LinearLayout? = null
    private var reply: TextView? = null
    private var input: EditText? = null
    private var micBtn: Button? = null
    private var callBtn: Button? = null
    private var voiceBtn: Button? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var callMode = false
    private var lastReply = ""
    private var player: android.media.MediaPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prefs(): SharedPreferences = getSharedPreferences("capri", Context.MODE_PRIVATE)
    private fun voiceOn() = prefs().getBoolean("voice", true) && prefs().getString("voice_mode", "cloud") != "off"
    private fun volumeLevel(): Float = ((prefs().getString("volume", "100")?.toIntOrNull() ?: 100) / 100f).coerceIn(0f, 1f)
    private fun speedValue(): Float = (prefs().getString("eleven_speed", "")?.toFloatOrNull() ?: 1.0f).coerceIn(0.7f, 1.2f)
    private fun pitchValue(): Float = when (prefs().getString("pitch", "normal")) {
        "low" -> 0.85f
        "high" -> 1.3f
        else -> 1.0f
    }
    private fun petWidthDp(): Int = when (prefs().getString("size", "medium")) {
        "small" -> 70
        "large" -> 120
        else -> 90
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Brain.load(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.getDefault())
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale.US)
                tts?.setPitch(1.15f)
                tts?.setSpeechRate(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { ui.post { pet?.talk(lastReply, true) } }
                    override fun onDone(id: String?) { ui.post { spoke() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { ui.post { spoke() } }
                })
                ttsReady = true
            }
        }
    }

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
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (mic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(1, n, type)
            } else if (Build.VERSION.SDK_INT >= 30 && mic) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun showPet() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = SpriteView(this)
        val lp = WindowManager.LayoutParams(
            dp(petWidthDp()), dp(petWidthDp() * 8 / 5),
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

    private fun smallButton(label: String): Button = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0)
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
            maxLines = 8
            text = "CAPRI: Yo. Ketik atau tekan mik dan ngomong."
        }
        val inp = EditText(this).apply {
            hint = "ketik ke Capri…"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        val send = smallButton("Kirim")
        val mic = smallButton("🎤")
        val call = smallButton("📞")
        val voice = smallButton("🔊")
        val close = smallButton("Tutup")
        val row = LinearLayout(this)
        for (b in listOf(send, mic, call, voice, close)) {
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(r)
        box.addView(inp)
        box.addView(row)

        send.setOnClickListener { sendTyped() }
        inp.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }
        mic.setOnClickListener { if (listening) stopListening() else startListening() }
        call.setOnClickListener {
            callMode = !callMode
            if (callMode) startListening() else stopListening()
            refresh()
        }
        voice.setOnClickListener {
            prefs().edit().putBoolean("voice", !voiceOn()).apply()
            if (!voiceOn()) { tts?.stop(); pet?.stopTalk() }
            refresh()
        }
        close.setOnClickListener { closePanel() }

        val width = (resources.displayMetrics.widthPixels - dp(24)).coerceAtMost(dp(360))
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
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
        micBtn = mic
        callBtn = call
        voiceBtn = voice
        refresh()
    }

    private fun closePanel() {
        callMode = false
        stopListening()
        panel?.let { wm.removeView(it) }
        panel = null
        reply = null
        input = null
        micBtn = null
        callBtn = null
        voiceBtn = null
    }

    private fun refresh() {
        micBtn?.text = if (listening) "⏹" else "🎤"
        callBtn?.text = if (callMode) "📴" else "📞"
        voiceBtn?.text = if (voiceOn()) "🔊" else "🔇"
    }

    private fun say(msg: String) { reply?.text = "CAPRI: $msg" }

    // ---------- Kirim ke otak ----------

    private fun sendTyped() {
        val inp = input ?: return
        val text = inp.text.toString().trim()
        if (text.isEmpty()) return
        inp.setText("")
        ask(text)
    }

    private fun ask(text: String) {
        val p = prefs()
        val key = (p.getString("key", "") ?: "").trim()
        val model = (p.getString("model", "") ?: "").trim().ifEmpty { Brain.DEFAULT_MODEL }
        val nick = (p.getString("nick", "") ?: "").trim()
        if (key.isEmpty()) {
            say("Belum ada API key. Buka app Capri Mobile dan isi key nvapi- dulu.")
            return
        }
        say("…")
        stopAudio()
        pet?.stopTalk()
        pet?.play("glitch")
        thread {
            val lang = p.getString("lang", "id") ?: "id"
            val replies = p.getString("replies", "short") ?: "short"
            val memory = p.getString("memory", "20")?.toIntOrNull() ?: 20
            val out = Brain.ask(key, model, text, nick, lang, replies, memory)
            ui.post {
                lastReply = out
                say(out)
                pet?.play("idle")
                speak(out)
            }
        }
    }

    // ---------- Suara keluar (TTS) ----------

    private fun speak(text: String) {
        val p = prefs()
        val eKey = (p.getString("eleven_key", "") ?: "").trim()
        val eVoice = (p.getString("eleven_voice", "") ?: "").trim()
        if (voiceOn() && eKey.isNotEmpty() && eVoice.isNotEmpty() && p.getString("voice_mode", "cloud") == "cloud") {
            val eModel = (p.getString("eleven_model", "") ?: "").trim()
            val stab = p.getString("eleven_stab", "")?.toFloatOrNull() ?: ElevenVoice.DEFAULT_STABILITY
            val sim = p.getString("eleven_sim", "")?.toFloatOrNull() ?: ElevenVoice.DEFAULT_SIMILARITY
            val spd = p.getString("eleven_speed", "")?.toFloatOrNull() ?: ElevenVoice.DEFAULT_SPEED
            val clean = cleanForSpeech(text)
            if (clean.isEmpty()) { afterReply(); return }
            thread {
                try {
                    val wav = ElevenVoice.speak(eKey, eVoice, eModel, stab, sim, spd.coerceIn(0.7f, 1.2f), clean)
                    ui.post { playWav(wav) }
                } catch (e: Exception) {
                    ui.post {
                        say(lastReply + "\n(suara ElevenLabs gagal: ${e.message?.take(80)})")
                        speakFallback(text)
                    }
                }
            }
        } else {
            speakFallback(text)
        }
    }

    // Suara bawaan HP, dipakai kalau belum ada key/voice ID ElevenLabs atau kalau ElevenLabs gagal.
    private fun speakFallback(text: String) {
        val clean = cleanForSpeech(text)
        if (voiceOn() && ttsReady && clean.isNotEmpty()) {
            tts?.setPitch(pitchValue())
            tts?.setSpeechRate(speedValue())
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "capri")
        } else {
            pet?.talk(text, false)
            ui.postDelayed({ afterReply() }, (text.length * 70L).coerceIn(800L, 6000L))
        }
    }

    private fun cleanForSpeech(text: String): String = text
        .replace(Regex("[*_`#~]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]"), "")
        .trim()

    private fun playWav(bytes: ByteArray) {
        stopAudio()
        try {
            val f = java.io.File(cacheDir, "capri-voice.wav")
            f.writeBytes(bytes)
            val mp = android.media.MediaPlayer()
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.setOnPreparedListener {
                pet?.talk(lastReply, true)
                it.setVolume(volumeLevel(), volumeLevel())
                it.start()
            }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                spoke()
            }
            mp.setOnErrorListener { m, _, _ ->
                m.release()
                if (player === m) player = null
                spoke()
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            spoke()
        }
    }

    private fun stopAudio() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
    }

    private fun spoke() {
        pet?.stopTalk()
        afterReply()
    }

    private fun afterReply() {
        if (callMode && panel != null) listenSoon(400)
    }

    // ---------- Suara masuk (mik) ----------

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            input?.setText(t)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
            if (t.isNotEmpty()) {
                input?.setText("")
                ask(t)
            } else if (callMode) {
                listenSoon(600)
            }
            refresh()
        }

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (callMode) listenSoon(600)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    callMode = false
                    say("Izin mikrofon belum diberikan. Buka app Capri Mobile dan izinkan, lalu Hentikan dan Mulai lagi.")
                }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    callMode = false
                    say("Pengenal suara butuh internet. Cek koneksi.")
                }
                else -> {
                    callMode = false
                    say("Mik error (kode $error).")
                }
            }
            refresh()
        }
    }

    private fun listenSoon(ms: Long) {
        ui.postDelayed({ if (callMode && !listening && panel != null) startListening() }, ms)
    }

    private fun startListening() {
        if (listening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callMode = false
            say("Izin mikrofon belum diberikan. Buka app Capri Mobile dan izinkan.")
            refresh()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            callMode = false
            say("HP ini nggak punya layanan pengenal suara (app Google).")
            refresh()
            return
        }
        tts?.stop() // supaya suara Capri sendiri nggak ikut terdengar mik
        pet?.stopTalk()
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        listening = true
        say("Mendengarkan…")
        refresh()
        rec.startListening(intent)
    }

    private fun stopListening() {
        if (listening) recognizer?.cancel()
        listening = false
        refresh()
    }

    override fun onDestroy() {
        if (pet != null) {
            closePanel()
            wm.removeView(pet)
            pet = null
        }
        stopAudio()
        tts?.stop()
        tts?.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }
}
