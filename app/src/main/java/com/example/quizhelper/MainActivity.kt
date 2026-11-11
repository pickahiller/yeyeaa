package com.example.quizhelper

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }

        root.addView(label("AI provider"))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, AiClient.providers)
            setSelection(AiClient.providers.indexOf(prefs.getString("provider", AiClient.ANTHROPIC)).coerceAtLeast(0))
        }
        root.addView(spinner)

        root.addView(label("Base URL (OpenAI-compatible: OpenAI, OpenRouter, Groq, Ollama…)"))
        val base = EditText(this).apply { inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT }
        root.addView(base, match, wrap)

        root.addView(label("Model (must support image input)"))
        val model = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT }
        root.addView(model, match, wrap)

        root.addView(label("API key"))
        val key = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(key, match, wrap)

        fun load(p: String) {
            base.setText(prefs.getString("base_$p", AiClient.defaultBase(p)))
            model.setText(prefs.getString("model_$p", AiClient.defaultModel(p)))
            key.setText(prefs.getString("key_$p", ""))
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) =
                load(AiClient.providers[pos])
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                val p = AiClient.providers[spinner.selectedItemPosition]
                prefs.edit()
                    .putString("provider", p)
                    .putString("base_$p", base.text.toString().trim())
                    .putString("model_$p", model.text.toString().trim())
                    .putString("key_$p", key.text.toString().trim())
                    .apply()
                Toast.makeText(context, "Saved — using $p", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(label("Then turn on “Quiz Helper” under Accessibility. If it's greyed out: " +
                "App info → ⋮ → Allow restricted settings."))
        root.addView(Button(this).apply {
            text = "Open Accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(label("Double-press Volume Down on a screen with a multiple-choice question. " +
                "The status-bar icon becomes A/B/C/D."))

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
