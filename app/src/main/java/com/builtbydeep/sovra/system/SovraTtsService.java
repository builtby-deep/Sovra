package com.builtbydeep.sovra.system;

import android.media.AudioFormat;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import android.util.Log;

import com.builtbydeep.sovra.AudioEmotionHelper;
import com.builtbydeep.sovra.KokoroEngine;
import com.builtbydeep.sovra.KokoroVoiceHelper;
import com.builtbydeep.sovra.PiperModelCatalog;
import com.builtbydeep.sovra.SupertonicEngine;
import com.builtbydeep.sovra.VoiceEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SovraTtsService extends TextToSpeechService {
    private static final String TAG = "SovraTtsService";
    private static final String MODEL_PREFERENCES = "sp1";
    private static final String SYNTHESIS_PREFERENCES = "sp3";
    private static final Pattern SUPERTONIC_VOICE = Pattern.compile(
            "^Supertonic_([A-Za-z0-9_-]+)_Speaker_(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_VOICE = Pattern.compile(
            "^Sovra_([A-Za-z0-9_-]+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
            "(?<=[.!?\\n|।])\\s+");
    private static final String[] SUPERTONIC_LANGUAGES = {
            "en", "ko", "ja", "ar", "bg", "cs", "da", "de", "el", "es", "et", "fi",
            "fr", "hi", "hr", "hu", "id", "it", "lt", "lv", "nl", "pl", "pt", "ro",
            "ru", "sk", "sl", "sv", "tr", "uk", "vi"
    };
    private static final String[] KOKORO_LANGUAGES = {
            "en-US", "en-GB", "es", "fr", "hi", "it", "ja", "pt", "zh"
    };
    private static final Set<String> AVAILABLE_LOCALE_TAGS = createAvailableLocaleTags();

    private final Object engineLock = new Object();
    private volatile boolean stopped;
    private Locale loadedLocale = Locale.US;
    private Object loadedEngine;
    private String loadedEngineKey;
    private String lastSpeakerKey;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected int onIsLanguageAvailable(String language, String country, String variant) {
        if (language == null || language.trim().isEmpty()) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        String languageCode = language.toLowerCase(Locale.ROOT);
        boolean languageAvailable = false;
        for (String tag : AVAILABLE_LOCALE_TAGS) {
            Locale supported = Locale.forLanguageTag(tag);
            if (!supported.getLanguage().equalsIgnoreCase(languageCode)) {
                continue;
            }
            languageAvailable = true;
            if (country == null || country.isEmpty()) {
                return TextToSpeech.LANG_AVAILABLE;
            }
            if (supported.getCountry().equalsIgnoreCase(country)) {
                return TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
        }
        return languageAvailable
                ? TextToSpeech.LANG_AVAILABLE : TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        int availability = onIsLanguageAvailable(language, country, variant);
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED
                || availability == TextToSpeech.LANG_MISSING_DATA) {
            return availability;
        }
        loadedLocale = new Locale(language, country == null ? "" : country,
                variant == null ? "" : variant);
        return availability;
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{
                loadedLocale.getLanguage(),
                loadedLocale.getCountry(),
                loadedLocale.getVariant()
        };
    }

    @Override
    public String onGetDefaultVoiceNameFor(String language, String country, String variant) {
        String localeTag = localeTag(language, country, variant);
        TtsDefaultHelper.TtsDefault systemDefault = getSystemDefault(localeTag);
        if (systemDefault != null) {
            return voiceNameForDefault(localeTag, systemDefault);
        }
        return "Sovra_" + languageTagForVoice(localeTag);
    }

    @Override
    public List<Voice> onGetVoices() {
        Map<String, Voice> voices = new LinkedHashMap<>();
        for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
            Locale locale = Locale.forLanguageTag(voice.language);
            addVoice(voices, voice.displayName + " (Kokoro)", locale);
        }
        for (String language : SUPERTONIC_LANGUAGES) {
            Locale locale = Locale.forLanguageTag(language);
            for (int speakerId = 0; speakerId < 10; speakerId++) {
                addVoice(voices, "Supertonic_" + language + "_Speaker_" + speakerId, locale);
            }
        }
        for (PiperModelCatalog.Model model : PiperModelCatalog.getModels()) {
            addVoice(voices, model.name, piperLocale(model));
        }
        for (String tag : AVAILABLE_LOCALE_TAGS) {
            addVoice(voices, "Sovra_" + languageTagForVoice(tag), Locale.forLanguageTag(tag));
        }
        return Collections.unmodifiableList(new ArrayList<>(voices.values()));
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        stopped = false;
        if (request == null || callback == null) {
            if (callback != null) {
                callback.error();
            }
            return;
        }

        try {
            String languageTag = requestLocaleTag(request);
            VoiceSpec spec = resolveVoice(request.getVoiceName(), languageTag);
            if (spec == null) {
                throw new IOException("No Sovra voice is configured for " + languageTag);
            }
            float speed = scaleFromSystem(request.getSpeechRate());
            float pitch = scaleFromSystem(request.getPitch());
            byte[] pcm;
            int sampleRate;
            synchronized (engineLock) {
                ensureEngine(spec);
                boolean smartPunctuation = getSynthesisPreferences()
                        .getBoolean("smart_punct", false);
                boolean emotionTags = getSynthesisPreferences()
                        .getBoolean("emotion_tags", false);
                boolean speakerTags = getSynthesisPreferences()
                        .getBoolean("multi_speaker_tags", false);

                String speakerKey = spec.engineKey() + ':' + spec.speakerId;
                if (speakerTags && lastSpeakerKey != null && !lastSpeakerKey.equals(speakerKey)) {
                    AudioEmotionHelper.triggerSpeakerGap();
                }
                lastSpeakerKey = speakerKey;

                String inputText = request.getCharSequenceText() == null
                        ? "" : request.getCharSequenceText().toString();
                pcm = synthesizeText(spec, inputText, speed, pitch,
                        smartPunctuation, emotionTags, speakerTags);
                sampleRate = getLoadedSampleRate();
            }
            streamPcm(pcm, sampleRate, callback);
        } catch (Throwable failure) {
            Log.e(TAG, "Speech synthesis failed", failure);
            callback.error();
        }
    }

    @Override
    protected void onStop() {
        stopped = true;
    }

    public static String[] getAvailableLocaleTags() {
        return AVAILABLE_LOCALE_TAGS.toArray(new String[0]);
    }

    private byte[] synthesizeText(
            VoiceSpec spec,
            String inputText,
            float speed,
            float pitch,
            boolean smartPunctuation,
            boolean emotionTags,
            boolean speakerTags) throws IOException {
        if (inputText.trim().isEmpty()) {
            return new byte[0];
        }
        String[] sentences = SENTENCE_BOUNDARY.split(inputText, -1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        float silenceScale = getSynthesisPreferences().getFloat("silence_scale", 0.5f);
        boolean useEmotionProcessor = smartPunctuation || emotionTags || speakerTags;
        AudioEmotionHelper emotionHelper = useEmotionProcessor
                ? new AudioEmotionHelper(new AudioEmotionHelper.SegmentSynthesizer() {
                    @Override
                    public AudioEmotionHelper.AudioSegment generate(
                            String text, float segmentSpeed, float segmentPitch) {
                        if (stopped) {
                            return new AudioEmotionHelper.AudioSegment(new byte[0], getLoadedSampleRate());
                        }
                        byte[] samples = generateWithLoadedEngine(
                                spec, text, segmentSpeed, segmentPitch);
                        return new AudioEmotionHelper.AudioSegment(samples, getLoadedSampleRate());
                    }

                    @Override
                    public int getSampleRate() {
                        return getLoadedSampleRate();
                    }
                }) : null;

        for (String sentence : sentences) {
            if (stopped) {
                break;
            }
            String segment = sentence.trim();
            if (segment.isEmpty()) {
                continue;
            }
            byte[] bytes;
            if (emotionHelper != null) {
                bytes = emotionHelper.processAndGenerate(segment, smartPunctuation,
                        emotionTags, speed, pitch, 1.0f, silenceScale);
            } else {
                bytes = generateWithLoadedEngine(spec, segment, speed, pitch);
            }
            output.write(bytes, 0, bytes.length);
        }
        return output.toByteArray();
    }

    private VoiceSpec resolveVoice(String requestedName, String requestLanguage) throws IOException {
        String name = requestedName == null ? "" : requestedName.trim();
        Matcher supertonic = SUPERTONIC_VOICE.matcher(name);
        if (supertonic.matches()) {
            return VoiceSpec.supertonic(supertonic.group(1),
                    parseSpeakerId(supertonic.group(2)));
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        String kokoroSuffix = " (kokoro)";
        if (lowerName.endsWith(kokoroSuffix)) {
            String requestedKokoroName = name.substring(0, name.length() - kokoroSuffix.length());
            KokoroVoiceHelper.Voice voice =
                    findKokoroVoice(requestedKokoroName, requestLanguage);
            if (voice == null) {
                throw new IOException("Unknown Kokoro voice: " + requestedName);
            }
            return VoiceSpec.kokoro(voice.language, voice.id);
        }

        for (PiperModelCatalog.Model model : PiperModelCatalog.getModels()) {
            if (model.name.equalsIgnoreCase(name)
                    || model.archiveName.equalsIgnoreCase(name)
                    || model.modelDirectory.equalsIgnoreCase(name)) {
                return VoiceSpec.vits(
                        "vits",
                        piperLocale(model).toLanguageTag(),
                        new File(new File(getFilesDir(), "models"), model.modelDirectory)
                                .getAbsolutePath(),
                        new File(new File(new File(getFilesDir(), "models"),
                                model.modelDirectory), "tokens.txt").getAbsolutePath(),
                        0);
            }
        }

        Matcher generic = GENERIC_VOICE.matcher(name);
        String languageTag = generic.matches() ? generic.group(1) : requestLanguage;
        if (name.isEmpty() || generic.matches()) {
            TtsDefaultHelper.TtsDefault saved = getSystemDefault(languageTag);
            if (saved != null) {
                return fromSavedDefault(saved, languageTag);
            }
            VoiceSpec active = resolveActiveModel(languageTag);
            if (active != null) {
                return active;
            }
        }

        TtsDefaultHelper.TtsDefault saved = getSystemDefault(languageTag);
        if (saved != null) {
            return fromSavedDefault(saved, languageTag);
        }
        if (!generic.matches() && !name.isEmpty()) {
            throw new IOException("Unknown Sovra voice: " + requestedName);
        }
        return null;
    }

    private VoiceSpec resolveActiveModel(String languageTag) {
        android.content.SharedPreferences models =
                getSharedPreferences(MODEL_PREFERENCES, MODE_PRIVATE);
        String type = models.getString("active_model_type", "");
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String language = valueOr(models.getString("active_language", ""), languageTag);
        if ("kokoro".equals(normalizedType)) {
            return VoiceSpec.kokoro(language,
                    models.getInt("active_kokoro_speaker",
                            KokoroVoiceHelper.DEFAULT_SPEAKER_ID));
        }
        if ("supertonic".equals(normalizedType)) {
            return VoiceSpec.supertonic(language,
                    models.getInt("active_supertonic_speaker", 0));
        }
        if ("vits".equals(normalizedType) || "mms".equals(normalizedType)) {
            String modelPath = models.getString("active_model", "");
            String tokensPath = models.getString("active_tokens", "");
            if (modelPath == null || modelPath.isEmpty()
                    || tokensPath == null || tokensPath.isEmpty()) {
                return null;
            }
            return VoiceSpec.vits(normalizedType, language, modelPath, tokensPath,
                    models.getInt("active_piper_speaker", 0));
        }
        return null;
    }

    private VoiceSpec fromSavedDefault(
            TtsDefaultHelper.TtsDefault value, String languageTag) throws IOException {
        String type = value.modelType.toLowerCase(Locale.ROOT);
        if ("kokoro".equals(type)) {
            return VoiceSpec.kokoro(languageTag, value.speakerId);
        }
        if ("supertonic".equals(type)) {
            return VoiceSpec.supertonic(languageTag, value.speakerId);
        }
        if ("vits".equals(type) || "mms".equals(type)) {
            String directory = value.modelDir == null ? "" : value.modelDir;
            File modelDirectory = directory.isEmpty() ? null : new File(directory);
            if (modelDirectory != null && !modelDirectory.isAbsolute()) {
                modelDirectory = new File(getFilesDir(), directory);
            }
            String modelPath = nonEmpty(value.onnxPath);
            String tokensPath = nonEmpty(value.tokensPath);
            if (modelDirectory != null) {
                if (modelPath == null) {
                    modelPath = new File(modelDirectory, "model.onnx").getAbsolutePath();
                }
                if (tokensPath == null) {
                    tokensPath = new File(modelDirectory, "tokens.txt").getAbsolutePath();
                }
            }
            if (modelPath == null || tokensPath == null) {
                throw new IOException("Saved system TTS default is missing VITS model paths");
            }
            return VoiceSpec.vits(type, languageTag, modelPath, tokensPath, value.speakerId);
        }
        throw new IOException("Unsupported saved system TTS model type: " + value.modelType);
    }

    private TtsDefaultHelper.TtsDefault getSystemDefault(String languageTag) {
        String normalized = normalizeLanguageTag(languageTag);
        TtsDefaultHelper.TtsDefault saved = TtsDefaultHelper.get(this, normalized);
        if (saved == null) {
            int separator = normalized.indexOf('-');
            if (separator > 0) {
                saved = TtsDefaultHelper.get(this, normalized.substring(0, separator));
            }
        }
        return saved;
    }

    private void ensureEngine(VoiceSpec spec) throws IOException {
        String key = spec.engineKey() + ':' + getSynthesisPreferences().getInt("voice_style", 1);
        if (loadedEngine != null && key.equals(loadedEngineKey)) {
            return;
        }
        releaseLoadedEngine();
        int voiceStyle = getSynthesisPreferences().getInt("voice_style", 1);
        float noiseScale = voiceStyle == 0 ? 0.33f : voiceStyle == 2 ? 0.85f : 0.667f;
        float noiseScaleW = voiceStyle == 0 ? 0.50f : voiceStyle == 2 ? 0.90f : 0.80f;
        switch (spec.type) {
            case "kokoro": {
                KokoroEngine engine = new KokoroEngine();
                engine.initialize(this, spec.speakerId, Runtime.getRuntime().availableProcessors());
                loadedEngine = engine;
                break;
            }
            case "supertonic": {
                SupertonicEngine engine = new SupertonicEngine();
                engine.initialize(this, Runtime.getRuntime().availableProcessors());
                loadedEngine = engine;
                break;
            }
            case "vits":
            case "mms": {
                VoiceEngine engine = new VoiceEngine();
                engine.initialize(this, spec.modelPath, spec.tokensPath, spec.type,
                        spec.languageTag, Runtime.getRuntime().availableProcessors(),
                        noiseScale, noiseScaleW);
                loadedEngine = engine;
                break;
            }
            default:
                throw new IOException("Unsupported Sovra TTS model type: " + spec.type);
        }
        loadedEngineKey = key;
    }

    private byte[] generateWithLoadedEngine(
            VoiceSpec spec, String text, float speed, float pitch) {
        if (stopped) {
            return new byte[0];
        }
        if (loadedEngine instanceof KokoroEngine) {
            return ((KokoroEngine) loadedEngine).generate(
                    text, spec.speakerId, speed, pitch);
        }
        if (loadedEngine instanceof SupertonicEngine) {
            int steps = getSynthesisPreferences().getInt("supertonic_num_steps", 16);
            return ((SupertonicEngine) loadedEngine).generate(
                    text, spec.speakerId, speed, spec.languageTag, steps, pitch);
        }
        if (loadedEngine instanceof VoiceEngine) {
            return ((VoiceEngine) loadedEngine).generate(text, spec.speakerId, speed, pitch);
        }
        throw new IllegalStateException("No TTS engine is initialized");
    }

    private int getLoadedSampleRate() {
        if (loadedEngine instanceof KokoroEngine) {
            return ((KokoroEngine) loadedEngine).getSampleRate();
        }
        if (loadedEngine instanceof SupertonicEngine) {
            return ((SupertonicEngine) loadedEngine).getSampleRate();
        }
        if (loadedEngine instanceof VoiceEngine) {
            return ((VoiceEngine) loadedEngine).getSampleRate();
        }
        throw new IllegalStateException("No TTS engine is initialized");
    }

    private void streamPcm(byte[] pcm, int sampleRate, SynthesisCallback callback) {
        if (stopped) {
            callback.error();
            return;
        }
        if (sampleRate <= 0 || (pcm.length & 1) != 0) {
            throw new IllegalStateException("TTS output must be mono PCM16 with a valid sample rate");
        }
        if (callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
                != TextToSpeech.SUCCESS) {
            return;
        }
        int maxBufferSize = callback.getMaxBufferSize();
        int chunkSize = Math.min(maxBufferSize > 0 ? maxBufferSize : 8192, 8192);
        chunkSize = Math.max(2, chunkSize & ~1);
        for (int offset = 0; offset < pcm.length && !stopped; offset += chunkSize) {
            int count = Math.min(chunkSize, pcm.length - offset);
            if ((count & 1) != 0) {
                count--;
            }
            if (count > 0 && callback.audioAvailable(pcm, offset, count)
                    != TextToSpeech.SUCCESS) {
                callback.error();
                return;
            }
        }
        if (stopped) {
            callback.error();
        } else {
            callback.done();
        }
    }

    private void releaseLoadedEngine() {
        if (loadedEngine instanceof KokoroEngine) {
            ((KokoroEngine) loadedEngine).release();
        } else if (loadedEngine instanceof SupertonicEngine) {
            ((SupertonicEngine) loadedEngine).release();
        } else if (loadedEngine instanceof VoiceEngine) {
            ((VoiceEngine) loadedEngine).release();
        }
        loadedEngine = null;
        loadedEngineKey = null;
    }

    private KokoroVoiceHelper.Voice findKokoroVoice(String name, String requestedLanguage) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String language = normalizeLanguageTag(requestedLanguage);
        KokoroVoiceHelper.Voice fallback = null;
        for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
            if (!voice.displayName.equalsIgnoreCase(name)
                    && !voice.modelName.equalsIgnoreCase(name)) {
                continue;
            }
            if (fallback == null) {
                fallback = voice;
            }
            if (normalizeLanguageTag(voice.language).equals(language)
                    || primaryLanguage(voice.language).equals(primaryLanguage(language))) {
                return voice;
            }
        }
        if (fallback == null && normalizedName.equals("alpha")) {
            return KokoroVoiceHelper.getVoice(KokoroVoiceHelper.DEFAULT_SPEAKER_ID);
        }
        return fallback;
    }

    private static void addVoice(Map<String, Voice> voices, String name, Locale locale) {
        voices.putIfAbsent(name + '|' + locale.toLanguageTag(),
                new Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL,
                        false, Collections.emptySet()));
    }

    private static Set<String> createAvailableLocaleTags() {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(Arrays.asList(KOKORO_LANGUAGES));
        tags.addAll(Arrays.asList(SUPERTONIC_LANGUAGES));
        tags.add("hi-IN");
        return Collections.unmodifiableSet(tags);
    }

    private static String localeTag(String language, String country, String variant) {
        Locale locale = new Locale(language == null ? "" : language,
                country == null ? "" : country, variant == null ? "" : variant);
        return locale.toLanguageTag();
    }

    private static String requestLocaleTag(SynthesisRequest request) {
        return localeTag(request.getLanguage(), request.getCountry(), request.getVariant());
    }

    private static String languageTagForVoice(String localeTag) {
        String normalized = normalizeLanguageTag(localeTag);
        return normalized.isEmpty() || "und".equals(normalized) ? "en" : normalized;
    }

    private static String normalizeLanguageTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return "en";
        }
        String normalized = Locale.forLanguageTag(tag.trim().replace('_', '-')).toLanguageTag();
        return "und".equalsIgnoreCase(normalized)
                ? tag.trim().replace('_', '-').toLowerCase(Locale.ROOT)
                : normalized;
    }

    private static String primaryLanguage(String tag) {
        Locale locale = Locale.forLanguageTag(tag.replace('_', '-'));
        return locale.getLanguage();
    }

    private static Locale piperLocale(PiperModelCatalog.Model model) {
        if (model.archiveName.contains("en_GB")) {
            return Locale.UK;
        }
        if (model.archiveName.contains("hi_IN")) {
            return new Locale("hi", "IN");
        }
        return Locale.US;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String voiceNameForDefault(
            String languageTag, TtsDefaultHelper.TtsDefault value) {
        String type = value.modelType.toLowerCase(Locale.ROOT);
        if ("supertonic".equals(type)) {
            return "Supertonic_" + languageTagForVoice(languageTag)
                    + "_Speaker_" + Math.max(0, Math.min(9, value.speakerId));
        }
        if ("kokoro".equals(type)) {
            int speakerId = value.speakerId >= 0
                    && value.speakerId < KokoroVoiceHelper.getVoices().size()
                    ? value.speakerId : KokoroVoiceHelper.DEFAULT_SPEAKER_ID;
            return KokoroVoiceHelper.getVoice(speakerId).displayName + " (Kokoro)";
        }
        return "Sovra_" + languageTagForVoice(languageTag);
    }

    private static String nonEmpty(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static float scaleFromSystem(int value) {
        return value <= 0 ? 1.0f : value / 100.0f;
    }

    private static int parseSpeakerId(String value) throws IOException {
        try {
            int speakerId = Integer.parseInt(value);
            if (speakerId < 0 || speakerId > 9) {
                throw new IOException("Supertonic speaker ID must be between 0 and 9");
            }
            return speakerId;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid Supertonic speaker ID: " + value, exception);
        }
    }

    private android.content.SharedPreferences getSynthesisPreferences() {
        return getSharedPreferences(SYNTHESIS_PREFERENCES, MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        synchronized (engineLock) {
            releaseLoadedEngine();
        }
        super.onDestroy();
    }

    private static final class VoiceSpec {
        final String type;
        final String languageTag;
        final String modelPath;
        final String tokensPath;
        final int speakerId;

        private VoiceSpec(
                String type,
                String languageTag,
                String modelPath,
                String tokensPath,
                int speakerId) {
            this.type = type;
            this.languageTag = languageTag;
            this.modelPath = modelPath;
            this.tokensPath = tokensPath;
            this.speakerId = speakerId;
        }

        static VoiceSpec kokoro(String languageTag, int speakerId) {
            return new VoiceSpec("kokoro", normalizeLanguageTag(languageTag),
                    null, null, speakerId);
        }

        static VoiceSpec supertonic(String languageTag, int speakerId) {
            return new VoiceSpec("supertonic", normalizeLanguageTag(languageTag),
                    null, null, speakerId);
        }

        static VoiceSpec vits(
                String type, String languageTag, String modelPath, String tokensPath, int speakerId) {
            return new VoiceSpec(type, normalizeLanguageTag(languageTag),
                    modelPath, tokensPath, speakerId);
        }

        String engineKey() {
            return type + '|' + languageTag + '|' + modelPath + '|' + tokensPath;
        }
    }
}
