package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class WitAiProvider implements CloudTtsProvider {
    @Override public String getId() { return "wit_ai"; }
    @Override public String getName() { return "Wit.ai"; }
    @Override public String getSubtitle() { return "Meta Neural TTS • Unlimited Free"; }
    @Override public String getBadge() { return "CLOUD | API"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("wit_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("Wit.ai Server Token is required");
        String voice = (options != null && options.voice != null && !options.voice.trim().isEmpty())
                ? options.voice.trim() : "Rebecca";

        JSONObject body = new JSONObject();
        body.put("q", text);
        body.put("voice", voice);

        OkHttpClient client = new OkHttpClient.Builder().readTimeout(25, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
                .url("https://api.wit.ai/synthesize?v=20240304")
                .header("Authorization", "Bearer " + key)
                .header("Accept", "audio/mpeg")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Wit.ai HTTP " + response.code());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
