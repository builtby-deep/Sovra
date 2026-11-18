package com.builtbydeep.sovra;

import android.content.Context;

import com.k2fsa.sherpa.onnx.GeneratedAudio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class TtsEngineUtils {
    private static final int BUFFER_SIZE = 32 * 1024;
    private static volatile boolean nativeLoadAttempted;
    private static volatile UnsatisfiedLinkError nativeLoadFailure;

    private TtsEngineUtils() {
    }

    static void ensureNativeLibraryLoaded() {
        if (!nativeLoadAttempted) {
            synchronized (TtsEngineUtils.class) {
                if (!nativeLoadAttempted) {
                    try {
                        System.loadLibrary("sherpa-onnx-jni");
                    } catch (UnsatisfiedLinkError error) {
                        nativeLoadFailure = error;
                    } finally {
                        nativeLoadAttempted = true;
                    }
                }
            }
        }
        if (nativeLoadFailure != null) {
            throw new IllegalStateException("Unable to load sherpa-onnx-jni", nativeLoadFailure);
        }
    }

    static byte[] toPcm16(GeneratedAudio audio, float pitch) {
        if (audio == null || audio.getSamples() == null) {
            throw new IllegalArgumentException("The TTS engine returned no audio samples");
        }
        float[] samples = audio.getSamples();
        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float value = Math.max(-1.0f, Math.min(1.0f, samples[i]));
            pcm[i] = (short) Math.round(value * 32767.0f);
        }

        if (pitch != 1.0f && audio.getSampleRate() > 0 && pcm.length > 0) {
            Sonic sonic = new Sonic(audio.getSampleRate(), 1);
            sonic.setSpeed(1.0f);
            sonic.setRate(1.0f);
            sonic.setVolume(1.0f);
            sonic.setPitch(pitch);
            sonic.writeShortToStream(pcm, pcm.length);
            sonic.flushStream();
            short[] shifted = new short[sonic.samplesAvailable()];
            sonic.readShortFromStream(shifted, shifted.length);
            pcm = shifted;
        }

        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2] = (byte) (pcm[i] & 0xff);
            bytes[i * 2 + 1] = (byte) ((pcm[i] >>> 8) & 0xff);
        }
        return bytes;
    }

    static void extractAssetZip(Context context, String assetName, File destination)
            throws IOException {
        if (destination.isDirectory() && destination.list() != null
                && destination.list().length > 0) {
            return;
        }
        File parent = destination.getCanonicalFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Unable to create asset extraction directory: " + parent);
        }
        File staging = new File(parent, destination.getName() + ".extracting");
        deleteTree(staging);
        if (!staging.mkdirs()) {
            throw new IOException("Unable to create staging directory: " + staging);
        }

        boolean success = false;
        try (InputStream asset = context.getAssets().open(assetName);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(asset))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];
            String root = staging.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                File output = new File(staging, entry.getName());
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(root) && !canonical.equals(staging.getCanonicalPath())) {
                    throw new IOException("Unsafe path in asset archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IOException("Unable to create extracted directory: " + output);
                    }
                } else {
                    File outputParent = output.getParentFile();
                    if (outputParent == null || (!outputParent.isDirectory() && !outputParent.mkdirs())) {
                        throw new IOException("Unable to create extracted directory: " + outputParent);
                    }
                    try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(output))) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            stream.write(buffer, 0, count);
                        }
                    }
                }
                zip.closeEntry();
            }

            replaceDirectoryAtomically(staging, destination);
            success = true;
        } finally {
            if (!success) {
                deleteTree(staging);
            }
        }
    }

    static boolean hasNonEmptyFiles(File directory, String... names) {
        if (directory == null || names == null) {
            return false;
        }
        for (String name : names) {
            File file = new File(directory, name);
            if (!file.isFile() || file.length() <= 0) {
                return false;
            }
        }
        return true;
    }

    static void copy(InputStream input, File output) throws IOException {
        File parent = output.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Unable to create directory for " + output);
        }
        try (InputStream source = new BufferedInputStream(input);
             OutputStream destination = new BufferedOutputStream(new FileOutputStream(output))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = source.read(buffer)) != -1) {
                destination.write(buffer, 0, count);
            }
        }
    }

    static void copyFile(File source, File destination) throws IOException {
        try (InputStream input = new FileInputStream(source)) {
            copy(input, destination);
        }
    }

    static void moveFileAtomically(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void replaceDirectoryAtomically(File staging, File destination) throws IOException {
        if (!destination.exists()) {
            moveFileAtomically(staging, destination);
            return;
        }
        File backup = new File(destination.getAbsolutePath() + ".previous");
        deleteTree(backup);
        moveDirectoryWithoutReplacement(destination, backup);
        try {
            moveDirectoryWithoutReplacement(staging, destination);
        } catch (IOException exception) {
            try {
                if (!destination.exists()) {
                    moveDirectoryWithoutReplacement(backup, destination);
                }
            } catch (IOException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
        deleteTree(backup);
    }

    private static void moveDirectoryWithoutReplacement(File source, File destination)
            throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), destination.toPath());
        }
    }

    static void deleteTree(File path) throws IOException {
        if (!path.exists()) {
            return;
        }
        File[] children = path.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!path.delete()) {
            throw new IOException("Unable to remove temporary path: " + path);
        }
    }
}
