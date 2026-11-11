package com.example.quizhelper

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class AnswerService : AccessibilityService() {

    private val io = Executors.newSingleThreadExecutor()
    private var lastPress = 0L

    companion object {
        private const val CHANNEL = "answers"
        private const val NOTIF_ID = 42
        private const val PROMPT =
            "This is a screenshot containing a multiple-choice question. Work out the correct option. " +
            "Reply in exactly this format: LETTER|one short sentence of reasoning (max 15 words). " +
            "LETTER must be A, B, C or D. If there is no multiple-choice question, reply: NONE|No question found."
    }

    override fun onServiceConnected() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Answers", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // Double-press Volume Down -> trigger
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val now = SystemClock.uptimeMillis()
        return if (now - lastPress < 500) {
            lastPress = 0
            capture()
            true            // swallow the 2nd press so volume doesn't change twice
        } else {
            lastPress = now
            false
        }
    }

    private fun capture() {
        notify(null, "Reading screen…")
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                if (bmp == null) { notify(null, "Screenshot failed"); return }
                io.execute { solve(bmp) }
            }
            override fun onFailure(errorCode: Int) = notify(null, "Screenshot failed ($errorCode)")
        })
    }

    private fun solve(src: Bitmap) {
        try {
            val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
            val provider = prefs.getString("provider", AiClient.ANTHROPIC) ?: AiClient.ANTHROPIC
            val key = prefs.getString("key_$provider", "").orEmpty()
            if (key.isBlank()) { notify(null, "Set your $provider API key in the app"); return }
            val base = prefs.getString("base_$provider", "").orEmpty().ifBlank { AiClient.defaultBase(provider) }
            val model = prefs.getString("model_$provider", "").orEmpty().ifBlank { AiClient.defaultModel(provider) }

            // shrink + JPEG-encode
            val scale = 1400f / maxOf(src.width, src.height)
            val bmp = if (scale < 1f)
                Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
            else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

            val text = AiClient.ask(provider, base, model, key, PROMPT, b64)
            val letter = Regex("""^\W*([ABCDabcd])\b""").find(text)?.groupValues?.get(1)?.uppercase()?.first()
            val explanation = if (text.contains('|')) text.substringAfter('|').trim() else text
            notify(letter, explanation)
        } catch (e: Exception) {
            notify(null, "Error: ${e.message}")
        }
    }

    private fun notify(letter: Char?, text: String) {
        val icon = when (letter) {
            'A' -> R.drawable.ic_a
            'B' -> R.drawable.ic_b
            'C' -> R.drawable.ic_c
            'D' -> R.drawable.ic_d
            else -> R.drawable.ic_q
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(if (letter != null) "Answer: $letter" else "Quiz Helper")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}
