package com.builtbydeep.sovra;

import java.io.File;
import java.io.IOException;

public final class KokoroModelFiles {
    public static final String DIRECTORY_NAME = "kokoro-multi-lang-v1_0";
    public static final String BASE_URL =
            "https://huggingface.co/seepa22/Kokoro-82M/resolve/main/kokoro-multi-lang/";
    public static final String MODEL = "model.onnx";
    public static final String TOKENS = "tokens.txt";
    public static final String VOICES = "voices.bin";
    private static final String[] REQUIRED_FILES = {MODEL, TOKENS, VOICES};

    private final File directory;

    public KokoroModelFiles(File filesDirectory) {
        this.directory = new File(new File(filesDirectory, "models"), DIRECTORY_NAME);
    }

    public File getDirectory() {
        return directory;
    }

    public File getModel() {
        return new File(directory, MODEL);
    }

    public File getTokens() {
        return new File(directory, TOKENS);
    }

    public File getVoices() {
        return new File(directory, VOICES);
    }

    public boolean isComplete() {
        return TtsEngineUtils.hasNonEmptyFiles(directory, REQUIRED_FILES);
    }

    public void download(ModelFileDownloader.ProgressListener listener) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create Kokoro model directory: " + directory);
        }
        for (String filename : REQUIRED_FILES) {
            File output = new File(directory, filename);
            if (!output.isFile() || output.length() == 0) {
                ModelFileDownloader.downloadAtomically(
                        BASE_URL + filename, output, 30000, 60000, "Sovra", listener);
            }
        }
        if (!isComplete()) {
            throw new IOException("Kokoro model download did not produce all required files");
        }
    }
}
