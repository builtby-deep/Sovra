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

public final class GradiumAiProvider implements CloudTtsProvider {
    public String getId() { return "gradium_ai"; }
    public String getName() { return "Gradium AI"; }
    public String getSubtitle() { return "Ultra-Low Latency • 45K/mo"; }
    public String getBadge() { return "CLOUD | API"; }
    public boolean requiresApiKey() { return true; }
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("gradium_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Gradium AI API key is missing");
        String voiceId = options == null || options.voiceId.trim().isEmpty()
                ? p.getString("gradium_voice_id", "YTpq7expH9539ERJ") : options.voiceId.trim();
        JSONObject body = new JSONObject();
        body.put("text", text).put("voice_id", voiceId).put("output_format", "wav");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.gradium.ai/api/post/speech/tts")
                .header("x-api-key", key)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Gradium HTTP " + response.code() + ": " + response.message());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
