package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class CapCutTtsProvider implements CloudTtsProvider {
    private static final String PRIMARY_DIRECT_URL = "https://tiktok-tts.weilnet.workers.dev/api/generation";
    private static final String BACKUP_DIRECT_URL = "https://gesserit.com/api/tiktok-tts";

    @Override public String getId() { return "capcut_tts"; }
    @Override public String getName() { return "CapCut / TikTok TTS"; }
    @Override public String getSubtitle() { return "Keyless Neural Voices • Direct / Wrapper"; }
    @Override public String getBadge() { return "CLOUD | ONLINE"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String wrapper = p.getString("capcut_wrapper_url", "").trim();
        if (!wrapper.isEmpty()) return synthesizeWrapper(wrapper, text, options);
        return synthesizeDirect(text, options);
    }

    private byte[] synthesizeDirect(String text, CloudVoiceOptions options) throws Exception {
        String speaker = (options == null || options.voice == null || options.voice.trim().isEmpty())
                ? "en_us_001" : options.voice.trim();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("voice", speaker);
        RequestBody reqBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));

        try {
            return executeDirectCall(client, PRIMARY_DIRECT_URL, reqBody);
        } catch (Exception primaryErr) {
            return executeDirectCall(client, BACKUP_DIRECT_URL, reqBody);
        }
    }

    private byte[] executeDirectCall(OkHttpClient client, String url, RequestBody reqBody) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                .post(reqBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("CapCut/TikTok HTTP " + response.code());
            }
            JSONObject json = new JSONObject(raw);
            String b64 = json.optString("data", json.optString("base64", ""));
            if (b64.isEmpty()) {
                throw new IOException(json.optString("error", "Empty audio from CapCut/TikTok TTS"));
            }
            return Base64.decode(b64, Base64.DEFAULT);
        }
    }

    private byte[] synthesizeWrapper(String baseUrl, String text, CloudVoiceOptions options) throws Exception {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String speaker = (options == null || options.voice == null || options.voice.trim().isEmpty())
                ? "en_us_001" : options.voice.trim();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();

        Request request;
        if (cleanBase.endsWith("/v2/synthesize")) {
            JSONObject body = new JSONObject().put("text", text).put("speaker", speaker).put("method", "buffer");
            request = new Request.Builder().url(cleanBase)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json"))).build();
        } else {
            String endpoint = cleanBase.endsWith("/v1/synthesize") ? cleanBase : (cleanBase + "/v1/synthesize");
            String url = endpoint + "?text=" + URLEncoder.encode(text, "UTF-8")
                    + "&speaker=" + URLEncoder.encode(speaker, "UTF-8")
                    + "&type=0&pitch=10&speed=10&volume=10&method=buffer";
            request = new Request.Builder().url(url).get().build();
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("CapCut server HTTP " + response.code());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
