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

public final class GroqOrpheusProvider implements CloudTtsProvider {
    public String getId() { return "groq_orpheus"; }
    public String getName() { return "Groq Orpheus"; }
    public String getSubtitle() { return "Orpheus V1 • Expressive English (Daily Reset)"; }
    public String getBadge() { return "CLOUD | API"; }
    public boolean requiresApiKey() { return true; }
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("groq_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Groq API key is missing");
        String voice = options == null || options.voice.trim().isEmpty() ? "autumn" : options.voice.trim();
        JSONObject body = new JSONObject();
        body.put("model", "canopylabs/orpheus-v1-english").put("voice", voice)
                .put("input", text).put("response_format", "wav");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.groq.com/openai/v1/audio/speech")
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Groq HTTP " + response.code() + ": " + response.message());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
