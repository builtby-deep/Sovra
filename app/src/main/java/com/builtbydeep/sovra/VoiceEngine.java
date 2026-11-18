package com.builtbydeep.sovra;

import android.content.Context;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceEngine implements AutoCloseable {
    private static final String[] PROVIDERS = {"xnnpack", "cpu"};
    private static final Pattern ALPHABETIC_TOKEN = Pattern.compile("[A-Za-z]+");

    private OfflineTts tts;
    private int sampleRate;
    private String modelType;

    public synchronized void initialize(
            Context context,
            String modelPath,
            String tokensPath,
            String modelType,
            String language,
            int numThreads,
            float noiseScale,
            float noiseScaleW) throws IOException {
        if (context == null || modelPath == null || tokensPath == null || modelType == null) {
            throw new IllegalArgumentException("Context, model, tokens, and model type are required");
        }
        File modelFile = new File(modelPath);
        File tokensFile = new File(tokensPath);
        if (!modelFile.isFile() || modelFile.length() == 0
                || !tokensFile.isFile() || tokensFile.length() == 0) {
            throw new IOException("VITS model and tokens must be non-empty regular files");
        }

        String normalizedType = modelType.trim().toLowerCase(java.util.Locale.ROOT);
        String primaryLanguage = language == null ? ""
                : language.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        boolean chinese = primaryLanguage.equals("zh") || primaryLanguage.startsWith("zh-");
        boolean mms = normalizedType.equals("mms");
        File espeakRoot = new File(context.getFilesDir(), "espeak-ng-data");
        TtsEngineUtils.extractAssetZip(context, "espeak-ng-data.zip", espeakRoot);
        File espeakData = new File(espeakRoot, "espeak-ng-data");
        if (!new File(espeakData, "phontab").isFile()) {
            espeakData = espeakRoot;
        }
        if (!new File(espeakData, "phontab").isFile()) {
            throw new IOException("VITS requires bundled espeak-ng data");
        }
        String dataDir = espeakData.getAbsolutePath();
        String lexicon = "";
        String dictDir = "";
        String smokeText;

        if (chinese && !mms) {
            File helperRoot = new File(context.getFilesDir(), "chinese_helper_data");
            TtsEngineUtils.extractAssetZip(context, "chinese_helper.zip", helperRoot);
            File helperFiles = findDirectory(helperRoot, "phone.fst");
            if (helperFiles == null) {
                helperFiles = helperRoot;
            }
            if (!TtsEngineUtils.hasNonEmptyFiles(helperFiles,
                    "phone.fst", "date.fst", "new_heteronym.fst", "lexicon.txt")) {
                throw new IOException("Chinese VITS support files are incomplete");
            }
            File dict = new File(helperFiles, "dict");
            if (!dict.isDirectory()) {
                throw new IOException("Chinese VITS dictionary directory is missing");
            }
            lexicon = new File(helperFiles, "lexicon.txt").getAbsolutePath();
            dictDir = dict.getAbsolutePath();
            smokeText = "测试";
        } else if (mms) {
            smokeText = firstAlphabeticToken(tokensFile);
        } else {
            smokeText = "Hello";
        }

        TtsEngineUtils.ensureNativeLibraryLoaded();
        OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
        vits.setModel(modelFile.getAbsolutePath());
        vits.setTokens(tokensFile.getAbsolutePath());
        vits.setNoiseScale(noiseScale);
        vits.setNoiseScaleW(noiseScaleW);
        vits.setLengthScale(1.0f);
        vits.setDataDir(dataDir);
        vits.setLexicon(lexicon);
        vits.setDictDir(dictDir);

        Throwable lastFailure = null;
        OfflineTts selected = null;
        int selectedSampleRate = 0;
        for (String provider : PROVIDERS) {
            OfflineTts candidate = null;
            try {
                OfflineTtsModelConfig model = new OfflineTtsModelConfig();
                model.setVits(vits);
                model.setNumThreads(Math.max(1, numThreads));
                model.setProvider(provider);
                model.setDebug(false);
                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(model);
                config.setMaxNumSentences(5);
                candidate = new OfflineTts(context.getAssets(), config);
                GeneratedAudio smoke = candidate.generate(smokeText, 0, 1.0f);
                if (smoke == null || smoke.getSamples() == null || smoke.getSamples().length == 0
                        || smoke.getSampleRate() <= 0) {
                    throw new IllegalStateException("VITS smoke test produced no valid audio");
                }
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
                    new IllegalStateException("VITS failed with providers xnnpack and cpu",
                            lastFailure);
            throw failure;
        }

        OfflineTts previous = tts;
        tts = selected;
        sampleRate = selectedSampleRate;
        this.modelType = normalizedType;
        if (previous != null) {
            previous.release();
        }
    }

    public synchronized byte[] generate(String text, int speakerId, float speed, float pitch) {
        if (tts == null) {
            throw new IllegalStateException("VITS engine has not been initialized");
        }
        if (text == null || text.trim().isEmpty()) {
            return new byte[0];
        }
        float safeSpeed = Float.isFinite(speed) && speed > 0.0f ? speed : 1.0f;
        float safePitch = Float.isFinite(pitch) && pitch > 0.0f ? pitch : 1.0f;
        try {
            GeneratedAudio generated = tts.generate(
                    text.trim(), Math.max(0, speakerId), safeSpeed);
            return TtsEngineUtils.toPcm16(generated, safePitch);
        } catch (Throwable failure) {
            throw new IllegalStateException("VITS speech generation failed", failure);
        }
    }

    public synchronized int getSampleRate() {
        if (tts == null) {
            throw new IllegalStateException("VITS engine has not been initialized");
        }
        return sampleRate;
    }

    public synchronized String getModelType() {
        if (tts == null) {
            throw new IllegalStateException("VITS engine has not been initialized");
        }
        return modelType;
    }

    private static String firstAlphabeticToken(File tokensFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(tokensFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ALPHABETIC_TOKEN.matcher(line);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        throw new IOException("MMS tokens.txt contains no alphabetic smoke-test token");
    }

    private static File findDirectory(File root, String filename) {
        if (new File(root, filename).isFile()) {
            return root;
        }
        File[] children = root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File found = findDirectory(child, filename);
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
            modelType = null;
        }
    }
}
