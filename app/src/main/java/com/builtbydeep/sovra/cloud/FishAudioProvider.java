package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;

public final class FishAudioProvider implements CloudTtsProvider {
    public String getId() { return "fish_audio"; }
    public String getName() { return "Fish Audio"; }
    public String getSubtitle() { return "S2.1 Pro Free • Multilingual (83 Langs)"; }
    public String getBadge() { return "CLOUD | API"; }
    public boolean requiresApiKey() { return true; }
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("fish_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Fish Audio API key is missing");
        JSONObject body = new JSONObject();
        body.put("text", text).put("format", "mp3").put("mp3_bitrate", 128);
        if (options != null && options.voiceId != null && !options.voiceId.trim().isEmpty()) body.put("reference_id", options.voiceId.trim());
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.fish.audio/v1/tts")
                .header("Authorization", "Bearer " + key)
                .header("model", "s2.1-pro-free")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Fish Audio HTTP " + response.code() + ": " + response.message());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
