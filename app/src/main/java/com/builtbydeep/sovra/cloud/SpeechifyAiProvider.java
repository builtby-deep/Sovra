package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class SpeechifyAiProvider implements CloudTtsProvider {
    @Override public String getId() { return "speechify_ai"; }
    @Override public String getName() { return "SpeechifyAI"; }
    @Override public String getSubtitle() { return "Simba Multilingual • 50K/mo Free"; }
    @Override public String getBadge() { return "CLOUD | API"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("speechify_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("SpeechifyAI API key is required");
        String voiceId = (options != null && options.voiceId != null && !options.voiceId.trim().isEmpty())
                ? options.voiceId.trim() : "henry";

        JSONObject body = new JSONObject();
        body.put("input", text);
        body.put("voice_id", voiceId);
        body.put("model", "simba-multilingual");
        body.put("audio_format", "mp3");

        OkHttpClient client = new OkHttpClient.Builder().readTimeout(25, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
                .url("https://api.sws.speechify.com/v1/audio/speech")
                .header("Authorization", "Bearer " + key)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("SpeechifyAI HTTP " + response.code());
            JSONObject json = new JSONObject(raw);
            String b64 = json.optString("audio_data", "");
            if (b64.isEmpty()) throw new IOException("Empty audio from SpeechifyAI");
            return Base64.decode(b64, Base64.DEFAULT);
        }
    }
}
