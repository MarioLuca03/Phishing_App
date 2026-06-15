# Phishing App

Android application that helps users detect phishing URLs and suspicious emails using rule-based analysis combined with a machine learning model trained for URL classification.

## Features

- **URL analysis** — heuristic checks (HTTPS, redirects, suspicious parameters, domain patterns) combined with an ML risk score
- **Email analysis** — detects common phishing patterns in email text (urgency, fake login links, credential requests)
- **Clipboard monitoring** — optional background scanning of copied URLs via accessibility services
- **SMS scanning** — flags suspicious links in text messages
- **Image OCR** — extracts and analyzes text from images (e.g. screenshots of suspicious messages)
- **Risk scoring** — clear safe / suspicious / dangerous classification with detailed findings

## Machine learning

The URL scorer uses a **Random Forest** model trained in a separate ML project (Python / scikit-learn) on public phishing datasets (PhishTank, malicious URL datasets, safe Alexa domains).

The model is exported as `rf_url_model.json` and integrated into the app via `UrlMLScorer.kt`, which applies the same 16 URL features used during training (length, special characters, HTTPS, suspicious keywords, etc.).

## Tech stack

- Kotlin, Jetpack Compose
- Android Accessibility & Foreground Services
- ML model export (JSON) from scikit-learn Random Forest
- On-device inference in Kotlin (no cloud API required)

No API keys are required — all analysis runs on-device.

## Related work

The ML model was developed as part of an academic machine learning project and exported for mobile integration in this app.

## Note

Clipboard and accessibility features require explicit user permission in Android settings.
