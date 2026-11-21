package com.builtbydeep.sovra;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class EdgeTtsClient {
    public static final String MODEL_TYPE = "edge-tts";
    public static final String MODEL_ID = "microsoft-edge-tts";

    private static final String TRUSTED_CLIENT_TOKEN =
            "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    public static final String SEC_MS_GEC_VERSION = "2-143.0.3650.75";
    private static final String CHROMIUM_MAJOR_VERSION = "143";
    private static volatile String activeSecMsGecVersion = SEC_MS_GEC_VERSION;
    private static volatile String activeChromiumMajorVersion = CHROMIUM_MAJOR_VERSION;

    private static final int STREAM_CHUNK_ESCAPED_UTF8_BYTES = 700;
    private static final float DEFAULT_INTER_CHUNK_SILENCE_SECONDS = 0.12f;

    private static final String VOICES_URL =
            "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
                    + "?trustedclienttoken=" + TRUSTED_CLIENT_TOKEN;
    private static final String WEBSOCKET_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String ORIGIN =
            "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/" + CHROMIUM_MAJOR_VERSION
                    + ".0.0.0 Safari/537.36 Edg/" + CHROMIUM_MAJOR_VERSION + ".0.0.0";
    private static final long WINDOWS_EPOCH_OFFSET_SECONDS = 11644473600L;
    private static final long UINT32_MASK = 0xffffffffL;
    private static final long[] SHA256_INITIAL = {
            0x6A09E667L, 0xBB67AE85L, 0x3C6EF372L, 0xA54FF53AL,
            0x510E527FL, 0x9B05688CL, 0x1F83D9ABL, 0x5BE0CD19L
    };
    private static final long[] SHA256_STATE = {
            0xFD9FBEA2L, 0x91243CE7L, 0xBE499094L, 0xCD2E1E0AL,
            0x6C006F49L, 0x04BD35ABL, 0xFEB0A7E4L, 0x03EFFBD2L
    };
    private static final long[] SHA256_SCHEDULE = {
            0xEB24BF9EL, 0x1558691FL, 0xA0C09EE2L, 0x0CFA32BAL,
            0xA622EE8CL, 0xD1DD9491L, 0xC0DD25A0L, 0xF9E70AB6L,
            0xF0C31573L, 0x7058CE4EL, 0x7188E606L, 0x00E5AB33L,
            0xD258B060L, 0x72766564L
    };
    private static final long[] SHA256_ROUND_CONSTANTS = {
            0x428A2F98L, 0x71374491L, 0xB5C0FBCFL, 0xE9B5DBA5L,
            0x3956C25BL, 0x59F111F1L, 0x923F82A4L, 0xAB1C5ED5L,
            0xD807AA98L, 0x12835B01L, 0x243185BEL, 0x550C7DC3L,
            0x72BE5D74L, 0x80DEB1FEL, 0x9BDC06A7L, 0xC19BF174L,
            0xE49B69C1L, 0xEFBE4786L, 0x0FC19DC6L, 0x240CA1CCL,
            0x2DE92C6FL, 0x4A7484AAL, 0x5CB0A9DCL, 0x76F988DAL,
            0x983E5152L, 0xA831C66DL, 0xB00327C8L, 0xBF597FC7L,
            0xC6E00BF3L, 0xD5A79147L, 0x06CA6351L, 0x14292967L,
            0x27B70A85L, 0x2E1B2138L, 0x4D2C6DFCL, 0x53380D13L,
            0x650A7354L, 0x766A0ABBL, 0x81C2C92EL, 0x92722C85L,
            0xA2BFE8A1L, 0xA81A664BL, 0xC24B8B70L, 0xC76C51A3L,
            0xD192E819L, 0xD6990624L, 0xF40E3585L, 0x106AA070L,
            0x19A4C116L, 0x1E376C08L, 0x2748774CL, 0x34B0BCB5L,
            0x391C0CB3L, 0x4ED8AA4AL, 0x5B9CCA4FL, 0x682E6FF3L,
            0x748F82EEL, 0x78A5636FL, 0x84C87814L, 0x8CC70208L,
            0x90BEFFFAL, 0xA4506CEBL, 0xBEF9A3F7L, 0xC67178F2L
    };
    private static final long SHA256_ADJUSTMENT = 0xC9DC06A7L;
    private static final long SHA256_SIGMA0_ADJUSTMENT = 0x059C0B80L;
    private static final long SHA256_SIGMA1_ADJUSTMENT = 0x000B92C0L;
    private static final long[][] CS64_COEFFICIENTS = {
            {0xE82E91B1L, 0xB32700D0L}, {0x44DE24C6L, 0x88DFF51DL},
            {0x3BEB65BCL, 0x29514038L}, {0xC89C5933L, 0xB328CBDFL},
            {0x8D1EC211L, 0xD405B4D5L}, {0x314EF5D5L, 0x26E55F3CL},
            {0xE92491B1L, 0x607801C6L}, {0xF0A5B9D5L, 0x82B2C1FBL},
            {0x06064CF3L, 0xFA51A3DAL}, {0xDA79FA60L, 0x207CC464L},
            {0xBED20560L, 0x3C3C74F3L}, {0x89F9C9F8L, 0xD2D14B61L},
            {0x35FA38C6L, 0xBB04A2C1L}, {0xCB49E3C6L, 0xC5626167L}
    };
    private static final long CS64_FACTOR = 0x82893933L;
    private static final long CS64_ADJUSTMENT0 = 0xF2F90B1AL;
    private static final long CS64_ADJUSTMENT1 = 0x0B1A0000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile double clockSkewSeconds;
    private static final OkHttpClient VOICE_HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private static final OkHttpClient WEBSOCKET_HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private volatile WebSocket activeSocket;
    private volatile boolean cancelled;
    private volatile long operationId;

    public EdgeTtsClient() {}

    public static void updateProtocolVersion(String secMsGecVersion, String chromiumMajorVersion) {
        if (secMsGecVersion != null && !secMsGecVersion.trim().isEmpty()) {
            activeSecMsGecVersion = secMsGecVersion.trim();
        }
        if (chromiumMajorVersion != null && !chromiumMajorVersion.trim().isEmpty()) {
            activeChromiumMajorVersion = chromiumMajorVersion.trim();
        }
    }

    private static String currentUserAgent() {
        String major = activeChromiumMajorVersion;
        if (CHROMIUM_MAJOR_VERSION.equals(major)) return USER_AGENT;
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + major
                + ".0.0.0 Safari/537.36 Edg/" + major + ".0.0.0";
    }

    public static List<Voice> fallbackVoices() {
        List<Voice> voices = new ArrayList<>();
        voices.add(new Voice("hi-IN-SwaraNeural", "hi-IN", "Female"));
        voices.add(new Voice("hi-IN-MadhurNeural", "hi-IN", "Male"));
        voices.add(new Voice("en-IN-NeerjaNeural", "en-IN", "Female"));
        voices.add(new Voice("en-IN-PrabhatNeural", "en-IN", "Male"));
        voices.add(new Voice("en-US-AvaNeural", "en-US", "Female"));
        voices.add(new Voice("en-US-AndrewNeural", "en-US", "Male"));
        voices.add(new Voice("en-GB-SoniaNeural", "en-GB", "Female"));
        voices.add(new Voice("ja-JP-NanamiNeural", "ja-JP", "Female"));
        return voices;
    }

    public static List<Voice> fetchVoices() {
        IOException lastError = null;
        String major = activeChromiumMajorVersion;
        for (int attempt = 0; attempt < 2; attempt++) {
            Request request = new Request.Builder()
                    .url(VOICES_URL)
                    .header("Authority", "speech.platform.bing.com")
                    .header("Sec-CH-UA", "\" Not;A Brand\";v=\"99\", \"Microsoft Edge\";v=\""
                            + major + "\", \"Chromium\";v=\"" + major + "\"")
                    .header("Sec-CH-UA-Mobile", "?0")
                    .header("Accept", "*/*")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("User-Agent", currentUserAgent())
                    .header("Origin", ORIGIN)
                    .header("Cookie", createMuidCookie())
                    .build();
            try (Response response = VOICE_HTTP.newCall(request).execute()) {
                if (response.code() == 403) {
                    throw new HttpStatusException(403, response.header("Date"),
                            "Voice list request was rejected (HTTP 403).");
                }
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Voice list request failed (HTTP " + response.code() + ").");
                }
                org.json.JSONArray values = new org.json.JSONArray(response.body().string());
                List<Voice> voices = new ArrayList<>();
                for (int i = 0; i < values.length(); i++) {
                    org.json.JSONObject value = values.optJSONObject(i);
                    if (value == null) continue;
                    String name = value.optString("Name", "");
                    String locale = value.optString("Locale", "");
                    String gender = value.optString("Gender", "Unknown");
                    if (!name.isEmpty() && !locale.isEmpty()) voices.add(new Voice(name, locale, gender));
                }
                if (!voices.isEmpty()) {
                    return deduplicateVoices(voices);
                }
                lastError = new IOException("Voice list response contained no voices.");
                break;
            } catch (IOException error) {
                lastError = error;
                if (error instanceof HttpStatusException && attempt == 0) {
                    try {
                        adjustClockSkewSeconds(((HttpStatusException) error).serverDate);
                        continue;
                    } catch (IOException skewError) {
                        lastError = skewError;
                    }
                }
                break;
            } catch (org.json.JSONException error) {
                lastError = new IOException("Voice list response could not be parsed.", error);
                break;
            }
        }
        android.util.Log.w("EdgeTtsClient",
                "Voice list unavailable; using the built-in Edge TTS voices.", lastError);
        return deduplicateVoices(fallbackVoices());
    }

    static List<Voice> deduplicateVoices(List<Voice> voices) {
        LinkedHashMap<String, Voice> uniqueVoices = new LinkedHashMap<>();
        for (Voice voice : voices) {
            String key = voice.name.toLowerCase(Locale.ROOT);
            if (!uniqueVoices.containsKey(key)) uniqueVoices.put(key, voice);
        }
        return new ArrayList<>(uniqueVoices.values());
    }

    public static String formatDisplayName(String shortName, String gender) {
        if (shortName == null) shortName = "";
        String cleanName = shortName.trim();
        int lastComma = cleanName.lastIndexOf(',');
        if (cleanName.endsWith(")") && lastComma != -1 && lastComma + 1 < cleanName.length() - 1) {
            cleanName = cleanName.substring(lastComma + 1, cleanName.length() - 1).trim();
        } else if (cleanName.contains("-")) {
            cleanName = cleanName.substring(cleanName.lastIndexOf('-') + 1).trim();
        }
        if (cleanName.endsWith("Neural")) {
            cleanName = cleanName.substring(0, cleanName.length() - "Neural".length());
        }
        if (cleanName.endsWith("Multilingual")) {
            cleanName = cleanName.substring(0, cleanName.length() - "Multilingual".length()).trim()
                    + " (Multilingual)";
        } else if (cleanName.endsWith("Expressive")) {
            cleanName = cleanName.substring(0, cleanName.length() - "Expressive".length()).trim()
                    + " (Expressive)";
        }
        String cleanGender = gender == null || gender.trim().isEmpty() ? "Unknown" : gender.trim();
        return cleanName + " • " + cleanGender;
    }

    public static String formatLocaleDisplayName(String localeCode) {
        if (localeCode == null) return "Unknown";
        String code = localeCode.trim();
        if ("hi-IN".equalsIgnoreCase(code)) return "India (Hindi)";
        if ("en-IN".equalsIgnoreCase(code)) return "India (English)";
        if ("en-US".equalsIgnoreCase(code)) return "US (English)";
        if ("en-GB".equalsIgnoreCase(code)) return "UK (English)";
        if ("ja-JP".equalsIgnoreCase(code)) return "Japan (Japanese)";

        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        String language = locale.getDisplayLanguage(Locale.ENGLISH);
        String country = locale.getDisplayCountry(Locale.ENGLISH);
        if ("United States".equals(country)) country = "US";
        if ("United Kingdom".equals(country)) country = "UK";
        if (country.isEmpty()) return language.isEmpty() ? code : language;
        return country + " (" + (language.isEmpty() ? code : language) + ")";
    }

    public SynthesizedAudio synthesize(Context context, String text, Voice voice, float speed,
                                       float pitch) throws IOException {
        return synthesize(context, text, voice, speed, pitch, DEFAULT_INTER_CHUNK_SILENCE_SECONDS);
    }

    public SynthesizedAudio synthesize(Context context, String text, Voice voice, float speed,
                                       float pitch, float silenceSeconds) throws IOException {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        SynthesisResult result = synthesizeStreaming(context, text, voice, speed, pitch,
                silenceSeconds, (chunk, sampleRate) -> pcm.write(chunk));
        return new SynthesizedAudio(pcm.toByteArray(), result.sampleRate);
    }

    public SynthesisResult synthesizeStreaming(Context context, String text, Voice voice,
                                                float speed, float pitch,
                                                PcmChunkListener listener) throws IOException {
        return synthesizeStreaming(context, text, voice, speed, pitch,
                DEFAULT_INTER_CHUNK_SILENCE_SECONDS, listener, () -> true);
    }

    public SynthesisResult synthesizeStreaming(Context context, String text, Voice voice,
                                                float speed, float pitch,
                                                PcmChunkListener listener,
                                                BooleanSupplier shouldContinue) throws IOException {
        return synthesizeStreaming(context, text, voice, speed, pitch,
                DEFAULT_INTER_CHUNK_SILENCE_SECONDS, listener, shouldContinue);
    }

    public SynthesisResult synthesizeStreaming(Context context, String text, Voice voice,
                                                float speed, float pitch, float silenceSeconds,
                                                PcmChunkListener listener) throws IOException {
        return synthesizeStreaming(context, text, voice, speed, pitch, silenceSeconds,
                listener, () -> true);
    }

    public SynthesisResult synthesizeStreaming(Context context, String text, Voice voice,
                                                float speed, float pitch, float silenceSeconds,
                                                PcmChunkListener listener,
                                                BooleanSupplier shouldContinue) throws IOException {
        if (text == null || text.trim().isEmpty()) throw new IOException("Enter text to synthesize.");
        if (voice == null) throw new IOException("Select an Edge TTS voice.");
        if (listener == null) throw new IOException("An Edge TTS audio listener is required.");
        if (shouldContinue == null) throw new IOException("An Edge TTS cancellation guard is required.");
        if (!shouldContinue.getAsBoolean()) {
            throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
        }

        final long operation = startOperation();
        if (!shouldContinue.getAsBoolean()) {
            cancelOperation(operation);
            throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
        }
        List<String> chunks = splitIntoUtf8Chunks(text, STREAM_CHUNK_ESCAPED_UTF8_BYTES);
        if (chunks.isEmpty()) {
            throw new IOException("Enter text with speakable words to synthesize.");
        }

        int outputSampleRate = 24000;
        long totalPcmBytes = 0;
        float clampedSilence = Math.max(0.0f, Math.min(3.0f, silenceSeconds));

        for (int i = 0; i < chunks.size(); i++) {
            if (isStopped(operation, shouldContinue)) {
                cancelOperation(operation);
                throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
            }
            String chunk = chunks.get(i);
            float[] adjustedProsody = applyPunctuationProsody(chunk, speed, pitch);
            SynthesizedAudio chunkAudio = synthesizeSingleChunkWithRetry(
                    context, chunk, voice, adjustedProsody[0], adjustedProsody[1], operation,
                    shouldContinue);

            if (i == 0) {
                outputSampleRate = chunkAudio.sampleRate;
            } else if (chunkAudio.sampleRate != outputSampleRate) {
                throw new IOException("Microsoft Edge TTS changed its audio sample rate mid-stream.");
            }
            if (isStopped(operation, shouldContinue)) {
                cancelOperation(operation);
                throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
            }
            listener.onAudioChunk(chunkAudio.pcm, outputSampleRate);
            totalPcmBytes += chunkAudio.pcm.length;

            if (i < chunks.size() - 1) {
                if (clampedSilence > 0.001f) {
                    int silenceSamples = Math.round(outputSampleRate * clampedSilence);
                    if (silenceSamples > 0) {
                        byte[] silence = new byte[silenceSamples * 2];
                        listener.onAudioChunk(silence, outputSampleRate);
                        totalPcmBytes += silence.length;
                    }
                }
                sleepQuietly(100L);
            }
        }

        if (totalPcmBytes == 0) {
            throw new IOException("Microsoft Edge TTS returned no audio.");
        }
        return new SynthesisResult(outputSampleRate, totalPcmBytes);
    }

    private SynthesizedAudio synthesizeSingleChunkWithRetry(Context context, String chunk, Voice voice,
                                                            float speed, float pitch, long operation,
                                                            BooleanSupplier shouldContinue)
            throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (isStopped(operation, shouldContinue)) {
                cancelOperation(operation);
                throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
            }
            try {
                byte[] mp3Bytes = collectMp3(chunk, voice, speed, pitch, operation,
                        shouldContinue);
                return decodeMp3(context, mp3Bytes, operation);
            } catch (HttpStatusException error) {
                lastError = error;
                if (error.statusCode == 403 && attempt == 0) {
                    adjustClockSkewSeconds(error.serverDate);
                    continue;
                }
                if (error.statusCode == 429 && attempt < 2) {
                    sleepQuietly(400L * (attempt + 1));
                    continue;
                }
                throw error;
            } catch (IOException error) {
                lastError = error;
                if (isStopped(operation, shouldContinue) || attempt >= 2) throw error;
                sleepQuietly(250L * (attempt + 1));
            }
        }
        throw lastError != null ? lastError
                : new IOException("Microsoft Edge TTS request could not be completed.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    static List<String> splitIntoUtf8Chunks(String rawText, int maxEscapedUtf8Bytes) {
        String sanitized = sanitizeControlChars(rawText);
        List<String> result = new ArrayList<>();
        if (sanitized.isEmpty()) return result;

        int start = 0;
        int len = sanitized.length();
        while (start < len) {
            while (start < len && Character.isWhitespace(sanitized.charAt(start))) {
                start++;
            }
            if (start >= len) break;

            int end = findMaxFitIndex(sanitized, start, maxEscapedUtf8Bytes);
            if (end < len) {
                int bestBreak = findNaturalBreak(sanitized, start, end);
                if (bestBreak > start) {
                    end = bestBreak;
                }
            }

            String piece = sanitized.substring(start, end).trim();
            if (!piece.isEmpty() && hasSpeakableContent(piece)) {
                result.add(piece);
            }
            start = end;
        }
        return result;
    }

    private static String sanitizeControlChars(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static int findMaxFitIndex(String text, int start, int maxEscapedBytes) {
        int bytes = 0;
        int i = start;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            int cpBytes = escapedCodePointUtf8Bytes(cp);
            if (bytes + cpBytes > maxEscapedBytes) {
                break;
            }
            bytes += cpBytes;
            i += Character.charCount(cp);
        }
        if (i == start && start < len) {
            i += Character.charCount(text.codePointAt(start));
        }
        return i;
    }

    private static int escapedCodePointUtf8Bytes(int cp) {
        if (cp == '&') return 5;
        if (cp == '<' || cp == '>') return 4;
        if (cp == '"' || cp == '\'') return 6;
        if (cp <= 0x7F) return 1;
        if (cp <= 0x7FF) return 2;
        if (cp <= 0xFFFF) return 3;
        return 4;
    }

    private static int findNaturalBreak(String text, int start, int maxEnd) {
        int minAcceptable = start + ((maxEnd - start) / 3);

        for (int i = maxEnd - 1; i >= minAcceptable; i--) {
            if (text.charAt(i) == '\n') return i + 1;
        }
        for (int i = maxEnd - 1; i >= minAcceptable; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '।'
                    || c == '。' || c == '！' || c == '？') {
                int next = i + 1;
                while (next < maxEnd && (text.charAt(next) == '"' || text.charAt(next) == '\''
                        || text.charAt(next) == ')' || text.charAt(next) == '”')) {
                    next++;
                }
                if (next == maxEnd || Character.isWhitespace(text.charAt(next))) {
                    return next;
                }
            }
        }
        for (int i = maxEnd - 1; i >= minAcceptable; i--) {
            char c = text.charAt(i);
            if (c == ',' || c == ';' || c == ':' || c == '،' || c == '、') {
                return i + 1;
            }
        }
        for (int i = maxEnd - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return maxEnd;
    }

    private static boolean hasSpeakableContent(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static float[] applyPunctuationProsody(String chunk, float baseSpeed, float basePitch) {
        float speed = baseSpeed;
        float pitch = basePitch;
        String trimmed = chunk.trim();
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            speed = Math.max(0.5f, baseSpeed - 0.03f);
            pitch = Math.max(0.5f, basePitch - 0.03f);
        } else if (trimmed.endsWith("?") || trimmed.endsWith("？")) {
            pitch = Math.min(2.0f, basePitch + 0.04f);
        } else if (trimmed.endsWith("!") || trimmed.endsWith("！")) {
            speed = Math.min(2.0f, baseSpeed + 0.03f);
            pitch = Math.min(2.0f, basePitch + 0.03f);
        }
        return new float[]{speed, pitch};
    }

    private byte[] collectMp3(String text, Voice voice, float speed, float pitch, long operation,
                              BooleanSupplier shouldContinue) throws IOException {
        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<IOException> failure = new AtomicReference<>();
        SimpleDateFormat timestampFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = timestampFormat.format(new Date());
        String connectionId = UUID.randomUUID().toString().replace("-", "")
                .toLowerCase(Locale.ROOT);
        String url = WEBSOCKET_URL + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
                + "&ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + createSecMsGecToken(getUnixTimestamp())
                + "&Sec-MS-GEC-Version=" + activeSecMsGecVersion;
        Request request = new Request.Builder()
                .url(url)
                .header("Origin", ORIGIN)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("User-Agent", currentUserAgent())
                .header("Accept-Encoding", "gzip, deflate, br, zstd")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", createMuidCookie())
                .build();

        WebSocket socket = WEBSOCKET_HTTP.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                if (isStopped(operation, shouldContinue)) {
                    webSocket.cancel();
                    completed.countDown();
                    return;
                }
                String config = "X-Timestamp:" + timestamp
                        + "\r\nContent-Type:application/json; charset=utf-8"
                        + "\r\nPath:speech.config\r\n\r\n"
                        + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
                        + "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                        + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                String requestId = UUID.randomUUID().toString().replace("-", "")
                        .toLowerCase(Locale.ROOT);
                String ssml = "X-RequestId:" + requestId
                        + "\r\nContent-Type:application/ssml+xml"
                        + "\r\nX-Timestamp:" + timestamp
                        + "\r\nPath:ssml\r\n\r\n"
                        + "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\""
                        + " xml:lang=\"" + escapeXmlAttribute(voice.locale) + "\"><voice name=\""
                        + escapeXmlAttribute(voice.name) + "\"><prosody rate=\""
                        + formatRate(speed) + "\" pitch=\"" + formatPitch(pitch) + "\">"
                        + escapeXmlText(text) + "</prosody></voice></speak>";
                if (!webSocket.send(config) || !webSocket.send(ssml)) {
                    failure.set(new IOException("The Edge TTS connection closed before synthesis started."));
                    completed.countDown();
                    webSocket.cancel();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String message) {
                if (isStopped(operation, shouldContinue)) {
                    completed.countDown();
                    webSocket.cancel();
                    return;
                }
                if (message.contains("Path:turn.end")) {
                    completed.countDown();
                    webSocket.close(1000, "Synthesis complete");
                } else if (message.contains("Path:response") && message.contains("403")) {
                    failure.set(new IOException("Microsoft Edge TTS rejected the connection."));
                    completed.countDown();
                    webSocket.cancel();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                if (isStopped(operation, shouldContinue)) {
                    completed.countDown();
                    webSocket.cancel();
                    return;
                }
                byte[] frame = bytes.toByteArray();
                if (frame.length < 2) return;
                int headerLength = ((frame[0] & 0xff) << 8) | (frame[1] & 0xff);
                int payloadOffset = 2 + headerLength;
                if (payloadOffset > frame.length) {
                    failure.set(new IOException("Microsoft Edge TTS returned an invalid audio frame."));
                    completed.countDown();
                    webSocket.cancel();
                    return;
                }
                synchronized (mp3) {
                    mp3.write(frame, payloadOffset, frame.length - payloadOffset);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                String reason = response != null
                        ? "Microsoft Edge TTS connection failed (HTTP " + response.code() + ")."
                        : "Microsoft Edge TTS connection failed: " + error.getMessage();
                IOException responseError = response != null
                        ? new HttpStatusException(response.code(), response.header("Date"), reason, error)
                        : new IOException(reason, error);
                failure.compareAndSet(null, responseError);
                completed.countDown();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (completed.getCount() != 0) {
                    failure.compareAndSet(null, new IOException(
                            "Microsoft Edge TTS connection closed before audio completed."));
                    completed.countDown();
                }
            }
        });
        if (isCancelled(operation)) {
            socket.cancel();
        } else {
            activeSocket = socket;
        }

        try {
            if (!completed.await(60, TimeUnit.SECONDS)) {
                cancelOperation(operation);
                throw new IOException("Microsoft Edge TTS timed out. Check your connection and try again.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelOperation(operation);
            throw new IOException("Microsoft Edge TTS was interrupted.", error);
        } finally {
            if (activeSocket == socket) activeSocket = null;
        }

        IOException error = failure.get();
        if (error != null) throw error;
        byte[] mp3Bytes;
        synchronized (mp3) {
            mp3Bytes = mp3.toByteArray();
        }
        if (mp3Bytes.length == 0) throw new IOException("Microsoft Edge TTS returned no audio.");
        return mp3Bytes;
    }

    public synchronized void cancel() {
        cancelled = true;
        operationId++;
        WebSocket socket = activeSocket;
        if (socket != null) socket.cancel();
    }

    private synchronized long startOperation() {
        WebSocket previous = activeSocket;
        if (previous != null) previous.cancel();
        cancelled = false;
        return ++operationId;
    }

    private boolean isCancelled(long operation) {
        return cancelled || operationId != operation;
    }

    private boolean isStopped(long operation, BooleanSupplier shouldContinue) {
        return isCancelled(operation) || !shouldContinue.getAsBoolean();
    }

    private synchronized void cancelOperation(long operation) {
        if (operationId == operation) cancel();
    }

    static String createSecMsGecToken(double unixTimestampSeconds) {
        double ticks = unixTimestampSeconds + WINDOWS_EPOCH_OFFSET_SECONDS;
        ticks -= ticks % 300;
        ticks *= 1e9 / 100;
        String timestamp = String.format(Locale.US, "%.0f", ticks);
        byte[] tokenData = (timestamp + TRUSTED_CLIENT_TOKEN)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] digest = sha256Digest(tokenData);
        long[] cs64 = cs64Digest(tokenData, digest);
        return hexLittleEndian32(cs64[0]) + hexLittleEndian32(cs64[1])
                + "-" + hexUpper(digest);
    }

    static String generateMuid() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return hexUpper(bytes);
    }

    static String createMuidCookie() {
        return "muid=" + generateMuid() + ";";
    }

    private static double getUnixTimestamp() {
        return System.currentTimeMillis() / 1000.0 + clockSkewSeconds;
    }

    private static synchronized void adjustClockSkewSeconds(String serverDate)
            throws IOException {
        if (serverDate == null) throw new IOException("HTTP 403 response did not include a Date header.");
        final double serverTimestamp;
        try {
            serverTimestamp = ZonedDateTime.parse(serverDate, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() / 1000.0;
        } catch (DateTimeParseException error) {
            throw new IOException("HTTP 403 response contained an invalid Date header.", error);
        }
        clockSkewSeconds += serverTimestamp - getUnixTimestamp();
    }

    private static byte[] sha256Digest(byte[] data) {
        if (data.length < 7) throw new IllegalArgumentException("Token salt must contain seven bytes.");
        long length = 57L + data.length;
        ByteArrayOutputStream padded = new ByteArrayOutputStream();
        padded.write(data, 7, data.length - 7);
        padded.write(0x80);
        int zeroCount = (int) Math.floorMod(55L - length, 64L);
        for (int i = 0; i < zeroCount; i++) padded.write(0);
        long bitLength = length * 8;
        for (int shift = 56; shift >= 0; shift -= 8) padded.write((int) (bitLength >>> shift) & 0xff);

        long[] state = sha256Init(data);
        byte[] bytes = padded.toByteArray();
        for (int offset = 0; offset < bytes.length; offset += 64) {
            long[] words = new long[64];
            for (int i = 0; i < 16; i++) {
                int index = offset + i * 4;
                words[i] = ((bytes[index] & 0xffL) << 24)
                        | ((bytes[index + 1] & 0xffL) << 16)
                        | ((bytes[index + 2] & 0xffL) << 8)
                        | (bytes[index + 3] & 0xffL);
            }
            expandSha256Schedule(words, 16);
            long[] working = sha256Rounds(state, words, 0, false);
            for (int i = 0; i < state.length; i++)
                state[i] = (state[i] + working[i]) & UINT32_MASK;
        }

        byte[] digest = new byte[32];
        for (int i = 0; i < state.length; i++) {
            for (int byteIndex = 0; byteIndex < 4; byteIndex++)
                digest[i * 4 + byteIndex] = (byte) (state[i] >>> (24 - byteIndex * 8));
        }
        return digest;
    }

    private static long[] sha256Init(byte[] data) {
        long[] words = new long[64];
        words[14] = ((data[0] & 0xffL) << 16) | ((data[1] & 0xffL) << 8) | (data[2] & 0xffL);
        words[15] = ((data[3] & 0xffL) << 24) | ((data[4] & 0xffL) << 16)
                | ((data[5] & 0xffL) << 8) | (data[6] & 0xffL);
        for (int index = 16; index < 30; index++) {
            long value = SHA256_SCHEDULE[index - 16];
            if (index - 2 >= 14) {
                long sigma = smallSigma1(words[index - 2]);
                value += index == 16 ? sigma ^ SHA256_SIGMA1_ADJUSTMENT : sigma;
            }
            if (index - 7 >= 14) value += words[index - 7];
            if (index - 15 >= 14) {
                long sigma = smallSigma0(words[index - 15]);
                value += index == 29 ? sigma ^ SHA256_SIGMA0_ADJUSTMENT : sigma;
            }
            words[index] = value & UINT32_MASK;
        }
        words[30] = (smallSigma1(words[28]) + words[23] + smallSigma0(words[15])
                + words[14] + SHA256_ADJUSTMENT - SHA256_ROUND_CONSTANTS[14])
                & UINT32_MASK;
        expandSha256Schedule(words, 31);
        long[] working = sha256Rounds(SHA256_STATE, words, 14, true);
        long[] state = new long[8];
        for (int i = 0; i < state.length; i++)
            state[i] = (SHA256_INITIAL[i] + working[i]) & UINT32_MASK;
        return state;
    }

    private static void expandSha256Schedule(long[] words, int firstWord) {
        for (int index = firstWord; index < 64; index++) {
            words[index] = (smallSigma1(words[index - 2]) + words[index - 7]
                    + smallSigma0(words[index - 15]) + words[index - 16]) & UINT32_MASK;
        }
    }

    private static long[] sha256Rounds(long[] initialState, long[] words, int firstRound,
                                       boolean customFirstBlock) {
        long a = initialState[0], b = initialState[1], c = initialState[2], d = initialState[3];
        long e = initialState[4], f = initialState[5], g = initialState[6], h = initialState[7];
        for (int index = firstRound; index < 64; index++) {
            long constant = customFirstBlock && index == 14
                    ? SHA256_ADJUSTMENT : SHA256_ROUND_CONSTANTS[index];
            long t1 = (h + bigSigma1(e) + (e & f ^ ~e & g)
                    + constant + words[index]) & UINT32_MASK;
            long t2 = (bigSigma0(a) + ((a & b) ^ (a & c) ^ (b & c))) & UINT32_MASK;
            h = g;
            g = f;
            f = e;
            e = (d + t1) & UINT32_MASK;
            d = c;
            c = b;
            b = a;
            a = (t1 + t2) & UINT32_MASK;
        }
        return new long[]{a, b, c, d, e, f, g, h};
    }

    private static long smallSigma0(long value) {
        return ((value >>> 7) ^ (value << 25) ^ (value >>> 18)
                ^ (value << 14) ^ (value >>> 3)) & UINT32_MASK;
    }

    private static long smallSigma1(long value) {
        return ((value >>> 17) ^ (value << 15) ^ (value >>> 19)
                ^ (value << 13) ^ (value >>> 10)) & UINT32_MASK;
    }

    private static long bigSigma0(long value) {
        return (rotateRight(value, 2) ^ rotateRight(value, 13) ^ rotateRight(value, 22))
                & UINT32_MASK;
    }

    private static long bigSigma1(long value) {
        return (rotateRight(value, 6) ^ rotateRight(value, 11) ^ rotateRight(value, 25))
                & UINT32_MASK;
    }

    private static long rotateRight(long value, int bits) {
        return ((value >>> bits) | (value << (32 - bits))) & UINT32_MASK;
    }

    private static long rotate16(long value) {
        value &= UINT32_MASK;
        return ((value << 16) | (value >>> 16)) & UINT32_MASK;
    }

    private static long[] cs64Digest(byte[] data, byte[] digest) {
        long keyA = readLittleEndian32(digest, 0) | 1L;
        long keyB = readLittleEndian32(digest, 4) | 1L;
        long factorA = keyA * CS64_FACTOR & UINT32_MASK;
        long factorB = keyB * CS64_FACTOR & UINT32_MASK;
        List<Long> terms = new ArrayList<>();
        List<Long> offsets = new ArrayList<>();
        for (int index = 0; index < CS64_COEFFICIENTS.length; index++) {
            long[] coefficient = CS64_COEFFICIENTS[index];
            terms.add(coefficient[0] * (index % 2 == 0 ? factorA : factorB) & UINT32_MASK);
            offsets.add(coefficient[1]);
        }

        byte[] prefixed = new byte[data.length + 1];
        System.arraycopy(data, 0, prefixed, 1, data.length);
        int paddedLength = (prefixed.length + 3) & ~3;
        byte[] wordBytes = new byte[paddedLength + ((paddedLength / 4) % 2 == 0 ? 0 : 4)];
        System.arraycopy(prefixed, 0, wordBytes, 0, prefixed.length);
        int wordCount = wordBytes.length / 4;
        for (int index = 0; index < wordCount; index++) {
            long word = readLittleEndian32(wordBytes, index * 4);
            long term = word * (index % 2 == 0 ? keyA : keyB);
            long offset = rotate16(word) * (index % 2 == 0 ? 0xEF0569FBL : 0xC31713DBL);
            if (index == 0) {
                term += CS64_ADJUSTMENT0 * factorA;
                offset += CS64_ADJUSTMENT1;
            }
            terms.add(term & UINT32_MASK);
            offsets.add(offset & UINT32_MASK);
        }

        long swapAcc = 0, swapLast = 0, state = 0, acc = 0;
        for (int index = 0; index < terms.size(); index += 2) {
            long x = terms.get(index), y = terms.get(index + 1);
            long t = rotate16(offsets.get(index) + x & UINT32_MASK);
            long q = t * 0x76F50F34L & UINT32_MASK;
            swapAcc = (swapAcc + (rotate16(q) * 0xC3EFEA97L + q & UINT32_MASK))
                    & UINT32_MASK;
            t = rotate16(offsets.get(index + 1) + y & UINT32_MASK);
            q = t * 0x1881CE3CL & UINT32_MASK;
            swapLast = (rotate16(q) * 0x35BD1EC9L + q) & UINT32_MASK;
            swapAcc = (swapAcc + swapLast) & UINT32_MASK;

            state = (state * keyA + x) & UINT32_MASK;
            state = rotate16(state) * 0xCF98B111L & UINT32_MASK;
            state = rotate16(state) * 0x87085B9FL & UINT32_MASK;
            state = rotate16(state) * 0x12CEB96DL & UINT32_MASK;
            state = rotate16(state) * 0x257E1D83L & UINT32_MASK;
            acc = (acc + state) & UINT32_MASK;
            t = (state * keyB + y) & UINT32_MASK;
            t = rotate16(t) * 0xA27416F5L & UINT32_MASK;
            t = rotate16(t) * 0xD38396FFL & UINT32_MASK;
            state = rotate16(rotate16(t) * 0x7C932B89L & UINT32_MASK)
                    * 0xBFA49F69L & UINT32_MASK;
            acc = (acc + state) & UINT32_MASK;
        }
        return new long[]{swapLast ^ state, swapAcc ^ acc};
    }

    private static long readLittleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static String hexLittleEndian32(long value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (value >>> (i * 8));
        return hexUpper(bytes);
    }

    private static String hexUpper(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02X", value & 0xff));
        return result.toString();
    }

    private static final class HttpStatusException extends IOException {
        final int statusCode;
        final String serverDate;

        HttpStatusException(int statusCode, String serverDate, String message) {
            super(message);
            this.statusCode = statusCode;
            this.serverDate = serverDate;
        }

        HttpStatusException(int statusCode, String serverDate, String message, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.serverDate = serverDate;
        }
    }

    private static String formatRate(float speed) {
        int percent = Math.round((speed - 1.0f) * 100.0f);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    private static String formatPitch(float pitch) {
        int hertz = Math.round((pitch - 1.0f) * 50.0f);
        return (hertz >= 0 ? "+" : "") + hertz + "Hz";
    }

    private static String escapeXmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeXmlAttribute(String value) {
        return escapeXmlText(value);
    }

    private SynthesizedAudio decodeMp3(Context context, byte[] mp3Bytes, long operation)
            throws IOException {
        File inputFile = File.createTempFile("edge-tts-", ".mp3", context.getCacheDir());
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            try (FileOutputStream output = new FileOutputStream(inputFile)) {
                output.write(mp3Bytes);
            }
            extractor.setDataSource(inputFile.getAbsolutePath());
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    inputFormat = format;
                    break;
                }
            }
            if (audioTrack < 0 || inputFormat == null) throw new IOException("No MP3 audio track was found.");
            extractor.selectTrack(audioTrack);
            decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 24000;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT;

            while (!outputEnded) {
                if (isCancelled(operation)) {
                    throw new IOException("Microsoft Edge TTS synthesis was cancelled.");
                }
                if (!inputEnded) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("Unable to decode Edge TTS audio.");
                        inputBuffer.clear();
                        int size = extractor.readSampleData(inputBuffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                } else if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        appendMonoPcm(pcm, outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                channels, pcmEncoding);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                }
            }
            byte[] pcmData = pcm.toByteArray();
            if (pcmData.length == 0) throw new IOException("Microsoft Edge TTS audio could not be decoded.");
            return new SynthesizedAudio(pcmData, sampleRate);
        } catch (IllegalStateException error) {
            throw new IOException("Unable to decode Microsoft Edge TTS audio.", error);
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {}
                decoder.release();
            }
            extractor.release();
            if (!inputFile.delete()) inputFile.deleteOnExit();
        }
    }

    private static void appendMonoPcm(ByteArrayOutputStream output, ByteBuffer samples,
                                      int channels, int encoding) {
        int channelCount = Math.max(1, channels);
        if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
            while (samples.remaining() >= 4 * channelCount) {
                float sum = 0;
                for (int channel = 0; channel < channelCount; channel++) sum += samples.getFloat();
                short mono = (short) Math.max(Short.MIN_VALUE,
                        Math.min(Short.MAX_VALUE, Math.round(sum / channelCount * Short.MAX_VALUE)));
                output.write(mono & 0xff);
                output.write((mono >>> 8) & 0xff);
            }
            return;
        }
        while (samples.remaining() >= 2 * channelCount) {
            int sum = 0;
            for (int channel = 0; channel < channelCount; channel++) sum += samples.getShort();
            short mono = (short) (sum / channelCount);
            output.write(mono & 0xff);
            output.write((mono >>> 8) & 0xff);
        }
    }

    public static final class Voice {
        public final String name;
        public final String locale;
        public final String gender;

        Voice(String name, String locale, String gender) {
            this.name = name;
            this.locale = locale;
            this.gender = gender;
        }

        public String displayName() {
            return formatDisplayName(name, gender);
        }
    }

    public static final class SynthesizedAudio {
        public final byte[] pcm;
        public final int sampleRate;

        SynthesizedAudio(byte[] pcm, int sampleRate) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
        }
    }

    public interface PcmChunkListener {
        void onAudioChunk(byte[] pcm, int sampleRate) throws IOException;
    }

    public static final class SynthesisResult {
        public final int sampleRate;
        public final long pcmBytes;

        SynthesisResult(int sampleRate, long pcmBytes) {
            this.sampleRate = sampleRate;
            this.pcmBytes = pcmBytes;
        }
    }
}
