package com.builtbydeep.sovra;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class ModelFileDownloader {
    private static final int BUFFER_SIZE = 32 * 1024;

    private ModelFileDownloader() {
    }

    static void downloadAtomically(
            String url,
            File destination,
            int connectTimeoutMs,
            int readTimeoutMs,
            String userAgent,
            ProgressListener listener) throws IOException {
        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Unable to create download directory for " + destination);
        }
        File staging = new File(destination.getAbsolutePath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setInstanceFollowRedirects(true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("Download failed with HTTP " + responseCode + ": " + url);
            }
            long totalBytes = connection.getContentLengthLong();
            long downloaded = 0;
            if (listener != null) {
                listener.onProgress(destination.getName(), 0, totalBytes);
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new FileOutputStream(staging)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("Model download cancelled");
                    }
                    output.write(buffer, 0, count);
                    downloaded += count;
                    if (listener != null) {
                        listener.onProgress(destination.getName(), downloaded, totalBytes);
                    }
                }
            }
            if (!staging.isFile() || staging.length() == 0) {
                throw new IOException("Downloaded file is empty: " + url);
            }
            TtsEngineUtils.moveFileAtomically(staging, destination);
        } catch (IOException | RuntimeException error) {
            if (staging.exists() && !staging.delete()) {
                error.addSuppressed(new IOException("Unable to remove partial download: " + staging));
            }
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    interface ProgressListener {
        void onProgress(String filename, long downloadedBytes, long totalBytes);
    }
}
