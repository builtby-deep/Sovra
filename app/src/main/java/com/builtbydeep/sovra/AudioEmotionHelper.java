package com.builtbydeep.sovra;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AudioEmotionHelper {
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("(\\[[a-zA-Z]+\\]|\\.\\.\\.|[.,!?।])");
    private static volatile boolean speakerGapPending;

    private final SegmentSynthesizer synthesizer;

    public AudioEmotionHelper(SegmentSynthesizer synthesizer) {
        if (synthesizer == null) {
            throw new IllegalArgumentException("A segment synthesizer is required");
        }
        this.synthesizer = synthesizer;
    }

    public static void triggerSpeakerGap() {
        synchronized (AudioEmotionHelper.class) {
            speakerGapPending = true;
        }
    }

    public byte[] processAndGenerate(
            String inputText,
            boolean isPunctOn,
            boolean isEmotionOn,
            float baseSpeed,
            float basePitch,
            float baseVolume,
            float silenceScaleUI) {
        if (inputText == null || inputText.isEmpty()) {
            return new byte[0];
        }
        float speed = positiveOrDefault(baseSpeed, 1.0f);
        float pitch = positiveOrDefault(basePitch, 1.0f);
        float volume = Float.isFinite(baseVolume) ? Math.max(0.0f, baseVolume) : 1.0f;
        float silenceScale = Float.isFinite(silenceScaleUI)
                ? Math.max(0.0f, Math.min(1.0f, silenceScaleUI)) : 0.5f;
        float multiplier = silenceScale * 2.0f;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Profile current = Profile.normal(speed, pitch, volume);
        float previousVolume = volume;
        String textToProcess = isEmotionOn ? inputText.replace("[]", "[normal]") : inputText;

        if (consumeSpeakerGap()) {
            appendSilence(output, Math.round(500.0f * multiplier), synthesizer.getSampleRate());
        }

        StringBuilder text = new StringBuilder();
        Matcher matcher = TOKEN_PATTERN.matcher(textToProcess);
        int cursor = 0;
        while (matcher.find()) {
            text.append(textToProcess, cursor, matcher.start());
            String token = matcher.group();
            if (isEmotionOn && token.startsWith("[")) {
                previousVolume = synthesizeText(
                        text, output, current, previousVolume);
                text.setLength(0);
                current = Profile.forTag(token, speed, pitch, volume);
            } else if (isPunctuation(token)) {
                if (isPunctOn) {
                    previousVolume = synthesizeText(text, output, current, previousVolume);
                    text.setLength(0);
                    int delay = punctuationDelayMs(token, multiplier, current.speed);
                    appendSilence(output, delay, synthesizer.getSampleRate());
                } else {
                    text.append(token);
                }
            } else {
                text.append(token);
            }
            cursor = matcher.end();
        }
        text.append(textToProcess, cursor, textToProcess.length());
        synthesizeText(text, output, current, previousVolume);
        return output.toByteArray();
    }

    public static int punctuationDelayMs(String punctuation, float multiplier, float speed) {
        int baseMs;
        switch (punctuation) {
            case ",":
                baseMs = 150;
                break;
            case "!":
                baseMs = 200;
                break;
            case "?":
                baseMs = 250;
                break;
            case ".":
            case "।":
                baseMs = 300;
                break;
            case "...":
                baseMs = 450;
                break;
            default:
                return 0;
        }
        float safeSpeed = positiveOrDefault(speed, 1.0f);
        double scaled = (baseMs * Math.max(0.0f, multiplier)) / safeSpeed;
        double jittered = scaled * (1.0 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1));
        return Math.max(0, Math.round((float) jittered));
    }

    private float synthesizeText(
            CharSequence text,
            ByteArrayOutputStream output,
            Profile profile,
            float startVolume) {
        if (text == null || text.length() == 0) {
            return startVolume;
        }
        AudioSegment segment = synthesizer.generate(text.toString(),
                profile.speed, profile.pitch);
        if (segment == null || segment.pcm16 == null) {
            throw new IllegalStateException("The segment synthesizer returned no PCM audio");
        }
        if ((segment.pcm16.length & 1) != 0) {
            throw new IllegalStateException("PCM16 audio must contain complete 16-bit samples");
        }
        applyVolumeEnvelope(segment.pcm16, segment.sampleRate, startVolume,
                profile.volume, profile.attackTimeMs);
        output.write(segment.pcm16, 0, segment.pcm16.length);
        return profile.volume;
    }

    private static void applyVolumeEnvelope(
            byte[] pcm, int sampleRate, float startVolume, float endVolume, int attackTimeMs) {
        int sampleCount = pcm.length / 2;
        int attackSamples = sampleRate > 0
                ? (int) Math.min(sampleCount, (long) sampleRate * attackTimeMs / 1000L)
                : 0;
        for (int i = 0; i < sampleCount; i++) {
            float envelope = i < attackSamples && attackSamples > 0
                    ? startVolume + (endVolume - startVolume) * (i / (float) attackSamples)
                    : endVolume;
            int offset = i * 2;
            int sample = (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
            int scaled = Math.round(sample * envelope);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            pcm[offset] = (byte) (scaled & 0xff);
            pcm[offset + 1] = (byte) ((scaled >>> 8) & 0xff);
        }
    }

    private static void appendSilence(ByteArrayOutputStream output, int durationMs, int sampleRate) {
        if (durationMs <= 0 || sampleRate <= 0) {
            return;
        }
        long byteCount = (long) sampleRate * durationMs / 1000L * 2L;
        if (byteCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested silence segment is too large");
        }
        output.write(new byte[(int) byteCount], 0, (int) byteCount);
    }

    private static boolean consumeSpeakerGap() {
        synchronized (AudioEmotionHelper.class) {
            if (!speakerGapPending) {
                return false;
            }
            speakerGapPending = false;
            return true;
        }
    }

    private static boolean isPunctuation(String token) {
        return token.equals(",") || token.equals("!") || token.equals("?")
                || token.equals(".") || token.equals("...") || token.equals("।");
    }

    private static float positiveOrDefault(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0f ? value : fallback;
    }

    public interface SegmentSynthesizer {
        AudioSegment generate(String text, float speed, float pitch);

        int getSampleRate();
    }

    public static final class AudioSegment {
        public final byte[] pcm16;
        public final int sampleRate;

        public AudioSegment(byte[] pcm16, int sampleRate) {
            this.pcm16 = pcm16;
            this.sampleRate = sampleRate;
        }
    }

    private static final class Profile {
        final float volume;
        final float speed;
        final float pitch;
        final int attackTimeMs;

        Profile(float volume, float speed, float pitch, int attackTimeMs) {
            this.volume = volume;
            this.speed = speed;
            this.pitch = pitch;
            this.attackTimeMs = attackTimeMs;
        }

        static Profile normal(float speed, float pitch, float volume) {
            return new Profile(volume, speed, pitch, 1500);
        }

        static Profile forTag(String rawTag, float baseSpeed, float basePitch, float baseVolume) {
            String tag = rawTag.toLowerCase(java.util.Locale.ROOT);
            switch (tag) {
                case "[whispers]":
                case "[whisper]":
                    return new Profile(0.65f, baseSpeed * 0.95f, basePitch * 1.05f, 2500);
                case "[angry]":
                    return new Profile(1.15f, baseSpeed * 1.05f, basePitch * 0.95f, 1500);
                case "[sad]":
                    return new Profile(0.80f, baseSpeed * 0.92f, basePitch * 0.98f, 2500);
                case "[sarcastically]":
                case "[sarcastic]":
                    return new Profile(1.00f, baseSpeed * 1.02f, basePitch * 0.95f, 1500);
                case "[giggles]":
                case "[giggle]":
                    return new Profile(1.10f, baseSpeed * 1.05f, basePitch * 1.10f, 1000);
                case "[normal]":
                case "[]":
                default:
                    return Profile.normal(baseSpeed, basePitch, baseVolume);
            }
        }
    }
}
