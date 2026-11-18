# Sovra

An open-source, offline-first Android Text-to-Speech (TTS) application powered by **Sherpa-ONNX** and local neural voice models.

Sovra converts written text into natural-sounding speech directly on your Android device—with zero cloud processing, no accounts, and full user privacy.

## Features

- **100% Offline Synthesis:** Generate speech locally on your device without sending text to any external server.
- **Multi-Engine Support:** Run **Kokoro**, **Supertonic**, **Piper/VITS**, and **MMS** voice models in one app.
- **System-Wide Android TTS:** Registers as a native Android TTS engine so you can use Sovra voices inside e-book readers, accessibility tools, and other apps.
- **Expressive Controls:** Supports emotion tags (`[whispers]`, `[angry]`, `[sad]`, `[sarcastically]`, `[giggles]`), smart punctuation pauses, and adjustable pitch, speed, and silence scaling.
- **Document Import & Export:** Import `.txt` or `.pdf` files directly, manage generated audio in a built-in library, and export high-quality `.wav` files.
- **Model Manager:** Download, preview, organize, or import custom ONNX voice models locally.

## Supported Model Families

- **Kokoro:** Multi-language, multi-speaker neural voices (24 kHz).
- **Supertonic:** Multi-profile expressive voices across 27 languages.
- **Piper / VITS:** Lightweight, fast single and multi-speaker voices.
- **MMS:** Broad multilingual language coverage.

## Built With

- **Language & UI:** Java 11, Android XML Layouts, View Binding, Material Components
- **Inference Engine:** Sherpa-ONNX & ONNX Runtime (`arm64-v8a`, `x86_64`)
- **Utilities:** OkHttp, Gson, Apache Commons Compress, PDFBox Android

## Privacy

All speech generation happens locally on your device. Internet access is used solely when you choose to download voice models or check model catalogs.

## License

Licensed under the **Apache License 2.0**. Third-party libraries and voice models retain their respective licenses.

## Acknowledgements

Built on top of the open-source work by the **k2-fsa / Sherpa-ONNX** community and the creators of Kokoro, Supertonic, Piper, and MMS models.
