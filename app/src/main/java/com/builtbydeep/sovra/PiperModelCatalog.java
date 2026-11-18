package com.builtbydeep.sovra;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PiperModelCatalog {
    public static final String BASE_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";
    private static final List<Model> MODELS;

    static {
        List<Model> models = new ArrayList<>();
        models.add(new Model("Libritts-high", "English", "Multi", "110 MB",
                "vits-piper-en_US-libritts-high.tar.bz2", "libritts-high"));
        models.add(new Model("Glados-high", "English", "Female", "96 MB",
                "vits-piper-en_US-glados-high.tar.bz2", "glados-high"));
        models.add(new Model("Ljspeech-high", "English", "Female", "96 MB",
                "vits-piper-en_US-ljspeech-high.tar.bz2", "ljspeech-high"));
        models.add(new Model("Ryan-high", "English", "Male", "102 MB",
                "vits-piper-en_US-ryan-high.tar.bz2", "ryan-high"));
        models.add(new Model("Lessac-high", "English", "Female", "96 MB",
                "vits-piper-en_US-lessac-high.tar.bz2", "lessac-high"));
        models.add(new Model("Cori-high", "English UK", "Female", "96 MB",
                "vits-piper-en_GB-cori-high.tar.bz2", "cori-high"));
        models.add(new Model("Pratham-medium", "Hindi", "Male", "62 MB",
                "vits-piper-hi_IN-pratham-medium.tar.bz2", "pratham-medium"));
        models.add(new Model("Priyamvada-medium", "Hindi", "Female", "62 MB",
                "vits-piper-hi_IN-priyamvada-medium.tar.bz2", "priyamvada-medium"));
        models.add(new Model("Rohan-medium", "Hindi", "Male", "62 MB",
                "vits-piper-hi_IN-rohan-medium.tar.bz2", "rohan-medium"));
        MODELS = Collections.unmodifiableList(models);
    }

    private PiperModelCatalog() {
    }

    public static List<Model> getModels() {
        return MODELS;
    }

    public static Model getModel(String archiveName) {
        for (Model model : MODELS) {
            if (model.archiveName.equals(archiveName) || model.name.equals(archiveName)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown built-in Piper model: " + archiveName);
    }

    public static File downloadAndExtract(
            Model model,
            File filesDirectory,
            ModelFileDownloader.ProgressListener listener) throws IOException {
        if (model == null || !MODELS.contains(model)) {
            throw new IllegalArgumentException("A built-in Piper model is required");
        }
        File modelsDirectory = new File(filesDirectory, "models");
        File downloadsDirectory = new File(modelsDirectory, ".downloads");
        if (!downloadsDirectory.isDirectory() && !downloadsDirectory.mkdirs()) {
            throw new IOException("Unable to create Piper download directory");
        }
        File archive = new File(downloadsDirectory, model.archiveName);
        ModelFileDownloader.downloadAtomically(
                BASE_URL + model.archiveName, archive, 30000, 60000, "Sovra", listener);

        File destination = new File(modelsDirectory, model.modelDirectory);
        File staging = new File(modelsDirectory, model.modelDirectory + ".extracting");
        TtsEngineUtils.deleteTree(staging);
        if (!staging.mkdirs()) {
            throw new IOException("Unable to create Piper extraction directory: " + staging);
        }
        boolean installed = false;
        try {
            extractArchive(archive, staging, listener);
            if (!TtsEngineUtils.hasNonEmptyFiles(staging, "model.onnx", "tokens.txt")) {
                throw new IOException("Piper archive is missing a non-empty ONNX model or tokens file");
            }
            TtsEngineUtils.replaceDirectoryAtomically(staging, destination);
            installed = true;
            return destination;
        } finally {
            if (!installed && staging.exists()) {
                TtsEngineUtils.deleteTree(staging);
            }
            if (installed && archive.exists() && !archive.delete()) {
                throw new IOException("Unable to remove downloaded Piper archive: " + archive);
            }
        }
    }

    private static void extractArchive(
            File archive, File staging, ModelFileDownloader.ProgressListener listener)
            throws IOException {
        String root = staging.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[32 * 1024];
        boolean modelWritten = false;
        boolean tokensWritten = false;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new BZip2CompressorInputStream(new BufferedInputStream(
                        new java.io.FileInputStream(archive))))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Piper model extraction cancelled");
                }
                File resolved = new File(staging, entry.getName());
                String resolvedPath = resolved.getCanonicalPath();
                if (!resolvedPath.startsWith(root) && !resolvedPath.equals(staging.getCanonicalPath())) {
                    throw new IOException("Unsafe path in Piper archive: " + entry.getName());
                }
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw new IOException("Links are not allowed in Piper archives: " + entry.getName());
                }
                String entryName = entry.getName().replace('\\', '/');
                String lowerName = entryName.toLowerCase(java.util.Locale.ROOT);
                File output = null;
                if (!entry.isDirectory() && lowerName.endsWith(".onnx") && !modelWritten) {
                    output = new File(staging, "model.onnx");
                    modelWritten = true;
                } else if (!entry.isDirectory()
                        && (lowerName.endsWith("/tokens.txt")
                        || lowerName.equals("tokens.txt")
                        || lowerName.endsWith(".tokens"))
                        && !tokensWritten) {
                    output = new File(staging, "tokens.txt");
                    tokensWritten = true;
                }
                if (output != null) {
                    if (!output.getCanonicalPath().startsWith(root)) {
                        throw new IOException("Unsafe output path in Piper archive");
                    }
                    try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = tar.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedIOException(
                                        "Piper model extraction cancelled");
                            }
                            stream.write(buffer, 0, count);
                        }
                    }
                    if (listener != null) {
                        listener.onProgress(output.getName(), output.length(), entry.getSize());
                    }
                }
            }
        }
        if (!modelWritten || !tokensWritten) {
            throw new IOException("Piper archive did not contain both a model and tokens file");
        }
    }

    public static final class Model {
        public final String name;
        public final String language;
        public final String voice;
        public final String size;
        public final String archiveName;
        public final String modelDirectory;

        private Model(
                String name,
                String language,
                String voice,
                String size,
                String archiveName,
                String modelDirectory) {
            this.name = name;
            this.language = language;
            this.voice = voice;
            this.size = size;
            this.archiveName = archiveName;
            this.modelDirectory = modelDirectory;
        }

        public String getDownloadUrl() {
            return BASE_URL + archiveName;
        }
    }
}
