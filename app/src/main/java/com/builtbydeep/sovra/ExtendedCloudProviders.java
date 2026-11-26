package com.builtbydeep.sovra;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class ExtendedCloudProviders {
    public static final String CAPCUT = "capcut";
    public static final String FISH = "fish";
    public static final String CARTESIA = "cartesia";
    public static final String GRADIUM = "gradium";
    public static final String CLOUDFLARE = "cloudflare";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private ExtendedCloudProviders() {}

    public static boolean isExtended(String id) {
        return CAPCUT.equals(id) || FISH.equals(id)
                || CARTESIA.equals(id) || GRADIUM.equals(id) || CLOUDFLARE.equals(id);
    }

    public static boolean isKeyless(String id) {
        return CAPCUT.equals(id);
    }

    public static CloudTtsProvider create(String id) {
        if (CAPCUT.equals(id)) return new CapCutClient();
        if (FISH.equals(id)) return new FishClient();
        if (CARTESIA.equals(id)) return new CartesiaClient();
        if (GRADIUM.equals(id)) return new GradiumClient();
        if (CLOUDFLARE.equals(id)) return new CloudflareClient();
        return null;
    }

    // 1. CapCut / TikTok TTS (Keyless Direct Working Endpoint + Optional Wrapper URL)
    static final class CapCutClient implements CloudTtsProvider {
        private static final String PRIMARY_URL = "https://tiktok-tts.weilnet.workers.dev/api/generation";
        private static final String BACKUP_URL = "https://gesserit.com/api/tiktok-tts";

        @Override public String getId() { return CAPCUT; }
        @Override public int getSafeChunkLimit() { return 280; }

        @Override
        public List<Voice> fetchVoices(String apiKey) {
            return Arrays.asList(
                    new Voice("en_us_001", "Jessie (US Female)", "en-US", "", "English (US)"),
                    new Voice("en_us_002", "US Female 2 (Natural)", "en-US", "", "English (US)"),
                    new Voice("en_us_002", "US Female 2 (Natural)", "en-US", "", "English (US)"),
                    new Voice("en_us_006", "Joey (US Male)", "en-US", "", "English (US)"),
                    new Voice("en_us_007", "Professor (US Male)", "en-US", "", "English (US)"),
                    new Voice("en_us_009", "Scientist (US Male)", "en-US", "", "English (US)"),
                    new Voice("en_us_010", "Confidence (US Male)", "en-US", "", "English (US)"),
                    new Voice("en_uk_001", "Narrator (UK Male)", "en-GB", "", "English (UK)"),
                    new Voice("en_uk_001", "UK Female 1 (Narrator)", "en-GB", "", "English (UK)"),
                    new Voice("en_uk_001", "UK Female 1 (Narrator)", "en-GB", "", "English (UK)"),
                    new Voice("en_uk_003", "UK Male 2", "en-GB", "", "English (UK)"),
                    new Voice("en_au_001", "Metro (AU Female)", "en-AU", "", "English (AU)"),
                    new Voice("en_au_002", "Smooth (AU Male)", "en-AU", "", "English (AU)"),
                    new Voice("en_male_narration", "Storyteller Male", "en-US", "", "Creator & Drama"),
                    new Voice("en_female_emotional", "Peaceful Female", "en-US", "", "Creator & Drama"),
                    new Voice("en_male_cody", "Serious Cody", "en-US", "", "Creator & Drama"),
                    new Voice("en_female_samc", "Empathetic Sam", "en-US", "", "Creator & Drama"),
                    new Voice("en_male_ghosthost", "Ghost Host", "en-US", "", "Character"),
                    new Voice("en_male_ukbutler", "British Butler (Character)", "en-US", "", "Character"),
                    new Voice("en_male_ukbutler", "British Butler (Character)", "en-US", "", "Character"),
                    new Voice("en_female_madam_leota", "Madam Leota", "en-US", "", "Character"),
                    new Voice("jp_001", "Japanese Female 1", "ja-JP", "", "Japanese"),
                    new Voice("jp_006", "Japanese Male", "ja-JP", "", "Japanese"),
                    new Voice("kr_002", "Korean Male 1", "ko-KR", "", "Korean"),
                    new Voice("kr_003", "Korean Female", "ko-KR", "", "Korean"),
                    new Voice("es_002", "Spanish Male", "es-ES", "", "Spanish"),
                    new Voice("fr_001", "French Male 1", "fr-FR", "", "French"),
                    new Voice("de_001", "German Female", "de-DE", "", "German"),
                    new Voice("id_001", "Indonesian Female", "id-ID", "", "Indonesian")
            );
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            String speaker = (voice != null && voice.id != null && !voice.id.isEmpty()) ? voice.id : "en_us_001";
            String wrapper = apiKey == null ? "" : apiKey.trim();
            if (wrapper.startsWith("http://") || wrapper.startsWith("https://")) {
                return synthesizeWrapper(wrapper, speaker, text);
            }
            try {
                JSONObject payload = new JSONObject();
                payload.put("text", text);
                payload.put("voice", speaker);
                RequestBody body = RequestBody.create(payload.toString(), JSON);
                try {
                    return callDirect(PRIMARY_URL, body);
                } catch (IOException firstErr) {
                    return callDirect(BACKUP_URL, body);
                }
            } catch (org.json.JSONException e) {
                throw new IOException("CapCut request build failed.", e);
            }
        }

        private byte[] callDirect(String url, RequestBody body) throws IOException {
            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                    .post(body)
                    .build();
            try (Response res = HTTP.newCall(req).execute()) {
                String raw = res.body() == null ? "" : res.body().string();
                if (!res.isSuccessful()) throw new IOException("CapCut/TikTok HTTP " + res.code());
                JSONObject json = new JSONObject(raw);
                String b64 = json.optString("data", json.optString("base64", ""));
                if (b64.isEmpty()) throw new IOException(json.optString("error", "Empty CapCut audio"));
                return Base64.decode(b64, Base64.DEFAULT);
            } catch (org.json.JSONException e) {
                throw new IOException("Invalid CapCut response.", e);
            }
        }

        private byte[] synthesizeWrapper(String baseUrl, String speaker, String text) throws IOException {
            String clean = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            Request req;
            try {
                if (clean.endsWith("/v2/synthesize")) {
                    JSONObject body = new JSONObject().put("text", text).put("speaker", speaker).put("method", "buffer");
                    req = new Request.Builder().url(clean).post(RequestBody.create(body.toString(), JSON)).build();
                } else {
                    String ep = clean.endsWith("/v1/synthesize") ? clean : (clean + "/v1/synthesize");
                    String url = ep + "?text=" + URLEncoder.encode(text, "UTF-8")
                            + "&speaker=" + URLEncoder.encode(speaker, "UTF-8")
                            + "&type=0&pitch=10&speed=10&volume=10&method=buffer";
                    req = new Request.Builder().url(url).get().build();
                }
            } catch (org.json.JSONException e) {
                throw new IOException("CapCut wrapper request failed.", e);
            }
            try (Response res = HTTP.newCall(req).execute()) {
                if (!res.isSuccessful() || res.body() == null) throw new IOException("CapCut wrapper HTTP " + res.code());
                return res.body().bytes();
            }
        }
    }

    // 2. Fish Audio (OpenAudio S1 / Speech-1.6)
    static final class FishClient implements CloudTtsProvider {
        @Override public String getId() { return FISH; }
        @Override public int getSafeChunkLimit() { return 1200; }

        @Override
        public List<Voice> fetchVoices(String apiKey) {
            return Arrays.asList(
                    new Voice("7f92f8afb8ec43bf81429cc1c9199cb1", "Energetic Male", "en-US", "", "English & Multilingual"),
                    new Voice("b545c585f631496c914815291da4e893", "Storyteller Calm", "en-US", "", "English & Multilingual"),
                    new Voice("933563129e564b19a115bedd57b7406a", "Sarah (Warm Female)", "en-US", "", "English & Multilingual"),
                    new Voice("8ec9973300874562962bbcb300f82213", "Guyzo", "en-US", "", "English & Multilingual"),
                    new Voice("c5f56a6cc2ec4fa8920cb4c5889a3fb7", "Slax", "en-US", "", "English & Multilingual"),
                    new Voice("bf322df2096a46f18c579d0baa36f41d", "Adrian", "en-US", "", "English & Multilingual"),
                    new Voice("a2346273eb314b6cb82ba83dbc9d9fee", "Markiplier.", "en-US", "", "English & Multilingual"),
                    new Voice("4c6a6762e4ac4bdebdb4fa8525d054a2", "ATOMIC", "en-US", "", "English & Multilingual"),
                    new Voice("536d3a5e000945adb7038665781a4aca", "Ethan", "en-US", "", "English & Multilingual"),
                    new Voice("03397b4c4be74759b72533b663fbd001", "Elon Musk(Noise reduction)", "en-US", "", "English & Multilingual"),
                    new Voice("0de8162a9e384545a0106046b57488a7", "Narration (Hindi)", "hi-IN", "", "Hindi & Indian"),
                    new Voice("6366ea69a97d41ada9eb855a64e56fba", "Bunty (Hindi)", "hi-IN", "", "Hindi & Indian"),
                    new Voice("b79b6174191548d08af0fb6bf0396127", "Hindi Gojo Voice", "hi-IN", "", "Hindi & Indian"),
                    new Voice("4d7609058bd34213b1378b29efbde1f1", "Girl hindi", "hi-IN", "", "Hindi & Indian"),
                    new Voice("cf5dd04d673d4219b988f4d84e7f8bee", "Amitabh Bachan (Hindi)", "hi-IN", "", "Hindi & Indian"),
                    new Voice("a8c38987c1f94781b5a98342c9ba7860", "Filipino (Hindi)", "hi-IN", "", "Hindi & Indian")
            );
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("format", "mp3");
                body.put("mp3_bitrate", 128);
                if (voice != null && voice.id != null && !voice.id.isEmpty()) {
                    body.put("reference_id", voice.id);
                }
                String model = (modelId == null || modelId.isEmpty())
                        ? CloudTtsClients.FISH_FREE : modelId;
                Request req = new Request.Builder()
                        .url("https://api.fish.audio/v1/tts")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("model", model)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) throw new IOException("Fish Audio HTTP " + res.code());
                    return res.body().bytes();
                }
            } catch (org.json.JSONException e) {
                throw new IOException("Fish Audio request failed.", e);
            }
        }
    }


        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            try {
                String speaker = (voice != null && voice.id != null && !voice.id.isEmpty()) ? voice.id : "tara";
                JSONObject body = new JSONObject();
                body.put("model", "canopylabs/orpheus-v1-english");
                body.put("input", text);
                body.put("voice", speaker);
                body.put("response_format", "wav");
                Request req = new Request.Builder()
                        .header("Authorization", "Bearer " + apiKey)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    return res.body().bytes();
                }
            } catch (org.json.JSONException e) {
            }
        }

    // 4. Cartesia Sonic-3 (Ultra-Low Latency Multilingual + Hindi)
    static final class CartesiaClient implements CloudTtsProvider {
        @Override public String getId() { return CARTESIA; }
        @Override public int getSafeChunkLimit() { return 1500; }

        @Override
        public List<Voice> fetchVoices(String apiKey) {
            return Arrays.asList(
                    new Voice("694f9389-aac1-45b6-b726-9d9369183238", "Barbershop Man", "en-US", "", "English (US)"),
                    new Voice("a0e99841-438c-4a64-b679-ae501e7d6091", "Classy British Man", "en-GB", "", "English (UK)"),
                    new Voice("79a125e8-cd45-4c13-8a67-188112f4dd22", "British Lady", "en-GB", "", "English (UK)"),
                    new Voice("f786b574-daa5-4673-aa0c-cbe3e8534c02", "Katie (Friendly Female)", "en-US", "", "English (US)"),
                    new Voice("3b554273-4299-48b9-9aaf-eefd438e3941", "Indian Lady (Hindi/En)", "hi-IN", "", "Hindi & Indian"),
                    new Voice("638efaaa-4d0c-442e-b701-3fae16aad012", "Indian Man (Hindi/En)", "hi-IN", "", "Hindi & Indian"),
                    new Voice("2b568345-1d48-4047-b25f-7baccf842eb0", "Japanese Woman", "ja-JP", "", "Japanese")
            );
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            try {
                String voiceId = (voice != null && voice.id != null && !voice.id.isEmpty())
                        ? voice.id : "694f9389-aac1-45b6-b726-9d9369183238";
                JSONObject voiceObj = new JSONObject().put("mode", "id").put("id", voiceId);
                JSONObject formatObj = new JSONObject()
                        .put("container", "mp3")
                        .put("bit_rate", 128000)
                        .put("sample_rate", 44100);
                JSONObject body = new JSONObject();
                body.put("model_id", "sonic-3");
                body.put("transcript", text);
                body.put("voice", voiceObj);
                body.put("output_format", formatObj);
                if (language != null && !language.isEmpty()) body.put("language", language);

                Request req = new Request.Builder()
                        .url("https://api.cartesia.ai/tts/bytes")
                        .header("X-API-Key", apiKey)
                        .header("Cartesia-Version", "2025-04-16")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) throw new IOException("Cartesia HTTP " + res.code());
                    return res.body().bytes();
                }
            } catch (org.json.JSONException e) {
                throw new IOException("Cartesia request failed.", e);
            }
        }
    }

    // 5. Gradium AI
    static final class GradiumClient implements CloudTtsProvider {
        @Override public String getId() { return GRADIUM; }
        @Override public int getSafeChunkLimit() { return 1200; }

        @Override
        public List<Voice> fetchVoices(String apiKey) {
            return Arrays.asList(
                    new Voice("YTpq7expH9539ERJ", "Emma (US Female)", "en-US", "", "English (US)"),
                    new Voice("LFZvm12tW_z0xfGo", "Kent (US Male)", "en-US", "", "English (US)"),
                    new Voice("ubuXfxhu-pqZrAbv", "Sydney (US Female)", "en-US", "", "English (US)"),
                    new Voice("KWJiXkNZZx4MGg2z", "John (US Male)", "en-US", "", "English (US)"),
                    new Voice("axlOaUiWoHHG5gc2", "Elise (French Female)", "fr-FR", "", "French"),
                    new Voice("-uP9Byt3OrgoLPN6", "Mia (German Female)", "de-DE", "", "German")
            );
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            try {
                String voiceId = (voice != null && voice.id != null && !voice.id.isEmpty())
                        ? voice.id : "YTpq7expH9539ERJ";
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("voice_id", voiceId);
                body.put("output_format", "wav");
                body.put("only_audio", true);
                Request req = new Request.Builder()
                        .url("https://eu.api.gradium.ai/api/post/speech/tts")
                        .header("x-api-key", apiKey)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) throw new IOException("Gradium HTTP " + res.code());
                    return normalizeGradiumAudio(res.body().bytes());
                }
            } catch (org.json.JSONException e) {
                throw new IOException("Gradium request failed.", e);
            }
        }

        private static byte[] normalizeGradiumAudio(byte[] raw) throws IOException {
            if (raw == null || raw.length == 0) throw new IOException("Gradium returned empty audio.");
            if (raw[0] == '{') {
                try {
                    JSONObject obj = new JSONObject(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
                    String b64 = obj.optString("audio", obj.optString("data", ""));
                    if (b64.isEmpty()) throw new IOException("Gradium error: " + obj.optString("detail", obj.toString()));
                    raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                } catch (org.json.JSONException e) {
                    throw new IOException("Invalid Gradium JSON response.", e);
                }
            }
            if (raw.length > 44 && raw[0] == 'R' && raw[1] == 'I' && raw[2] == 'F' && raw[3] == 'F') {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(raw).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                bb.putInt(4, raw.length - 8);
                for (int i = 12; i < raw.length - 8; i++) {
                    if (raw[i] == 'd' && raw[i + 1] == 'a' && raw[i + 2] == 't' && raw[i + 3] == 'a') {
                        bb.putInt(i + 4, raw.length - (i + 8));
                        break;
                    }
                }
                return raw;
            }
            int sampleRate = 48000, channels = 1, bits = 16;
            int byteRate = sampleRate * channels * bits / 8;
            java.nio.ByteBuffer wav = java.nio.ByteBuffer.allocate(44 + raw.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            wav.put(new byte[]{'R','I','F','F'}).putInt(36 + raw.length).put(new byte[]{'W','A','V','E'});
            wav.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short) 1).putShort((short) channels);
            wav.putInt(sampleRate).putInt(byteRate).putShort((short) (channels * bits / 8)).putShort((short) bits);
            wav.put(new byte[]{'d','a','t','a'}).putInt(raw.length).put(raw);
            return wav.array();
        }
    }

    // 6. Cloudflare Workers AI (Deepgram Aura-2) - Enter key as "ACCOUNT_ID:API_TOKEN"
    static final class CloudflareClient implements CloudTtsProvider {
        @Override public String getId() { return CLOUDFLARE; }
        @Override public int getSafeChunkLimit() { return 1500; }

        @Override
        public List<Voice> fetchVoices(String apiKey) {
            return Arrays.asList(
                    new Voice("luna", "Luna (US Female)", "en-US", "", "English (US)"),
                    new Voice("asteria", "Asteria (US Female)", "en-US", "", "English (US)"),
                    new Voice("stella", "Stella (US Female)", "en-US", "", "English (US)"),
                    new Voice("athena", "Athena (UK Female)", "en-GB", "", "English (UK)"),
                    new Voice("hera", "Hera (US Female)", "en-US", "", "English (US)"),
                    new Voice("orion", "Orion (US Male)", "en-US", "", "English (US)"),
                    new Voice("arcas", "Arcas (US Male)", "en-US", "", "English (US)"),
                    new Voice("perseus", "Perseus (US Male)", "en-US", "", "English (US)"),
                    new Voice("angus", "Angus (Irish Male)", "en-IE", "", "English (IE)"),
                    new Voice("orpheus", "Orpheus (US Male)", "en-US", "", "English (US)"),
                    new Voice("helios", "Helios (UK Male)", "en-GB", "", "English (UK)"),
                    new Voice("zeus", "Zeus (US Male)", "en-US", "", "English (US)")
            );
        }

        @Override
        public byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                                 String language, float speed, float pitch, boolean smartPunctuation)
                throws IOException {
            String raw = apiKey == null ? "" : apiKey.trim();
            int sep = raw.indexOf(':');
            if (sep <= 0 || sep >= raw.length() - 1) {
                throw new IOException("Enter Cloudflare key as ACCOUNT_ID:API_TOKEN");
            }
            String accountId = raw.substring(0, sep).trim();
            String token = raw.substring(sep + 1).trim();
            String speaker = (voice != null && voice.id != null && !voice.id.isEmpty()) ? voice.id : "luna";
            try {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("speaker", speaker);
                String url = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/@cf/deepgram/aura-1";
                Request req = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();
                try (Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) throw new IOException("Cloudflare HTTP " + res.code());
                    return res.body().bytes();
                }
            } catch (org.json.JSONException e) {
                throw new IOException("Cloudflare request failed.", e);
            }
        }
    }
}
