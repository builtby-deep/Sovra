package com.builtbydeep.sovra;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;
import com.k2fsa.sherpa.onnx.GenerationConfig;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SupertonicEngine implements AutoCloseable {
    public static final int DEFAULT_NUM_STEPS = 16;
    private static final String[] PROVIDERS = {"xnnpack", "cpu"};
    private static final Set<String> SUPPORTED_LANGUAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es",
                    "et", "fi", "fr", "hi", "hr", "hu", "id", "it", "lt", "lv",
                    "nl", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "vi")));

    private OfflineTts tts;
    private int sampleRate;

    public synchronized void initialize(
            android.content.Context context, int numThreads) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        File filesDirectory = context.getFilesDir();
        SupertonicModelFiles files = new SupertonicModelFiles(filesDirectory);
        if (!files.isComplete()) {
            throw new IOException("Supertonic model is incomplete or tts.json is invalid");
        }

        TtsEngineUtils.ensureNativeLibraryLoaded();
        OfflineTtsSupertonicModelConfig supertonic = new OfflineTtsSupertonicModelConfig();
        supertonic.setDurationPredictor(
                files.getFile(SupertonicModelFiles.DURATION_PREDICTOR).getAbsolutePath());
        supertonic.setTextEncoder(
                files.getFile(SupertonicModelFiles.TEXT_ENCODER).getAbsolutePath());
        supertonic.setVectorEstimator(
                files.getFile(SupertonicModelFiles.VECTOR_ESTIMATOR).getAbsolutePath());
        supertonic.setVocoder(files.getFile(SupertonicModelFiles.VOCODER).getAbsolutePath());
        supertonic.setTtsJson(files.getFile(SupertonicModelFiles.TTS_JSON).getAbsolutePath());
        supertonic.setUnicodeIndexer(
                files.getFile(SupertonicModelFiles.UNICODE_INDEXER).getAbsolutePath());
        supertonic.setVoiceStyle(files.getFile(SupertonicModelFiles.VOICE).getAbsolutePath());
        Throwable lastFailure = null;
        OfflineTts selected = null;
        int selectedSampleRate = 0;

        for (String provider : PROVIDERS) {
            OfflineTts candidate = null;
            try {
                OfflineTtsModelConfig model = new OfflineTtsModelConfig();
                model.setSupertonic(supertonic);
                model.setNumThreads(Math.max(1, numThreads));
                model.setProvider(provider);
                model.setDebug(false);
                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(model);
                config.setMaxNumSentences(1);
                candidate = new OfflineTts(context.getAssets(), config);
                GeneratedAudio smoke = candidate.generateWithConfig(
                        "Hello.", createGenerationConfig(1, 1.0f, "en", DEFAULT_NUM_STEPS));
                validateSmokeTest(smoke);
                selected = candidate;
                selectedSampleRate = candidate.sampleRate();
                break;
            } catch (Throwable failure) {
                lastFailure = failure;
                if (candidate != null) {
                    try {
                        candidate.release();
                    } catch (Throwable releaseFailure) {
                        failure.addSuppressed(releaseFailure);
                    }
                }
            }
        }
        if (selected == null) {
            IllegalStateException failure =
                    new IllegalStateException("Supertonic failed with providers xnnpack and cpu",
                            lastFailure);
            throw failure;
        }

        OfflineTts previous = tts;
        tts = selected;
        sampleRate = selectedSampleRate;
        if (previous != null) {
            previous.release();
        }
    }

    public synchronized byte[] generate(
            String text,
            int activeSpeakerId,
            float speed,
            String language,
            int steps,
            float pitch) {
        if (tts == null) {
            throw new IllegalStateException("Supertonic engine has not been initialized");
        }
        if (text == null || text.trim().isEmpty()) {
            return new byte[0];
        }
        GenerationConfig config = createGenerationConfig(
                activeSpeakerId, speed, language, steps);
        try {
            GeneratedAudio generated = tts.generateWithConfig(text.trim(), config);
            return TtsEngineUtils.toPcm16(generated,
                    Float.isFinite(pitch) && pitch > 0.0f ? pitch : 1.0f);
        } catch (Throwable failure) {
            throw new IllegalStateException("Supertonic speech generation failed", failure);
        }
    }

    public static GenerationConfig createGenerationConfig(
            int speakerId, float speed, String language, int steps) {
        GenerationConfig config = new GenerationConfig();
        config.setSid(Math.max(0, Math.min(9, speakerId)));
        config.setSpeed(Float.isFinite(speed) && speed > 0.0f ? speed : 1.0f);
        config.setNumSteps(Math.max(1, Math.min(16, steps)));
        Map<String, String> extra = new HashMap<>();
        extra.put("lang", normalizeLanguage(language));
        config.setExtra(extra);
        return config;
    }

    public static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "en";
        }
        String normalized = language.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf('-');
        if (separator >= 0) {
            normalized = normalized.substring(0, separator);
        }
        return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : "en";
    }

    public synchronized int getSampleRate() {
        if (tts == null) {
            throw new IllegalStateException("Supertonic engine has not been initialized");
        }
        return sampleRate;
    }

    private static void validateSmokeTest(GeneratedAudio audio) {
        if (audio == null || audio.getSampleRate() <= 0
                || audio.getSamples() == null || audio.getSamples().length == 0) {
            throw new IllegalStateException("Supertonic smoke test produced no valid audio");
        }
    }

    @Override
    public synchronized void close() {
        release();
    }

    public synchronized void release() {
        if (tts != null) {
            tts.release();
            tts = null;
            sampleRate = 0;
        }
    }
}
