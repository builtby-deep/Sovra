package com.builtbydeep.sovra;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

public final class SovraAudio {
    private SovraAudio() {
    }

    public static GeneratedSpeech generate(Context context, String text) throws IOException {
        SharedPreferences modelPreferences =
                context.getSharedPreferences("sp1", Context.MODE_PRIVATE);
        SharedPreferences synthesisPreferences =
                context.getSharedPreferences("sp3", Context.MODE_PRIVATE);
        String type = modelPreferences.getString("active_model_type", "");
        String language = modelPreferences.getString("active_language", "en");
        if (type == null || type.trim().isEmpty()) {
            throw new IOException("Select and activate a voice model before generating speech");
        }
        float speed = synthesisPreferences.getFloat("voice_speed", 1.0f);
        float pitch = synthesisPreferences.getFloat("voice_pitch", 1.0f);
        float silence = synthesisPreferences.getFloat("silence_scale", 0.5f);
        int steps = synthesisPreferences.getInt("supertonic_num_steps", 16);
        int speaker = type.equals("kokoro")
                ? modelPreferences.getInt("active_kokoro_speaker", KokoroVoiceHelper.DEFAULT_SPEAKER_ID)
                : type.equals("supertonic")
                ? modelPreferences.getInt("active_supertonic_speaker", 0)
                : modelPreferences.getInt("active_piper_speaker", 0);
        return generate(context, text, type, language, speaker, speed, pitch, steps, silence,
                synthesisPreferences.getBoolean("smart_punct", false),
                synthesisPreferences.getBoolean("emotion_tags", false),
                synthesisPreferences.getBoolean("multi_speaker_tags", false));
    }

    public static GeneratedSpeech generatePreview(Context context, String text) throws IOException {
        return generate(context, text);
    }

    private static GeneratedSpeech generate(
            Context context,
            String text,
            String type,
            String language,
            int speaker,
            float speed,
            float pitch,
            int steps,
            float silence,
            boolean punctuation,
            boolean emotions,
            boolean speakerTags) throws IOException {
        Engine engine = new Engine(context, type, language, speaker, steps);
        try {
            byte[] pcm;
            if (punctuation || emotions || speakerTags) {
                AudioEmotionHelper helper = new AudioEmotionHelper(
                        new AudioEmotionHelper.SegmentSynthesizer() {
                            @Override
                            public AudioEmotionHelper.AudioSegment generate(
                                    String segment, float segmentSpeed, float segmentPitch) {
                                return new AudioEmotionHelper.AudioSegment(
                                        engine.generate(segment, segmentSpeed, segmentPitch),
                                        engine.sampleRate());
                            }

                            @Override
                            public int getSampleRate() {
                                return engine.sampleRate();
                            }
                        });
                pcm = helper.processAndGenerate(text, punctuation, emotions,
                        speed, pitch, 1.0f, silence);
            } else {
                pcm = engine.generate(text, speed, pitch);
            }
            return new GeneratedSpeech(pcm, engine.sampleRate());
        } finally {
            engine.close();
        }
    }

    public static final class GeneratedSpeech {
        public final byte[] pcm;
        public final int sampleRate;

        GeneratedSpeech(byte[] pcm, int sampleRate) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
        }
    }

    private static final class Engine implements AutoCloseable {
        private final KokoroEngine kokoro;
        private final SupertonicEngine supertonic;
        private final VoiceEngine voice;
        private final String type;
        private final String language;
        private final int speaker;
        private final int steps;

        Engine(Context context, String type, String language, int speaker, int steps)
                throws IOException {
            this.type = type.toLowerCase(java.util.Locale.ROOT);
            this.language = language;
            this.speaker = Math.max(0, speaker);
            this.steps = Math.max(1, Math.min(16, steps));
            SharedPreferences model = context.getSharedPreferences("sp1", Context.MODE_PRIVATE);
            SharedPreferences synthesis = context.getSharedPreferences("sp3", Context.MODE_PRIVATE);
            if (this.type.equals("kokoro")) {
                kokoro = new KokoroEngine();
                supertonic = null;
                voice = null;
                kokoro.initialize(context, this.speaker, Math.max(1,
                        Runtime.getRuntime().availableProcessors() / 2));
            } else if (this.type.equals("supertonic")) {
                kokoro = null;
                supertonic = new SupertonicEngine();
                voice = null;
                supertonic.initialize(context, Math.max(1,
                        Runtime.getRuntime().availableProcessors() / 2));
            } else {
                kokoro = null;
                supertonic = null;
                voice = new VoiceEngine();
                String modelPath = model.getString("active_model", null);
                String tokensPath = model.getString("active_tokens", null);
                int style = Math.max(0, Math.min(2, synthesis.getInt("voice_style", 0)));
                float noise = style == 0 ? 0.33f : style == 1 ? 0.667f : 0.85f;
                float noiseW = style == 0 ? 0.50f : style == 1 ? 0.80f : 0.90f;
                voice.initialize(context, modelPath, tokensPath, this.type, language,
                        Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                        noise, noiseW);
            }
        }

        byte[] generate(String text, float speed, float pitch) {
            if (kokoro != null) {
                return kokoro.generate(text, speaker, speed, pitch);
            }
            if (supertonic != null) {
                return supertonic.generate(text, speaker, speed, language, steps, pitch);
            }
            return voice.generate(text, speaker, speed, pitch);
        }

        int sampleRate() {
            if (kokoro != null) return kokoro.getSampleRate();
            if (supertonic != null) return supertonic.getSampleRate();
            return voice.getSampleRate();
        }

        @Override
        public void close() {
            if (kokoro != null) kokoro.close();
            if (supertonic != null) supertonic.close();
            if (voice != null) voice.close();
        }
    }
}
