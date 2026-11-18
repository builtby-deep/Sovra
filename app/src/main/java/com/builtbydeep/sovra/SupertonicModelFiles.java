package com.builtbydeep.sovra;

import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public final class SupertonicModelFiles {
    public static final String DIRECTORY_NAME = "supertonic";
    public static final String BASE_URL =
            "https://huggingface.co/seepa22/Supertonic-voxsherpa/resolve/main/supertonic-v3/";
    public static final String DURATION_PREDICTOR = "duration_predictor.onnx";
    public static final String TEXT_ENCODER = "text_encoder.onnx";
    public static final String VECTOR_ESTIMATOR = "vector_estimator.onnx";
    public static final String VOCODER = "vocoder.onnx";
    public static final String TTS_JSON = "tts.json";
    public static final String UNICODE_INDEXER = "unicode_indexer.bin";
    public static final String VOICE = "voice.bin";
    private static final String[] REQUIRED_FILES = {
            DURATION_PREDICTOR, TEXT_ENCODER, VECTOR_ESTIMATOR, VOCODER,
            TTS_JSON, UNICODE_INDEXER, VOICE
    };

    private final File directory;

    public SupertonicModelFiles(File filesDirectory) {
        this.directory = new File(new File(filesDirectory, "models"), DIRECTORY_NAME);
    }

    public File getDirectory() {
        return directory;
    }

    public File getFile(String filename) {
        for (String required : REQUIRED_FILES) {
            if (required.equals(filename)) {
                return new File(directory, filename);
            }
        }
        throw new IllegalArgumentException("Unknown Supertonic model file: " + filename);
    }

    public boolean isComplete() {
        if (!TtsEngineUtils.hasNonEmptyFiles(directory, REQUIRED_FILES)) {
            return false;
        }
        try (FileReader reader = new FileReader(getFile(TTS_JSON))) {
            JsonParser.parseReader(reader);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public void download(ModelFileDownloader.ProgressListener listener) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create Supertonic model directory: " + directory);
        }
        final long aggregateSize = 400L * 1024L * 1024L;
        long aggregateDownloaded = 0;
        for (String filename : REQUIRED_FILES) {
            File output = new File(directory, filename);
            if (output.isFile() && output.length() > 0) {
                aggregateDownloaded += output.length();
            } else {
                final long completedBeforeFile = aggregateDownloaded;
                ModelFileDownloader.downloadAtomically(
                        BASE_URL + filename, output, 30000, 60000, "Sovra",
                        listener == null ? null : (downloadedName, downloadedBytes, totalBytes) ->
                                listener.onProgress("Supertonic",
                                        Math.min(aggregateSize,
                                                completedBeforeFile + downloadedBytes),
                                        aggregateSize));
                aggregateDownloaded += output.length();
            }
        }
        if (listener != null) {
            listener.onProgress("Supertonic",
                    Math.min(aggregateSize, aggregateDownloaded), aggregateSize);
        }
        if (!isComplete()) {
            throw new IOException("Supertonic files are incomplete or tts.json is invalid");
        }
    }
}
