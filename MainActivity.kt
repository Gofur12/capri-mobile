package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private fun askPermissions() {
        val list = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (list.isNotEmpty()) requestPermissions(list.toTypedArray(), 1)
    }

    // Dropdown dengan nilai tersimpan. Teks yang tampil di items, nilai yang disimpan di values.
    private fun pick(items: Array<String>, values: Array<String>, saved: String): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(values.indexOf(saved).coerceAtLeast(0))
        sp.tag = values
        return sp
    }

    private fun picked(sp: Spinner): String {
        @Suppress("UNCHECKED_CAST")
        val values = sp.tag as Array<String>
        return values[sp.selectedItemPosition]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions()

        val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }
        fun header(t: String) = TextView(this).apply {
            text = t
            textSize = 16f
            setPadding(0, pad, 0, 0)
        }
        fun numberField(v: String, hint: String = "") = EditText(this).apply {
            setText(v)
            this.hint = hint
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        // ---------- OTAK ----------
        col.addView(header("OTAK"))
        val key = EditText(this).apply {
            hint = "nvapi-..."
            setText(prefs.getString("key", ""))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply {
            setText(prefs.getString("model", Brain.DEFAULT_MODEL))
            setSingleLine()
        }
        val lang = pick(
            arrayOf("Bahasa Indonesia", "English", "Ikut bahasa kamu"),
            arrayOf("id", "en", "auto"),
            prefs.getString("lang", "id") ?: "id"
        )
        val replies = pick(
            arrayOf("Pendek", "Normal", "Panjang"),
            arrayOf("short", "normal", "long"),
            prefs.getString("replies", "short") ?: "short"
        )
        val memory = pick(
            arrayOf("Pendek (8 pesan)", "Normal (20 pesan)", "Panjang (40 pesan)"),
            arrayOf("8", "20", "40"),
            prefs.getString("memory", "20") ?: "20"
        )
        col.addView(label("API key NVIDIA (gratis di build.nvidia.com, disimpan hanya di HP ini)"))
        col.addView(key)
        col.addView(label("Model otak"))
        col.addView(model)
        col.addView(label("Bahasa Capri"))
        col.addView(lang)
        col.addView(label("Panjang jawaban"))
        col.addView(replies)
        col.addView(label("Ingatan chat"))
        col.addView(memory)

        // ---------- SUARA ----------
        col.addView(header("SUARA"))
        val voiceMode = pick(
            arrayOf("Mati (teks saja)", "Suara HP", "ElevenLabs"),
            arrayOf("off", "phone", "cloud"),
            prefs.getString("voice_mode", "cloud") ?: "cloud"
        )
        val eKey = EditText(this).apply {
            hint = "sk_... (ElevenLabs)"
            setText(prefs.getString("eleven_key", ""))
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val eVoice = EditText(this).apply {
            hint = "Voice ID ElevenLabs"
            setText(prefs.getString("eleven_voice", ""))
            setSingleLine()
        }
        val eModel = pick(
            arrayOf("Default (eleven_v4_turbo)", "Flash v2.5 (lebih cepat)", "Multilingual v2"),
            arrayOf("eleven_v4_turbo", "eleven_flash_v2_5", "eleven_multilingual_v2"),
            prefs.getString("eleven_model", "eleven_v4_turbo") ?: "eleven_v4_turbo"
        )
        val eStab = numberField(prefs.getString("eleven_stab", "")?.ifEmpty { null } ?: ElevenVoice.DEFAULT_STABILITY.toString(), "0-1")
        val eSim = numberField(prefs.getString("eleven_sim", "")?.ifEmpty { null } ?: ElevenVoice.DEFAULT_SIMILARITY.toString(), "0-1")
        val eSpeed = numberField(prefs.getString("eleven_speed", "")?.ifEmpty { null } ?: ElevenVoice.DEFAULT_SPEED.toString(), "0.7-1.2")
        val volume = numberField(prefs.getString("volume", "100") ?: "100", "50-150")
        val pitch = pick(
            arrayOf("Rendah", "Normal", "Tinggi"),
            arrayOf("low", "normal", "high"),
            prefs.getString("pitch", "normal") ?: "normal"
        )
        col.addView(label("Suara"))
        col.addView(voiceMode)
        col.addView(label("ElevenLabs API key"))
        col.addView(eKey)
        col.addView(label("Voice ID"))
        col.addView(eVoice)
        col.addView(label("Model ElevenLabs"))
        col.addView(eModel)
        col.addView(label("Stability (0-1, makin kecil makin ekspresif, nggak monoton)"))
        col.addView(eStab)
        col.addView(label("Similarity (0-1)"))
        col.addView(eSim)
        col.addView(label("Speed (0.7-1.2, juga dipakai suara HP)"))
        col.addView(eSpeed)
        col.addView(label("Volume (50-150%, di HP maksimal = volume HP)"))
        col.addView(volume)
        col.addView(label("Pitch (suara HP)"))
        col.addView(pitch)

        // ---------- CAPRI ----------
        col.addView(header("CAPRI"))
        val size = pick(
            arrayOf("Kecil", "Sedang", "Besar"),
            arrayOf("small", "medium", "large"),
            prefs.getString("size", "medium") ?: "medium"
        )
        val nick = EditText(this).apply {
            hint = "mis. brezze (boleh kosong)"
            setText(prefs.getString("nick", ""))
            setSingleLine()
        }
        col.addView(label("Capri manggil kamu apa?"))
        col.addView(nick)
        col.addView(label("Ukuran Capri (berlaku setelah Mulai ulang)"))
        col.addView(size)

        fun save() {
            prefs.edit()
                .putString("key", key.text.toString().trim())
                .putString("model", model.text.toString().trim())
                .putString("nick", nick.text.toString().trim())
                .putString("lang", picked(lang))
                .putString("replies", picked(replies))
                .putString("memory", picked(memory))
                .putString("voice_mode", picked(voiceMode))
                .putString("eleven_key", eKey.text.toString().trim())
                .putString("eleven_voice", eVoice.text.toString().trim())
                .putString("eleven_model", picked(eModel))
                .putString("eleven_stab", eStab.text.toString().trim())
                .putString("eleven_sim", eSim.text.toString().trim())
                .putString("eleven_speed", eSpeed.text.toString().trim())
                .putString("volume", volume.text.toString().trim())
                .putString("pitch", picked(pitch))
                .putString("size", picked(size))
                .apply()
        }

        // ---------- TOMBOL ----------
        val overlay = Button(this).apply {
            text = "1. Izinkan tampil di atas app lain"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val start = Button(this).apply {
            text = "2. Mulai Capri"
            setOnClickListener {
                save()
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    askPermissions()
                    Toast.makeText(this@MainActivity, "Izinkan mikrofon dulu, lalu tekan Mulai lagi.", Toast.LENGTH_LONG).show()
                } else if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Izinkan tampil di atas app lain dulu (tombol 1).", Toast.LENGTH_LONG).show()
                } else {
                    stopService(Intent(this@MainActivity, CapriService::class.java))
                    startForegroundService(Intent(this@MainActivity, CapriService::class.java))
                }
            }
        }
        val stop = Button(this).apply {
            text = "Hentikan Capri"
            setOnClickListener { stopService(Intent(this@MainActivity, CapriService::class.java)) }
        }
        val wipe = Button(this).apply {
            text = "Hapus ingatan chat"
            setOnClickListener {
                Brain.clear(this@MainActivity)
                Toast.makeText(this@MainActivity, "Ingatan Capri dihapus.", Toast.LENGTH_SHORT).show()
            }
        }
        val credit = TextView(this).apply {
            textSize = 11f
            setPadding(0, pad, 0, 0)
            text = "Karakter, sprite, dan persona berasal dari proyek Capri (github.com/lachydotmcg/capri-desktop), " +
                "© 2026 Lachy, lisensi PolyForm Noncommercial 1.0.0. Seni dan animasi oleh artisnya Capri. " +
                "Proyek fan-made nonkomersial, bukan produk resmi CD PROJEKT RED."
        }

        col.addView(overlay)
        col.addView(start)
        col.addView(stop)
        col.addView(wipe)
        col.addView(credit)
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
