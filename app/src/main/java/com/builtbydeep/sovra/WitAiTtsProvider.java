package com.builtbydeep.sovra;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class WitAiTtsProvider implements CloudTtsProvider {
    public static final String ID = "witai";
    public static final int CHUNK_LIMIT = 250;
    private static final String ENDPOINT = "https://api.wit.ai/synthesize?v=20240304";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final Pattern BRACKET_TAG = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern ABBREVIATION = Pattern.compile(
            "(?i)(?:mr|mrs|ms|dr|prof|sr|jr|st|vs|etc|e\\.g|i\\.e)\\.$");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private final java.util.Set<Call> activeCalls = ConcurrentHashMap.newKeySet();
    private final Object requestPacingLock = new Object();
    private long nextRequestAt;
    private volatile ExecutorService activeExecutor;

    @Override public String getId() { return ID; }
    @Override public int getSafeChunkLimit() { return CHUNK_LIMIT; }

    @Override
    public List<Voice> fetchVoices(String apiKey) {
        ArrayList<Voice> voices = new ArrayList<>();
        addVoices(voices, "en-US", "United States", "Charlie", "Rubie", "Remi",
                "Rebecca", "Cooper", "Cody");
        addVoices(voices, "en-CA", "Canada", "Rosie", "Colin");
        return Collections.unmodifiableList(voices);
    }

    private static void addVoices(List<Voice> voices, String locale, String region,
                                  String... names) {
        for (String name : names) {
            voices.add(new Voice(name, name, locale, "", region));
        }
    }

    @Override
    public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                             String language, float speed, float pitch, boolean smartPunctuation)
            throws IOException {
        return synthesize(apiKey, voice, text, 1);
    }

    public byte[] synthesize(String apiKey, Voice voice, String text, int voiceStyle)
            throws IOException {
        String cleanKey = apiKey == null ? "" : apiKey.trim();
        String cleanText = sanitizeText(text);
        if (!hasSpeakableContent(cleanText)) {
            throw new IOException("No speakable text to synthesize.");
        }
        JSONObject body = new JSONObject();
        try {
            body.put("q", cleanText);
            body.put("voice", voice.id);
            // style removed
        } catch (org.json.JSONException error) {
            throw new IOException("Unable to prepare the Wit.ai request.", error);
        }

        IOException lastError = null;
        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                awaitRequestSlot();
                Request request = new Request.Builder().url(ENDPOINT)
                        .header("Authorization", "Bearer " + cleanKey)
                        .header("Accept", "audio/mpeg")
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), JSON)).build();
                Call call = HTTP.newCall(request);
                activeCalls.add(call);
                try (Response response = call.execute()) {
                    if (response.isSuccessful()) {
                        if (response.body() == null) {
                            throw new NonRetryableException(
                                    new IOException("Wit.ai returned an empty audio response."));
                        }
                        byte[] audio = response.body().bytes();
                        if (audio.length == 0) {
                            throw new NonRetryableException(
                                    new IOException("Wit.ai returned an empty audio response."));
                        }
                        return audio;
                    }
                    String detail = response.body() != null ? response.body().string().trim() : "";
                    IOException error = new IOException("Wit.ai request failed (HTTP "
                            + response.code() + ")" + (detail.isEmpty() ? "." : ": " + detail));
                    if (response.code() != 429 && response.code() < 500) {
                        throw new NonRetryableException(error);
                    }
                    lastError = error;
                } finally {
                    activeCalls.remove(call);
                }
            } catch (NonRetryableException error) {
                throw error.getCause();
            } catch (IOException error) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Wit.ai synthesis was cancelled.", error);
                }
                lastError = error;
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(attempt == 0 ? 600L : 1200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Wit.ai synthesis was cancelled.", interrupted);
                }
            }
        }
        throw lastError == null ? new IOException("Wit.ai synthesis failed.") : lastError;
    }

    private void awaitRequestSlot() throws IOException {
        synchronized (requestPacingLock) {
            while (true) {
                long now = System.currentTimeMillis();
                long waitMillis = nextRequestAt - now;
                if (waitMillis <= 0L) {
                    nextRequestAt = now + 1050L;
                    return;
                }
                try {
                    requestPacingLock.wait(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Wit.ai synthesis was cancelled.", interrupted);
                }
            }
        }
    }

    public List<File> synthesizeToFiles(Context context, String apiKey, Voice voice,
                                        List<String> chunks, int voiceStyle,
                                        BooleanSupplier shouldContinue) throws IOException {
        cancelActiveRequests();
        File directory = getChunkDirectory(context);
        deleteChunkFiles(directory);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create the Wit.ai audio cache.");
        }
        if (chunks.isEmpty()) return Collections.emptyList();

        ExecutorService executor = Executors.newFixedThreadPool(3);
        activeExecutor = executor;
        CompletionService<File> completion = new ExecutorCompletionService<>(executor);
        ArrayList<Future<File>> inOrder = new ArrayList<>(chunks.size());
        ArrayList<File> files = new ArrayList<>(chunks.size());
        int nextToSubmit = 0;
        try {
            while (nextToSubmit < Math.min(3, chunks.size())) {
                inOrder.add(submitChunk(completion, directory, nextToSubmit++, apiKey,
                        voice, chunks, voiceStyle, shouldContinue));
            }
            for (int index = 0; index < chunks.size(); index++) {
                if (!shouldContinue.getAsBoolean()) {
                    throw new IOException("Wit.ai synthesis was cancelled.");
                }
                File file;
                try {
                    file = inOrder.get(index).get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Wit.ai synthesis was cancelled.", interrupted);
                } catch (java.util.concurrent.ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    throw new IOException("Unable to fetch a Wit.ai audio chunk.", cause);
                }
                files.add(file);
                if (nextToSubmit < chunks.size()) {
                    inOrder.add(submitChunk(completion, directory, nextToSubmit++, apiKey,
                            voice, chunks, voiceStyle, shouldContinue));
                }
            }
            return files;
        } catch (IOException | RuntimeException error) {
            for (Future<File> future : inOrder) future.cancel(true);
            cancelActiveRequests();
            deleteChunkFiles(directory);
            throw error;
        } finally {
            executor.shutdownNow();
            if (activeExecutor == executor) activeExecutor = null;
        }
    }

    private Future<File> submitChunk(CompletionService<File> completion, File directory,
                                     int index, String apiKey, Voice voice, List<String> chunks,
                                     int voiceStyle, BooleanSupplier shouldContinue) {
        final int chunkIndex = index;
        return completion.submit(new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (!shouldContinue.getAsBoolean()) {
                    throw new IOException("Wit.ai synthesis was cancelled.");
                }
                byte[] audio = synthesize(apiKey, voice, chunks.get(chunkIndex), voiceStyle);
                if (!shouldContinue.getAsBoolean()) {
                    throw new IOException("Wit.ai synthesis was cancelled.");
                }
                File file = new File(directory, String.format(Locale.US, "chunk-%05d.mp3", chunkIndex));
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(chunkIndex == 0 ? audio : stripId3v2(audio));
                }
                return file;
            }
        });
    }

    public void cancelActiveRequests() {
        ExecutorService executor = activeExecutor;
        if (executor != null) executor.shutdownNow();
        for (Call call : activeCalls) call.cancel();
        activeCalls.clear();
    }

    public void cancelAndClean(Context context) {
        cancelActiveRequests();
        deleteChunkFiles(getChunkDirectory(context));
    }

    private static File getChunkDirectory(Context context) {
        return new File(context.getCacheDir(), "wit_chunks");
    }

    private static void deleteChunkFiles(File directory) {
        File[] entries = directory.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) deleteChunkFiles(entry);
                if (!entry.delete() && entry.exists()) {
                    android.util.Log.w("WitAiTtsProvider",
                            "Unable to remove temporary Wit.ai file: " + entry.getName());
                }
            }
        }
        if (directory.exists() && !directory.delete() && directory.list() != null
                && directory.list().length == 0) {
            android.util.Log.w("WitAiTtsProvider", "Unable to remove the Wit.ai cache directory.");
        }
    }

    static String styleFor(int voiceStyle) {
        if (voiceStyle == 2) return "expressive";
        if (voiceStyle == 0) return "casual";
        return "formal";
    }

    public static String sanitizeText(String text) {
        String withoutTags = BRACKET_TAG.matcher(text == null ? "" : text).replaceAll(" ");
        StringBuilder cleaned = new StringBuilder(withoutTags.length());
        for (int offset = 0; offset < withoutTags.length();) {
            int codePoint = withoutTags.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isEmoji(codePoint)) cleaned.appendCodePoint(codePoint);
        }
        return cleaned.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean isEmoji(int codePoint) {
        return codePoint == 0x00A9 || codePoint == 0x00AE
                || codePoint >= 0x200D && codePoint <= 0x200F
                || codePoint >= 0x20D0 && codePoint <= 0x20FF
                || codePoint >= 0x2600 && codePoint <= 0x27BF
                || codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint >= 0x1F000 && codePoint <= 0x1FAFF
                || codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    public static boolean containsDevanagari(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= '\u0900' && text.charAt(i) <= '\u097F') return true;
        }
        return false;
    }

    public static List<String> splitIntoChunks(String text) {
        String remaining = sanitizeText(text);
        ArrayList<String> chunks = new ArrayList<>();
        while (remaining.length() > CHUNK_LIMIT) {
            int end = chooseBoundary(remaining, CHUNK_LIMIT);
            if (end > 0 && end < remaining.length()
                    && Character.isHighSurrogate(remaining.charAt(end - 1))) end--;
            String chunk = remaining.substring(0, end).trim();
            if (chunk.isEmpty()) {
                end = CHUNK_LIMIT;
                if (Character.isHighSurrogate(remaining.charAt(end - 1))) end--;
                chunk = remaining.substring(0, end);
            }
            if (hasSpeakableContent(chunk)) chunks.add(chunk);
            remaining = remaining.substring(end).trim();
        }
        if (!remaining.isEmpty() && hasSpeakableContent(remaining)) chunks.add(remaining);
        return Collections.unmodifiableList(chunks);
    }

    private static boolean hasSpeakableContent(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) return true;
        }
        return false;
    }

    private static int chooseBoundary(String text, int limit) {
        int start = Math.max(0, limit - 1);
        for (int i = start; i >= 0; i--) {
            char current = text.charAt(i);
            if (current != '.' && current != '?' && current != '!') continue;
            if (current == '.' && isDecimalPoint(text, i)) continue;
            if (current == '.' && isAbbreviation(text, i)) continue;
            int next = i + 1;
            while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
            if (next == text.length() || next > i + 1
                    && Character.isUpperCase(text.codePointAt(next))) return i + 1;
        }
        int clauseEnd = Math.max(text.lastIndexOf(',', start),
                Math.max(text.lastIndexOf(';', start), text.lastIndexOf(':', start)));
        if (clauseEnd >= 0) return clauseEnd + 1;
        for (int i = start; i >= 0; i--) {
            if (Character.isWhitespace(text.charAt(i))) return i + 1;
        }
        return limit;
    }

    private static boolean isDecimalPoint(String text, int index) {
        return index > 0 && index + 1 < text.length()
                && Character.isDigit(text.charAt(index - 1))
                && Character.isDigit(text.charAt(index + 1));
    }

    private static boolean isAbbreviation(String text, int index) {
        int start = index;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
        Matcher matcher = ABBREVIATION.matcher(text.substring(start, index + 1));
        return matcher.find();
    }

    public static byte[] stripId3v2(byte[] audio) {
        if (audio == null || audio.length < 10 || audio[0] != 'I'
                || audio[1] != 'D' || audio[2] != '3') return audio;
        int size = (audio[6] & 0x7f) << 21 | (audio[7] & 0x7f) << 14
                | (audio[8] & 0x7f) << 7 | audio[9] & 0x7f;
        int headerSize = 10 + size + ((audio[5] & 0x10) != 0 ? 10 : 0);
        if (headerSize >= audio.length) return audio;
        return java.util.Arrays.copyOfRange(audio, headerSize, audio.length);
    }

    private static final class NonRetryableException extends IOException {
        private final IOException cause;

        NonRetryableException(IOException cause) {
            super(cause.getMessage(), cause);
            this.cause = cause;
        }

        @Override public synchronized IOException getCause() { return cause; }
    }
}
