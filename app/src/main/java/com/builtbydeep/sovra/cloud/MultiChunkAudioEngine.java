package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Locale;

public final class MultiChunkAudioEngine {
    public interface ProgressListener {
        void onProgress(int chunk, int total);
    }

    public static String sanitizeText(String input, boolean preserveEmotionTags) {
        if (input == null) return "";
        String text = input;
        if (!preserveEmotionTags) {
            text = text.replaceAll("\\*\\*|__|##|`", "");
        } else {
            text = text.replaceAll("\\*\\*|__", "");
        }
        text = text.replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replace('\u2013', '-').replace('\u2014', '-');
        text = text.replaceAll("[ \\t]+", " ").replaceAll("\\r\\n?", "\n");
        return text.trim();
    }

    public static List<String> splitUtf8Safe(String text, int maxChars, boolean byteLimit) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        String normalized = text.trim();
        int start = 0;
        while (start < normalized.length()) {
            int searchLimit = Math.min(normalized.length(), start + maxChars);
            int split = searchLimit;
            if (searchLimit < normalized.length()) {
                int sentence = -1;
                for (int i = searchLimit; i > start; i--) {
                    char c = normalized.charAt(i - 1);
                    if (c == '.' || c == '?' || c == '!' || c == '\u0964' || c == '\n') {
                        sentence = i;
                        break;
                    }
                }
                if (sentence > start) split = sentence;
                else {
                    int space = normalized.lastIndexOf(' ', searchLimit);
                    if (space > start) split = space + 1;
                }
            }
            if (byteLimit) {
                while (split > start &&
                        normalized.substring(start, split).getBytes(StandardCharsets.UTF_8).length > 240) {
                    split--;
                }
            }
            String chunk = normalized.substring(start, split).trim();
            if (!chunk.isEmpty()) result.add(chunk);
            start = split;
        }
        return result;
    }

    public static byte[] stitchMp3(List<byte[]> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunk = stripMp3Tags(chunks.get(i), i == 0);
                out.write(chunk);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stitch MP3", e);
        }
    }

    private static byte[] stripMp3Tags(byte[] bytes, boolean keepId3v2) {
        int start = 0;
        if (bytes.length >= 10 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            int size = ((bytes[6] & 0x7f) << 21) | ((bytes[7] & 0x7f) << 14)
                    | ((bytes[8] & 0x7f) << 7) | (bytes[9] & 0x7f);
            int id3End = 10 + size;
            if ((bytes.length > 10) && (bytes[5] & 0x10) != 0) id3End += 10;
            if (keepId3v2) start = 0;
            else if (id3End >= 0 && id3End < bytes.length) start = id3End;
            else start = 0;
        }
        int end = bytes.length;
        if (bytes.length >= 128 && end >= 128
                && bytes[end - 128] == 'T' && bytes[end - 127] == 'A'
                && bytes[end - 126] == 'G') end -= 128;
        if (keepId3v2 && start == 0) {
            return bytes;
        }
        if (end <= start) return new byte[0];
        byte[] clean = new byte[end - start];
        System.arraycopy(bytes, start, clean, 0, clean.length);
        return clean;
    }

    public static byte[] stitchWav(List<byte[]> chunks) throws Exception {
        if (chunks.isEmpty()) return new byte[0];
        WavHeader header = parseWav(chunks.get(0));
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            WavHeader current = parseWav(chunk);
            if (header.sampleRate != current.sampleRate || header.numChannels != current.numChannels
                    || header.bitsPerSample != current.bitsPerSample) {
                throw new IllegalArgumentException("WAV chunks have incompatible audio formats");
            }
            byte[] payload = current.data;
            int dataLength = payload.length;
            if (header.bitsPerSample == 16 && (dataLength & 1) != 0) dataLength--;
            pcm.write(payload, 0, dataLength);
        }
        byte[] samples = pcm.toByteArray();
        byte[] wav = new byte[header.dataOffset + samples.length];
        System.arraycopy(chunks.get(0), 0, wav, 0, header.dataOffset);
        putLittleEndianInt(wav, 4, wav.length - 8);
        putLittleEndianInt(wav, header.dataSizeOffset, samples.length);
        System.arraycopy(samples, 0, wav, header.dataOffset, samples.length);
        return wav;
    }

    private static void putLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value & 0xff);
        bytes[offset + 1] = (byte) ((value >>> 8) & 0xff);
        bytes[offset + 2] = (byte) ((value >>> 16) & 0xff);
        bytes[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16) | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static WavHeader parseWav(byte[] bytes) throws Exception {
        if (bytes.length < 12 || bytes[0] != 'R' || bytes[1] != 'I'
                || bytes[2] != 'F' || bytes[3] != 'F') throw new IllegalArgumentException("Invalid WAV response");
        int offset = 12;
        int sampleRate = 0, numChannels = 0, bits = 0, dataOffset = -1, dataSizeOffset = -1;
        while (offset + 8 <= bytes.length) {
            int size = littleEndianInt(bytes, offset + 4);
            if (bytes[offset] == 'f' && bytes[offset + 1] == 'm'
                    && bytes[offset + 2] == 't' && bytes[offset + 3] == ' ') {
                numChannels = littleEndianShort(bytes, offset + 10);
                sampleRate = littleEndianInt(bytes, offset + 12);
                bits = littleEndianShort(bytes, offset + 22);
            } else if (bytes[offset] == 'd' && bytes[offset + 1] == 'a'
                    && bytes[offset + 2] == 't' && bytes[offset + 3] == 'a') {
                dataOffset = offset + 8;
                dataSizeOffset = offset + 4;
                break;
            }
            offset += 8 + size;
        }
        if (dataOffset < 0 || sampleRate <= 0) throw new IllegalArgumentException("WAV header is missing required chunks");
        byte[] data = new byte[Math.max(0, bytes.length - dataOffset)];
        System.arraycopy(bytes, dataOffset, data, 0, data.length);
        return new WavHeader(sampleRate, numChannels, bits, dataOffset, dataSizeOffset, data);
    }

    private static class WavHeader {
        final int sampleRate, numChannels, bitsPerSample, dataOffset, dataSizeOffset;
        final byte[] data;
        WavHeader(int sampleRate, int numChannels, int bits, int dataOffset, int dataSizeOffset, byte[] data) {
            this.sampleRate = sampleRate;
            this.numChannels = numChannels;
            this.bitsPerSample = bits;
            this.dataOffset = dataOffset;
            this.dataSizeOffset = dataSizeOffset;
            this.data = data;
        }
    }

    public static byte[] synthesize(Context context, CloudTtsProvider provider, String text,
                                    CloudVoiceOptions options, ProgressListener progress) throws Exception {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm == null ? null : pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "Sovra:CloudTts");
        if (wakeLock != null) wakeLock.acquire();
        try {
            boolean groq = "groq_orpheus".equals(provider.getId());
            boolean capcut = "capcut_tts".equals(provider.getId());
            String sanitized = sanitizeText(text, groq);
            List<String> chunks = splitUtf8Safe(sanitized, groq ? 180 : (capcut ? 220 : 1500), capcut);
            List<byte[]> responses = new ArrayList<>();
            Random random = new Random();
            for (int i = 0; i < chunks.size(); i++) {
                if (progress != null) progress.onProgress(i + 1, chunks.size());
                byte[] chunkAudio = synthesizeWithRetry(context, provider, chunks.get(i), options);
                responses.add(chunkAudio);
                if (i + 1 < chunks.size()) {
                    long delay;
                    if (groq) delay = 6250L;
                    else if (capcut) delay = 1800L + random.nextInt(401);
                    else delay = 350L;
                    try { Thread.sleep(delay); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
            boolean mp3 = provider instanceof FishAudioProvider
                    || provider instanceof CartesiaSonicProvider
                    || provider instanceof CloudflareAuraProvider;
            byte[] finalAudio = mp3 ? stitchMp3(responses) : stitchWav(responses);
            long characters = text.length();
            long bytes = finalAudio.length;
            SharedPreferences prefs = context.getSharedPreferences("spCloudUsage", Context.MODE_PRIVATE);
            long chars = prefs.getLong("usage_chars_" + provider.getId(), 0L);
            long audioBytes = prefs.getLong("usage_bytes_" + provider.getId(), 0L);
            prefs.edit().putLong("usage_chars_" + provider.getId(), chars + characters)
                    .putLong("usage_bytes_" + provider.getId(), audioBytes + bytes).apply();
            return finalAudio;
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private static byte[] synthesizeWithRetry(Context context, CloudTtsProvider provider,
                                               String text, CloudVoiceOptions options) throws Exception {
        Exception last = null;
        int[] delays = {2000, 5000};
        for (int attempt = 0; attempt <= 2; attempt++) {
            try {
                return provider.synthesize(context, text, options);
            } catch (Exception e) {
                last = e;
                if (attempt >= 2) break;
                String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
                if (!(message.contains("429") || message.contains("502") || message.contains("503")
                        || message.contains("timeout") || message.contains("socket"))) break;
                try { Thread.sleep(delays[attempt]); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last == null ? new Exception("Synthesis failed") : last;
    }

    private static final class LocaleHolder {}
}
