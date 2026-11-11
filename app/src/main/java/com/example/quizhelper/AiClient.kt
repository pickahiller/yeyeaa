package com.example.quizhelper

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks to Anthropic, any OpenAI-compatible API, or Gemini. All take a JPEG screenshot + prompt. */
object AiClient {
    const val ANTHROPIC = "Anthropic"
    const val OPENAI = "OpenAI-compatible"   // OpenAI, OpenRouter, Groq, Together, Ollama, LM Studio, ...
    const val GEMINI = "Gemini"
    val providers = listOf(ANTHROPIC, OPENAI, GEMINI)

    fun defaultModel(p: String) = when (p) {
        ANTHROPIC -> "claude-sonnet-5-5"
        OPENAI -> "gpt-4o"
        else -> "gemini-2.5-flash"
    }

    fun defaultBase(p: String) = when (p) {
        ANTHROPIC -> "https://api.anthropic.com"
        OPENAI -> "https://api.openai.com/v1"
        else -> "https://generativelanguage.googleapis.com"
    }

    fun ask(provider: String, base: String, model: String, key: String, prompt: String, jpegB64: String): String {
        val root = base.trim().trimEnd('/')
        return when (provider) {
            ANTHROPIC -> {
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 150)
                    .put("messages", JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray()
                            .put(JSONObject().put("type", "image").put("source", JSONObject()
                                .put("type", "base64").put("media_type", "image/jpeg").put("data", jpegB64)))
                            .put(JSONObject().put("type", "text").put("text", prompt)))))
                val r = post("$root/v1/messages",
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body)
                val parts = r.getJSONArray("content")
                (0 until parts.length()).map { parts.getJSONObject(it).optString("text") }.joinToString("").trim()
            }
            OPENAI -> {
                val body = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray()
                            .put(JSONObject().put("type", "text").put("text", prompt))
                            .put(JSONObject().put("type", "image_url").put("image_url",
                                JSONObject().put("url", "data:image/jpeg;base64,$jpegB64"))))))
                val r = post("$root/chat/completions", mapOf("Authorization" to "Bearer $key"), body)
                r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
            }
            else -> { // Gemini
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject()
                        .put("parts", JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(JSONObject().put("inline_data", JSONObject()
                                .put("mime_type", "image/jpeg").put("data", jpegB64))))))
                val r = post("$root/v1beta/models/$model:generateContent", mapOf("x-goog-api-key" to key), body)
                val parts = r.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                (0 until parts.length()).map { parts.getJSONObject(it).optString("text") }.joinToString("").trim()
            }
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${text.take(160)}")
        return JSONObject(text)
    }
}
