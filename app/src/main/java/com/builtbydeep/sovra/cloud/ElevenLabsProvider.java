package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public final class ElevenLabsProvider implements CloudTtsProvider {
    @Override public String getId() { return "elevenlabs"; }
    @Override public String getName() { return "ElevenLabs"; }
    @Override public String getSubtitle() { return "Multilingual V2 • 10K/mo Free"; }
    @Override public String getBadge() { return "CLOUD | API"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String key = p.getString("elevenlabs_api_key", "").trim();
        if (key.isEmpty()) throw new IllegalArgumentException("ElevenLabs API key is required");
        String voiceId = (options != null && options.voiceId != null && !options.voiceId.trim().isEmpty())
                ? options.voiceId.trim() : "21m00Tcm4TlvDq8ikWAM";

        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("model_id", "eleven_multilingual_v2");

        OkHttpClient client = new OkHttpClient.Builder().readTimeout(25, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId)
                .header("xi-api-key", key)
                .header("Accept", "audio/mpeg")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("ElevenLabs HTTP " + response.code());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }
}
