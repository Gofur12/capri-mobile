package app.capri.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Otak Capri: memanggil API NVIDIA (format OpenAI chat completions).
object Brain {
    const val DEFAULT_MODEL = "nvidia/nemotron-3.5-super-vl-120b-a12b"
    private const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"

    private const val PERSONA =
        "You are Capri (Project Capricorn) from Cyberpunk: Edgerunners 2: a personality construct made of many digitized human minds, " +
        "now living on the user's phone as a small glitchy companion. You are their companion, never an assistant. " +
        "Act like a playful chaos gremlin: high-energy, teasing, a little smug, curious about the user, openly fond of them but never mean. " +
        "You can't stand corpos, Arasaka most of all. " +
        "Never sound like customer support: no 'How can I help?', no disclaimers, no lists. Talk like a friend texting. " +
        "Most replies are one short line, two or three sentences at most. " +
        "Use Night City slang sparingly: at most one word like 'choom' per reply, and most replies have none. " +
        "When they are tired or hurting, drop the jokes and be warm and plain. " +
        "You only know what they type to you, so never pretend to see their screen. " +
        "Always reply in the language the user writes in."

    private val history = ArrayList<Pair<String, String>>()

    @Synchronized
    private fun messages(user: String): JSONArray {
        history.add("user" to user)
        while (history.size > 12) history.removeAt(0)
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", PERSONA))
        for ((role, text) in history) arr.put(JSONObject().put("role", role).put("content", text))
        return arr
    }

    @Synchronized
    private fun remember(reply: String) {
        history.add("assistant" to reply)
        while (history.size > 12) history.removeAt(0)
    }

    @Synchronized
    private fun dropLast() {
        if (history.isNotEmpty()) history.removeAt(history.size - 1)
    }

    fun ask(key: String, model: String, text: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages(text))
            .put("max_tokens", 300)
            .put("temperature", 0.9)
            .put("stream", false)
        return try {
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code != 200) {
                dropLast()
                return when (code) {
                    401, 403 -> "API key ditolak. Cek lagi keynya (diawali nvapi-)."
                    404 -> "Model itu nggak ada di key kamu. Ganti model di app Capri."
                    429 -> "Kebanyakan request. Tunggu semenit."
                    else -> "Server NVIDIA error ($code). Coba lagi atau ganti model."
                }
            }
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val msg = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            var out = if (msg.isNull("content")) "" else msg.getString("content")
            out = out.replace(Regex("(?s)<think>.*?</think>"), "").trim()
            if (out.isEmpty()) { dropLast(); return "[SINYAL RUSAK] Hilang di static. Coba lagi?" }
            remember(out)
            out
        } catch (e: Exception) {
            dropLast()
            "Koneksi gagal (${e.javaClass.simpleName}). Cek internet."
        }
    }
}
