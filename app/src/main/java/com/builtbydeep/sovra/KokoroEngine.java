package com.builtbydeep.sovra;

import android.content.Context;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;

import java.io.File;
import java.io.IOException;

public final class KokoroEngine implements AutoCloseable {
    public static final int KOKORO_SAMPLE_RATE = 24000;
    private static final String[] PROVIDERS = {"xnnpack", "cpu"};

    private OfflineTts tts;
    private int sampleRate;
    private int defaultSpeakerId = KokoroVoiceHelper.DEFAULT_SPEAKER_ID;

    public synchronized void initialize(Context context, int activeSpeakerId, int numThreads)
            throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        File filesDirectory = context.getFilesDir();
        KokoroModelFiles files = new KokoroModelFiles(filesDirectory);
        if (!files.isComplete()) {
            throw new IOException("Kokoro requires non-empty model.onnx, tokens.txt, and voices.bin");
        }

        File espeakRoot = new File(filesDirectory, "espeak-ng-data");
        TtsEngineUtils.extractAssetZip(context, "espeak-ng-data.zip", espeakRoot);
        File espeakData = new File(espeakRoot, "espeak-ng-data");
        if (!new File(espeakData, "phontab").isFile()) {
            espeakData = espeakRoot;
        }

        File frontendRoot = new File(filesDirectory, "tts_frontend_data");
        TtsEngineUtils.extractAssetZip(context, "tts_frontend.zip", frontendRoot);
        File lexicon = findFile(frontendRoot, "lexicon-us-en.txt");
        if (lexicon == null || !lexicon.isFile() || lexicon.length() == 0) {
            throw new IOException("Kokoro lexicon-us-en.txt is missing from tts_frontend.zip");
        }
        if (!new File(espeakData, "phontab").isFile()) {
            throw new IOException("Kokoro espeak-ng data is missing phontab");
        }

        TtsEngineUtils.ensureNativeLibraryLoaded();
        int speakerId = activeSpeakerId >= 0 && activeSpeakerId < KokoroVoiceHelper.getVoices().size()
                ? activeSpeakerId : KokoroVoiceHelper.DEFAULT_SPEAKER_ID;
        Throwable lastFailure = null;
        OfflineTts selected = null;
        int selectedSampleRate = 0;

        for (String provider : PROVIDERS) {
            OfflineTts candidate = null;
            try {
                OfflineTtsKokoroModelConfig kokoro = new OfflineTtsKokoroModelConfig();
                kokoro.setModel(files.getModel().getAbsolutePath());
                kokoro.setTokens(files.getTokens().getAbsolutePath());
                kokoro.setVoices(files.getVoices().getAbsolutePath());
                kokoro.setDataDir(espeakData.getAbsolutePath());
                kokoro.setLang("en-us");
                kokoro.setLexicon(lexicon.getAbsolutePath());
                OfflineTtsModelConfig model = new OfflineTtsModelConfig();
                model.setKokoro(kokoro);
                model.setNumThreads(Math.max(1, numThreads));
                model.setProvider(provider);
                model.setDebug(false);
                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(model);
                config.setMaxNumSentences(1);
                config.setSilenceScale(0.2f);
                candidate = new OfflineTts(context.getAssets(), config);
                GeneratedAudio smoke = candidate.generate("Hello.", speakerId, 1.0f);
                validateSmokeTest(smoke, candidate.sampleRate());
                selected = candidate;
                selectedSampleRate = smoke.getSampleRate();
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
                    new IllegalStateException("Kokoro failed with providers xnnpack and cpu",
                            lastFailure);
            throw failure;
        }

        OfflineTts previous = tts;
        tts = selected;
        sampleRate = selectedSampleRate;
        defaultSpeakerId = speakerId;
        if (previous != null) {
            previous.release();
        }
    }

    public synchronized byte[] generate(String text, int speakerId, float speed, float pitch) {
        if (tts == null) {
            throw new IllegalStateException("Kokoro engine has not been initialized");
        }
        if (text == null || text.trim().isEmpty()) {
            return new byte[0];
        }
        float safeSpeed = Float.isFinite(speed) && speed > 0.0f ? speed : 1.0f;
        float safePitch = Float.isFinite(pitch) && pitch > 0.0f ? pitch : 1.0f;
        try {
            GeneratedAudio generated = tts.generate(text.trim(),
                    speakerId >= 0 ? speakerId : defaultSpeakerId, safeSpeed);
            if (generated.getSampleRate() != KOKORO_SAMPLE_RATE) {
                throw new IllegalStateException("Kokoro output must be exactly 24000 Hz");
            }
            return TtsEngineUtils.toPcm16(generated, safePitch);
        } catch (Throwable failure) {
            throw new IllegalStateException("Kokoro speech generation failed", failure);
        }
    }

    public synchronized int getSampleRate() {
        if (tts == null) {
            throw new IllegalStateException("Kokoro engine has not been initialized");
        }
        return sampleRate;
    }

    private static void validateSmokeTest(GeneratedAudio audio, int reportedSampleRate) {
        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            throw new IllegalStateException("Kokoro smoke test produced no samples");
        }
        if (reportedSampleRate != KOKORO_SAMPLE_RATE
                || audio.getSampleRate() != KOKORO_SAMPLE_RATE) {
            throw new IllegalStateException("Kokoro output sample rate is not 24000 Hz");
        }
    }

    private static File findFile(File root, String filename) {
        File direct = new File(root, filename);
        if (direct.isFile()) {
            return direct;
        }
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File found = findFile(child, filename);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
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
