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

public final class CartesiaSonicProvider implements CloudTtsProvider {
    public String getId() { return "cartesia_sonic"; }
    public String getName() { return "Cartesia"; }
    public String getSubtitle() { return "Sonic 3.6 • 20K/mo Multilingual"; }
    public String getBadge() { return "CLOUD | API"; }
    public boolean requiresApiKey() { return true; }
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("cartesia_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Cartesia API key is missing");
        String voiceId = options == null || options.voiceId.trim().isEmpty()
                ? p.getString("cartesia_voice_id", "") : options.voiceId.trim();
        if (voiceId.isEmpty()) throw new IllegalArgumentException("Cartesia voice ID is missing");
        JSONObject body = new JSONObject();
        body.put("model_id", "sonic-3.6");
        body.put("transcript", text);
        body.put("voice", new JSONObject().put("mode", "id").put("id", voiceId));
        body.put("output_format", new JSONObject()
                .put("container", "mp3").put("bit_rate", 128000).put("sample_rate", 44100));
        if (options != null && options.speed >= 0.6f && options.speed <= 1.5f && options.speed != 1.0f) {
            body.put("speed", Math.max(0.6f, Math.min(1.5f, options.speed)));
        }
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.cartesia.ai/tts/bytes")
                .header("X-API-Key", key).header("Cartesia-Version", "2026-08-14")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Cartesia HTTP " + response.code() + ": " + response.message());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
