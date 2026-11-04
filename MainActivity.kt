package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val prefs = getSharedPreferences("capri", Context.MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }

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
        fun save() {
            prefs.edit()
                .putString("key", key.text.toString().trim())
                .putString("model", model.text.toString().trim())
                .apply()
        }

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
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Izinkan tampil di atas app lain dulu (tombol 1).", Toast.LENGTH_LONG).show()
                } else {
                    startForegroundService(Intent(this@MainActivity, CapriService::class.java))
                }
            }
        }
        val stop = Button(this).apply {
            text = "Hentikan Capri"
            setOnClickListener { stopService(Intent(this@MainActivity, CapriService::class.java)) }
        }
        val credit = TextView(this).apply {
            textSize = 11f
            setPadding(0, pad, 0, 0)
            text = "Karakter, sprite, dan persona berasal dari proyek Capri (github.com/lachydotmcg/capri-desktop), " +
                "© 2026 Lachy, lisensi PolyForm Noncommercial 1.0.0. Seni dan animasi oleh artisnya Capri. " +
                "Proyek fan-made nonkomersial, bukan produk resmi CD PROJEKT RED."
        }

        col.addView(label("API key NVIDIA (gratis di build.nvidia.com, disimpan hanya di HP ini)"))
        col.addView(key)
        col.addView(label("Model"))
        col.addView(model)
        col.addView(overlay)
        col.addView(start)
        col.addView(stop)
        col.addView(credit)
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
