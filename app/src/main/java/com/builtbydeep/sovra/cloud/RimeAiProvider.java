package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class RimeAiProvider implements CloudTtsProvider {
    @Override public String getId() { return "rime_ai"; }
    @Override public String getName() { return "Rime AI"; }
    @Override public String getSubtitle() { return "MistV2 Natural • Free Tier"; }
    @Override public String getBadge() { return "CLOUD | API"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("rime_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Rime AI API key is required");
        String speaker = (options != null && options.voice != null && !options.voice.trim().isEmpty())
                ? options.voice.trim() : "lagoon";

        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("speaker", speaker);
        body.put("modelId", "mistv2");
        body.put("audioFormat", "mp3");

        OkHttpClient client = new OkHttpClient.Builder().readTimeout(25, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
                .url("https://users.rime.ai/v1/rime-tts")
                .header("Authorization", "Bearer " + key)
                .header("Accept", "audio/mp3")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Rime AI HTTP " + response.code());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
