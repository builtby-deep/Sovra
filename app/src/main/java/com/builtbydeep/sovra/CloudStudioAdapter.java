package com.builtbydeep.sovra;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CloudStudioAdapter {
    private static final Pattern SPEAKER_TAG = Pattern.compile("\\[(speaker[12])\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKET_TAG = Pattern.compile("\\[(?!speaker[12]\\])[^\\]]*\\]", Pattern.CASE_INSENSITIVE);

    private CloudStudioAdapter() {}

    public static List<Segment> prepare(String providerId, String modelId, String text,
                                        CloudTtsProvider.Voice primary,
                                        List<CloudTtsProvider.Voice> availableVoices,
                                        SharedPreferences settings) throws IOException {
        if (CloudTtsClients.WITAI.equals(providerId)
                && WitAiTtsProvider.containsDevanagari(text)) {
            throw new IOException("Wit.ai supports English voices only.");
        }
        if (primary == null) throw new IOException("Select a cloud voice before generating audio.");
        float speed = settings.getFloat("voice_speed", 1.0f);
        float pitch = settings.getFloat("voice_pitch", 1.0f);
        boolean emotion = settings.getBoolean("emotion_tags", false);
        boolean multiSpeaker = settings.getBoolean("multi_speaker_tags", false);
        float silenceSeconds = settings.getFloat("silence_scale", 0.2f);
        int voiceStyle = settings.getInt("voice_style", 1);
        boolean keepEmotion = CloudTtsClients.ELEVENLABS.equals(providerId)
                && CloudTtsClients.ELEVEN_V3.equals(modelId) && emotion;
        CloudTtsProvider.Voice secondary = findSecondary(primary, availableVoices);
        if (multiSpeaker && secondary == null) {
            Matcher secondSpeaker = SPEAKER_TAG.matcher(text);
            while (secondSpeaker.find()) {
                if ("speaker2".equalsIgnoreCase(secondSpeaker.group(1))) {
                    throw new IOException("No secondary voice is available in the selected cloud voice's region.");
                }
            }
        }

        ArrayList<Segment> result = new ArrayList<>();
        Matcher matcher = multiSpeaker ? SPEAKER_TAG.matcher(text) : null;
        int cursor = 0;
        CloudTtsProvider.Voice currentVoice = primary;
        while (matcher != null && matcher.find()) {
            addSegment(result, text.substring(cursor, matcher.start()), currentVoice, keepEmotion);
            currentVoice = "speaker2".equalsIgnoreCase(matcher.group(1)) && secondary != null
                    ? secondary : primary;
            cursor = matcher.end();
        }
        addSegment(result, text.substring(cursor), currentVoice, keepEmotion);
        if (result.isEmpty()) throw new IOException("Enter text with speakable words to synthesize.");
        for (Segment segment : result) {
            segment.speed = speed;
            segment.pitch = pitch;
            segment.silenceSeconds = silenceSeconds;
            segment.voiceStyle = voiceStyle;
        }
        return result;
    }

    private static void addSegment(List<Segment> output, String source,
                                   CloudTtsProvider.Voice voice, boolean keepEmotion) {
        String text = keepEmotion ? source : BRACKET_TAG.matcher(source).replaceAll("");
        text = SPEAKER_TAG.matcher(text).replaceAll("");
        text = text.trim();
        if (!text.isEmpty()) output.add(new Segment(text, voice));
    }

    private static CloudTtsProvider.Voice findSecondary(CloudTtsProvider.Voice primary,
                                                        List<CloudTtsProvider.Voice> voices) {
        if (voices == null) return null;
        for (CloudTtsProvider.Voice voice : voices) {
            if (!voice.id.equals(primary.id) && voice.locale.equalsIgnoreCase(primary.locale)
                    && voice.region.equalsIgnoreCase(primary.region)) return voice;
        }
        return null;
    }

    public static Synthesis synthesize(Context context, String providerId, String modelId, String apiKey,
                                      List<Segment> segments, boolean smartPunctuation,
                                      float silenceSeconds, SharedPreferences preferences)
            throws IOException {
        return synthesize(context, providerId, modelId, apiKey, segments, smartPunctuation,
                silenceSeconds, preferences, () -> !Thread.currentThread().isInterrupted());
    }

    public static Synthesis synthesize(Context context, String providerId, String modelId, String apiKey,
                                      List<Segment> segments, boolean smartPunctuation,
                                      float silenceSeconds, SharedPreferences preferences,
                                      java.util.function.BooleanSupplier shouldContinue)
            throws IOException {
        CloudTtsProvider provider = CloudTtsClients.get(providerId);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        ByteArrayOutputStream mp3 = CloudTtsClients.WITAI.equals(providerId)
                ? new ByteArrayOutputStream() : null;
        int sampleRate = 0;
        int spokenCharacters = 0;
        try {
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                Segment segment = segments.get(segmentIndex);
                List<String> chunks = CloudTtsClients.WITAI.equals(providerId)
                        ? WitAiTtsProvider.splitIntoChunks(segment.text)
                        : CloudTtsClients.splitText(segment.text, provider.getSafeChunkLimit());
                List<File> witChunkFiles = Collections.emptyList();
                if (CloudTtsClients.WITAI.equals(providerId)) {
                    if (WitAiTtsProvider.containsDevanagari(segment.text)) {
                        throw new IOException("Wit.ai supports English voices only.");
                    }
                    witChunkFiles = ((WitAiTtsProvider) provider).synthesizeToFiles(context, apiKey,
                            segment.voice, chunks, segment.voiceStyle, shouldContinue);
                }
                for (int i = 0; i < chunks.size(); i++) {
                    if (!shouldContinue.getAsBoolean()) {
                        throw new IOException("Cloud TTS synthesis was cancelled.");
                    }
                    String chunk = chunks.get(i);
                    String requestText = chunk;
                    if (CloudTtsClients.RIME.equals(providerId)
                            && CloudTtsClients.RIME_MIST.equals(modelId) && smartPunctuation) {
                        requestText = injectRimePauses(requestText);
                    }
                    byte[] audio;
                    if (CloudTtsClients.WITAI.equals(providerId)) {
                        File chunkFile = witChunkFiles.get(i);
                        try {
                            audio = java.nio.file.Files.readAllBytes(chunkFile.toPath());
                            mp3.write(mp3.size() == 0 ? audio : WitAiTtsProvider.stripId3v2(audio));
                        } finally {
                            if (!chunkFile.delete() && chunkFile.exists()) {
                                android.util.Log.w("CloudStudioAdapter",
                                        "Unable to remove a temporary Wit.ai audio chunk.");
                            }
                        }
                    } else {
                        audio = provider.synthesize(apiKey, modelId, segment.voice, requestText,
                                normalizeLanguage(segment.voice.locale), segment.speed, segment.pitch,
                                smartPunctuation, segment.voiceStyle);
                    }
                    DecodedAudio decoded = decodeMp3(context, audio);
                    if (sampleRate == 0) sampleRate = decoded.sampleRate;
                    if (sampleRate != decoded.sampleRate) {
                        throw new IOException("Cloud TTS changed its audio sample rate.");
                    }
                    if (pcm.size() > 0) {
                        float gap = Math.max(0.0f, Math.min(3.0f, silenceSeconds));
                        if (smartPunctuation && gap > 0.001f && (CloudTtsClients.RIME.equals(providerId)
                                && CloudTtsClients.RIME_CODA.equals(modelId)
                                || CloudTtsClients.ELEVENLABS.equals(providerId))) {
                            pcm.write(new byte[Math.round(sampleRate * gap) * 2]);
                        }
                    }
                    pcm.write(decoded.pcm);
                    spokenCharacters += countSpokenCharacters(chunk);
                }
            }
        } finally {
            if (CloudTtsClients.WITAI.equals(providerId)) {
                ((WitAiTtsProvider) provider).cancelAndClean(context);
            }
        }
        if (sampleRate == 0 || pcm.size() == 0) throw new IOException("Cloud TTS returned no playable audio.");
        recordUsage(preferences, providerId, spokenCharacters);
        return new Synthesis(pcm.toByteArray(), sampleRate, spokenCharacters,
                mp3 == null ? null : mp3.toByteArray());
    }

    private static String injectRimePauses(String text) {
        return text.replaceAll("([.!?])\\s+", "$1<200>");
    }

    private static int countSpokenCharacters(String text) {
        return BRACKET_TAG.matcher(text).replaceAll("").replaceAll("<[^>]+>", "").length();
    }

    private static String normalizeLanguage(String locale) {
        String language = Locale.forLanguageTag(locale).getLanguage();
        return language.isEmpty() ? "en" : language;
    }

    private static void recordUsage(SharedPreferences preferences, String providerId, int count) {
        String month = monthKey();
        String monthKey = "cloud_usage_month_" + providerId;
        String usageKey = "cloud_usage_chars_" + providerId;
        if (!month.equals(preferences.getString(monthKey, ""))) {
            preferences.edit().putString(monthKey, month).putInt(usageKey, 0).apply();
        }
        preferences.edit().putInt(usageKey, preferences.getInt(usageKey, 0) + count).apply();
    }

    public static int getMonthlyUsage(SharedPreferences preferences, String providerId) {
        String month = monthKey();
        String monthKey = "cloud_usage_month_" + providerId;
        String usageKey = "cloud_usage_chars_" + providerId;
        if (!month.equals(preferences.getString(monthKey, ""))) {
            preferences.edit().putString(monthKey, month).putInt(usageKey, 0).apply();
            return 0;
        }
        return preferences.getInt(usageKey, 0);
    }

    public static void resetMonthlyUsage(SharedPreferences preferences, String providerId) {
        preferences.edit().putString("cloud_usage_month_" + providerId, monthKey())
                .putInt("cloud_usage_chars_" + providerId, 0).apply();
    }

    private static String monthKey() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d", calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private static DecodedAudio decodeMp3(Context context, byte[] mp3Bytes) throws IOException {
        File inputFile = File.createTempFile("cloud-tts-", ".mp3", context.getCacheDir());
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
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false;
            boolean outputEnded = false;
            int outputSampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT;
            while (!outputEnded) {
                if (!inputEnded) {
                    int inputIndex = decoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) throw new IOException("Unable to read MP3 decoder input.");
                        int size = extractor.readSampleData(inputBuffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outputIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size];
                        outputBuffer.get(bytes);
                        appendMonoPcm(decoded, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN),
                                channels, pcmEncoding);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = decoder.getOutputFormat();
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        outputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING);
                }
            }

            if (decoded.size() == 0) throw new IOException("MP3 decoder returned no audio.");
            return new DecodedAudio(decoded.toByteArray(), outputSampleRate);
        } catch (IllegalStateException error) {
            throw new IOException("Unable to decode cloud MP3 audio.", error);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (IllegalStateException ignored) {}
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

    public static final class Segment {
        public final String text;
        public final CloudTtsProvider.Voice voice;
        float speed = 1.0f;
        float pitch = 1.0f;
        float silenceSeconds = 0.2f;
        int voiceStyle = 1;

        Segment(String text, CloudTtsProvider.Voice voice) {
            this.text = text;
            this.voice = voice;
        }
    }

    public static final class Synthesis {
        public final byte[] pcm;
        public final int sampleRate;
        public final int spokenCharacters;
        public final byte[] mp3;

        Synthesis(byte[] pcm, int sampleRate, int spokenCharacters, byte[] mp3) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.spokenCharacters = spokenCharacters;
            this.mp3 = mp3;
        }
    }

    private static final class DecodedAudio {
        final byte[] pcm;
        final int sampleRate;

        DecodedAudio(byte[] pcm, int sampleRate) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
        }
    }
}
