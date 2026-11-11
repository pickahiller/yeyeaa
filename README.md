# Quiz Helper
Double-press Volume Down -> screenshot -> AI answers -> A/B/C/D icon in the status bar.

## Build on GitHub
1. Create a repo and push this folder (the contents of `QuizHelper/`, so `app/` and `.github/` are at the repo root).
2. Open the **Actions** tab -> **Build APK** -> wait for the green check.
3. Open the run -> **Artifacts** -> download `QuizHelper-debug-apk`, unzip, install the APK.
   (Tag a commit `v1.0` and the APK is also attached to a GitHub Release.)

## Providers
Pick in the app. Keys are typed into the app only; never commit them.
- Anthropic: any Claude vision model
- OpenAI-compatible: set Base URL + model
    - OpenAI      https://api.openai.com/v1
    - OpenRouter  https://openrouter.ai/api/v1
    - Groq        https://api.groq.com/openai/v1
    - Ollama      http://<pc-ip>:11434/v1
- Gemini: any Gemini model

The model must accept image input.
