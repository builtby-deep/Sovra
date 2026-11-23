package com.builtbydeep.sovra;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class CloudTtsClients {
    public static final String RIME = "rime";
    public static final String SPEECHIFY = "speechify";
    public static final String ELEVENLABS = "elevenlabs";
    public static final String WITAI = WitAiTtsProvider.ID;
    public static final String RIME_CODA = "coda";
    public static final String RIME_MIST = "mistv3";
    public static final String SPEECHIFY_35 = "simba-3.5";
    public static final String SPEECHIFY_32 = "simba-3.2";
    public static final String ELEVEN_FLASH = "eleven_flash_v2_5";
    public static final String ELEVEN_V3 = "eleven_v3";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final RimeClient RIME_CLIENT = new RimeClient();
    private static final SpeechifyClient SPEECHIFY_CLIENT = new SpeechifyClient();
    private static final ElevenLabsClient ELEVENLABS_CLIENT = new ElevenLabsClient();
    private static final WitAiTtsProvider WITAI_CLIENT = new WitAiTtsProvider();

    private CloudTtsClients() {}

    public static CloudTtsProvider get(String providerId) {
        if (RIME.equals(providerId)) return RIME_CLIENT;
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_CLIENT;
        if (ELEVENLABS.equals(providerId)) return ELEVENLABS_CLIENT;
        if (WITAI.equals(providerId)) return WITAI_CLIENT;
        throw new IllegalArgumentException("Unknown cloud TTS provider: " + providerId);
    }

    public static WitAiTtsProvider getWitAiProvider() {
        return WITAI_CLIENT;
    }

    public static String getApiKeyPreference(String providerId) {
        return "cloud_api_key_" + providerId;
    }

    public static String getSelectedModelPreference(String providerId) {
        return "cloud_model_" + providerId;
    }

    public static String defaultModel(String providerId) {
        if (RIME.equals(providerId)) return RIME_MIST;
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_32;
        if (ELEVENLABS.equals(providerId)) return ELEVEN_FLASH;
        if (WITAI.equals(providerId)) return "";
        throw new IllegalArgumentException("Unknown cloud TTS provider: " + providerId);
    }

    public static String modelLabel(String providerId, String modelId) {
        if (RIME.equals(providerId)) return RIME_MIST.equals(modelId) ? "Mist v3" : "Coda";
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_32.equals(modelId) ? "Simba 3.2" : "Simba 3.5";
        if (ELEVENLABS.equals(providerId)) return ELEVEN_V3.equals(modelId) ? "Eleven v3" : "Flash v2.5";
        if (WITAI.equals(providerId)) return "Wit.ai";
        return modelId;
    }

    public static String quotaLabel(String providerId, String modelId) {
        if (RIME.equals(providerId)) return RIME_MIST.equals(modelId) ? "$50/mo" : "1M/mo";
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_32.equals(modelId) ? "500K/mo" : "333K/mo";
        if (ELEVENLABS.equals(providerId)) return ELEVEN_V3.equals(modelId) ? "10K/mo" : "20K/mo";
        if (WITAI.equals(providerId)) return "Unlimited/mo";
        return "";
    }

    public static String languageLabel(String providerId, String modelId) {
        if (RIME.equals(providerId)) return RIME_MIST.equals(modelId) ? "English, French, German, Spanish" : "10 languages • English, Hindi";
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_32.equals(modelId) ? "English only" : "48 languages • English, Hindi";
        if (WITAI.equals(providerId)) return "8 curated English voices";
        return ELEVEN_V3.equals(modelId) ? "70+ languages" : "32 languages • English, Hindi";
    }

    public static List<CloudTtsProvider.Voice> filterVoicesForModel(
            String providerId, String modelId, List<CloudTtsProvider.Voice> voices) {
        ArrayList<CloudTtsProvider.Voice> matches = new ArrayList<>();
        for (CloudTtsProvider.Voice voice : voices) {
            if (RIME.equals(providerId) && !modelId.equals(voice.modelId)) continue;
            if (SPEECHIFY.equals(providerId) && SPEECHIFY_32.equals(modelId)
                    && !voice.locale.toLowerCase(Locale.ROOT).startsWith("en")) continue;
            matches.add(voice);
        }
        return Collections.unmodifiableList(matches);
    }

    public static int monthlyCharacterLimit(String providerId, String modelId) {
        if (RIME.equals(providerId)) return RIME_MIST.equals(modelId) ? 1660000 : 1000000;
        if (SPEECHIFY.equals(providerId)) return SPEECHIFY_32.equals(modelId) ? 500000 : 333000;
        if (ELEVENLABS.equals(providerId)) return ELEVEN_V3.equals(modelId) ? 10000 : 20000;
        return 0;
    }

    static List<String> splitText(String text, int limit) {
        ArrayList<String> chunks = new ArrayList<>();
        if (text == null) return chunks;
        String remaining = text.trim();
        while (!remaining.isEmpty()) {
            int end = Math.min(limit, remaining.length());
            if (end < remaining.length()) {
                int boundary = -1;
                for (int i = end; i > Math.max(0, end - 250); i--) {
                    if (Character.isWhitespace(remaining.charAt(i - 1))
                            || ".!?。！？\n".indexOf(remaining.charAt(i - 1)) >= 0) {
                        boundary = i;
                        if (".!?。！？\n".indexOf(remaining.charAt(i - 1)) >= 0) break;
                    }
                }
                if (boundary > 0) end = boundary;
            }
            if (end < remaining.length() && Character.isHighSurrogate(remaining.charAt(end - 1))) end--;
            String chunk = remaining.substring(0, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            remaining = remaining.substring(end).trim();
        }
        return chunks;
    }

    private abstract static class BaseClient implements CloudTtsProvider {
        volatile List<Voice> cachedVoices = Collections.emptyList();
        volatile String cachedVoicesKey = "";

        Request.Builder request(String url, String apiKey) {
            return new Request.Builder().url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "audio/mpeg")
                    .header("Content-Type", "application/json");
        }

        byte[] postAudio(Request request) throws IOException {
            try (Response response = HTTP.newCall(request).execute()) {
                if (!response.isSuccessful()) throw httpError(response);
                if (response.body() == null) throw new IOException("Provider returned an empty audio response.");
                byte[] audio = response.body().bytes();
                if (audio.length == 0) throw new IOException("Provider returned an empty audio response.");
                return audio;
            }
        }

        IOException httpError(Response response) throws IOException {
            String body = response.body() == null ? "" : response.body().string();
            String detail = body.trim();
            if (detail.length() > 500) detail = detail.substring(0, 500);
            String reason;
            switch (response.code()) {
                case 400: reason = "Bad request"; break;
                case 401: reason = "Invalid API key"; break;
                case 402:
                case 429: reason = "Quota exhausted or rate limited"; break;
                default: reason = "Cloud TTS request failed"; break;
            }
            return new IOException(reason + " (HTTP " + response.code() + ")"
                    + (detail.isEmpty() ? "." : ": " + detail));
        }

        RequestBody jsonBody(JSONObject json) {
            return RequestBody.create(json.toString(), JSON);
        }

        List<Voice> immutable(List<Voice> voices) {
            return Collections.unmodifiableList(new ArrayList<>(voices));
        }

        static String localeLanguage(String locale) {
            Locale parsed = Locale.forLanguageTag(locale == null ? "" : locale);
            String language = parsed.getLanguage();
            return language.isEmpty() ? "en" : language;
        }
    }

    private static final class RimeClient extends BaseClient {
        private static final String VOICE_DETAILS = "https://users.rime.ai/data/voices/voice_details.json";
        private static final String VOICE_FALLBACK = "https://users.rime.ai/data/voices/all-v2.json";

        @Override public String getId() { return RIME; }
        @Override public int getSafeChunkLimit() { return 900; }

        @Override
        public List<Voice> fetchVoices(String apiKey) throws IOException {
            if (!cachedVoices.isEmpty()) return cachedVoices;
            IOException last = null;
            for (String url : new String[]{VOICE_DETAILS, VOICE_FALLBACK}) {
                Request request = new Request.Builder().url(url).header("Accept", "application/json").build();
                try (Response response = HTTP.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        last = new IOException("Rime voices request failed (HTTP " + response.code() + ").");
                        continue;
                    }
                    List<Voice> parsed = parseRimeVoices(response.body().string());
                    if (!parsed.isEmpty()) {
                        cachedVoices = immutable(parsed);
                        return cachedVoices;
                    }
                } catch (org.json.JSONException | RuntimeException error) {
                    last = new IOException("Rime voice catalog could not be parsed.", error);
                }
            }
            if (last == null) last = new IOException("Rime voice catalog contains no voices.");
            ArrayList<Voice> fallback = new ArrayList<>();
            for (String id : new String[]{"astra", "luna", "celeste", "masonry", "albion", "lyra"}) {
                fallback.add(new Voice(id, id, "en", RIME_CODA, "English"));
            }
            cachedVoices = immutable(fallback);
            return cachedVoices;
        }

        private List<Voice> parseRimeVoices(String json) throws org.json.JSONException {
            java.util.LinkedHashMap<String, Voice> voices = new java.util.LinkedHashMap<>();
            JSONArray array = jsonArray(json);
            for (int i = 0; array != null && i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = firstString(item, "id", "voice_id", "speaker", "name");
                if (id.isEmpty()) continue;
                String model = firstString(item, "modelId", "model_id", "model");
                model = model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (model.contains("mistv3")) model = RIME_MIST;
                else if (model.equals("coda")) model = RIME_CODA;
                else if (model.isEmpty() && item.optBoolean("mistv3", false)) model = RIME_MIST;
                else if (model.isEmpty() && item.optBoolean("coda", false)) model = RIME_CODA;
                if (!RIME_CODA.equals(model) && !RIME_MIST.equals(model)) continue;
                String language = normalizeRimeLanguage(firstString(item, "lang", "language_code",
                        "locale", "language"));
                String region = firstString(item, "country", "region");
                if (region.isEmpty()) region = Locale.forLanguageTag(language)
                        .getDisplayLanguage(Locale.ENGLISH);
                Voice voice = new Voice(id, firstString(item, "display_name", "name", "speaker", "id"),
                        language, model, region);
                voices.putIfAbsent(id + "|" + language + "|" + model, voice);
            }
            return new ArrayList<>(voices.values());
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            JSONObject body = new JSONObject();
            try {
                body.put("speaker", voice.id);
                body.put("text", text);
                body.put("modelId", modelId);
                body.put("lang", language);
                body.put("speedAlpha", clamp(speed, 0.5f, 2.0f));
                if (RIME_MIST.equals(modelId) && smartPunctuation) {
                    body.put("pauseBetweenBrackets", true);
                }
            } catch (org.json.JSONException error) {
                throw new IOException("Unable to prepare the Rime request.", error);
            }
            String endpoint = RIME_MIST.equals(modelId) ? "https://mist.api.rime.ai/" : "https://coda.api.rime.ai/";
            Request request = request(endpoint, apiKey).post(jsonBody(body)).build();
            return postAudio(request);
        }
    }

    private static final class SpeechifyClient extends BaseClient {
        @Override public String getId() { return SPEECHIFY; }
        @Override public int getSafeChunkLimit() { return 2000; }

        @Override
        public List<Voice> fetchVoices(String apiKey) throws IOException {
            if (!cachedVoices.isEmpty() && apiKey.equals(cachedVoicesKey)) return cachedVoices;
            Request request = new Request.Builder().url("https://api.speechify.ai/v1/voices")
                    .header("Authorization", "Bearer " + apiKey).header("Accept", "application/json").build();
            try (Response response = HTTP.newCall(request).execute()) {
                if (!response.isSuccessful()) throw httpError(response);
                if (response.body() == null) throw new IOException("Speechify voice catalog is empty.");
                JSONArray voicesJson = jsonArray(response.body().string());
                ArrayList<Voice> voices = new ArrayList<>();
                for (int i = 0; voicesJson != null && i < voicesJson.length(); i++) {
                    JSONObject item = voicesJson.optJSONObject(i);
                    if (item == null) continue;
                    String id = firstString(item, "id", "voice_id", "voiceId");
                    if (id.isEmpty()) continue;
                    String locale = firstString(item, "locale", "language", "language_code");
                    if (locale.isEmpty()) locale = "en-US";
                    String name = firstString(item, "name", "display_name", "id");
                    voices.add(new Voice(id, name, locale, "", Locale.forLanguageTag(locale).getDisplayLanguage(Locale.ENGLISH)));
                }
                cachedVoices = immutable(voices);
                cachedVoicesKey = apiKey;
                return cachedVoices;
            } catch (org.json.JSONException error) {
                throw new IOException("Speechify voice catalog could not be parsed.", error);
            }
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            String input = text;
            if (speed != 1.0f || pitch != 1.0f || smartPunctuation) {
                String content = escapeXml(text);
                if (smartPunctuation) content = injectSpeechifyPauses(content);
                input = "<speak><prosody rate=\"" + Math.round(clamp(speed, 0.25f, 2.0f) * 100)
                        + "%\" pitch=\"" + Math.round(clamp(pitch, 0.25f, 2.0f) * 100)
                        + "%\">" + content + "</prosody></speak>";
            }
            JSONObject body = new JSONObject();
            try {
                body.put("input", input);
                body.put("voice_id", voice.id);
                body.put("model", modelId);
                body.put("language", language);
            } catch (org.json.JSONException error) {
                throw new IOException("Unable to prepare the Speechify request.", error);
            }
            return postAudio(request("https://api.speechify.ai/v1/audio/stream", apiKey)
                    .post(jsonBody(body)).build());
        }
    }

    private static final class ElevenLabsClient extends BaseClient {
        @Override public String getId() { return ELEVENLABS; }
        @Override public int getSafeChunkLimit() { return 1500; }

        @Override
        public List<Voice> fetchVoices(String apiKey) throws IOException {
            if (!cachedVoices.isEmpty() && apiKey.equals(cachedVoicesKey)) return cachedVoices;
            Request request = new Request.Builder().url("https://api.elevenlabs.io/v1/voices")
                    .header("xi-api-key", apiKey).header("Accept", "application/json").build();
            try (Response response = HTTP.newCall(request).execute()) {
                if (!response.isSuccessful()) throw httpError(response);
                if (response.body() == null) throw new IOException("ElevenLabs voice catalog is empty.");
                JSONObject root = new JSONObject(response.body().string());
                JSONArray voicesJson = root.optJSONArray("voices");
                ArrayList<Voice> voices = new ArrayList<>();
                for (int i = 0; voicesJson != null && i < voicesJson.length(); i++) {
                    JSONObject item = voicesJson.optJSONObject(i);
                    if (item == null) continue;
                    String id = firstString(item, "voice_id", "id");
                    if (id.isEmpty()) continue;
                    String name = firstString(item, "name", "voice_id");
                    JSONObject labels = item.optJSONObject("labels");
                    String region = labels == null ? "All regions" : firstString(labels, "accent", "region", "language");
                    String locale = labels == null ? "en" : firstString(labels, "language", "locale");
                    if (locale.isEmpty()) locale = "en";
                    voices.add(new Voice(id, name, locale, "", region));
                }
                if (voices.isEmpty()) voices.add(new Voice("21m00Tcm4TlvDq8ikWAM", "Rachel", "en", "", "American"));
                cachedVoices = immutable(voices);
                cachedVoicesKey = apiKey;
                return cachedVoices;
            } catch (org.json.JSONException error) {
                throw new IOException("ElevenLabs voice catalog could not be parsed.", error);
            }
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            JSONObject settings = new JSONObject();
            JSONObject body = new JSONObject();
            try {
                settings.put("stability", 0.5);
                settings.put("similarity_boost", 0.75);
                if (!ELEVEN_V3.equals(modelId)) settings.put("speed", clamp(speed, 0.7f, 1.2f));
                body.put("text", smartPunctuation ? injectElevenPauses(text) : text);
                body.put("model_id", modelId);
                body.put("voice_settings", settings);
            } catch (org.json.JSONException error) {
                throw new IOException("Unable to prepare the ElevenLabs request.", error);
            }
            String url = "https://api.elevenlabs.io/v1/text-to-speech/"
                    + android.net.Uri.encode(voice.id) + "?output_format=mp3_44100_128";
            Request request = new Request.Builder().url(url).header("xi-api-key", apiKey)
                    .header("Accept", "audio/mpeg").header("Content-Type", "application/json")
                    .post(jsonBody(body)).build();
            return postAudio(request);
        }
    }

    private static JSONArray jsonArray(String json) throws org.json.JSONException {
        Object value = new org.json.JSONTokener(json).nextValue();
        if (value instanceof JSONArray) return (JSONArray) value;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            for (String key : new String[]{"voices", "data", "voice_details", "items"}) {
                JSONArray array = object.optJSONArray(key);
                if (array != null) return array;
            }
            return new JSONArray();
        }
        return new JSONArray();
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof String && !((String) value).trim().isEmpty()) return ((String) value).trim();
        }
        return "";
    }

    private static String normalizeRimeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return "en";
        String normalized = language.trim();
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                if (normalized.equalsIgnoreCase(locale.getISO3Language())) return locale.getLanguage();
            } catch (java.util.MissingResourceException ignored) {
                // Some platform locales do not have an ISO-639-2 code.
            }
        }
        for (Locale locale : Locale.getAvailableLocales()) {
            if (normalized.equalsIgnoreCase(locale.getDisplayLanguage(Locale.ENGLISH))) {
                return locale.getLanguage();
            }
        }
        return normalized.length() > 2
                ? Locale.forLanguageTag(normalized).getLanguage() : normalized.toLowerCase(Locale.ROOT);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String injectSpeechifyPauses(String value) {
        return value.replaceAll("([.!?])\\s+", "$1<break time=\"200ms\"/>");
    }

    private static String injectElevenPauses(String value) {
        return value.replaceAll("([.!?])\\s+", "$1<break time=\"200ms\"/>");
    }
}
