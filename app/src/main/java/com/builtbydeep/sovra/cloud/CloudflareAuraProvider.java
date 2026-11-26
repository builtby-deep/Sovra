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

public final class CloudflareAuraProvider implements CloudTtsProvider {
    public String getId() { return "cloudflare_aura"; }
    public String getName() { return "Cloudflare Aura"; }
    public String getSubtitle() { return "Deepgram Aura-2 • 10K Neurons/day"; }
    public String getBadge() { return "CLOUD | API"; }
    public boolean requiresApiKey() { return true; }
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String account = p.getString("cloudflare_account_id", "").trim();
        String token = p.getString("cloudflare_api_token", "").trim();
        if (account.isEmpty() || token.isEmpty()) throw new IllegalArgumentException("Cloudflare account ID and API token are required");
        String speaker = options == null || options.voice.trim().isEmpty() ? "luna" : options.voice.trim();
        JSONObject body = new JSONObject();
        body.put("text", text).put("speaker", speaker).put("encoding", "mp3");
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/" + account + "/ai/run/@cf/deepgram/aura-2-en")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Cloudflare HTTP " + response.code() + ": " + response.message());
            if (response.body() == null) return new byte[0];
            byte[] bytes = response.body().bytes();
            if (bytes.length > 0 && bytes[0] == '{') {
                JSONObject envelope = new JSONObject(new String(bytes));
                if (envelope.has("result")) {
                    JSONObject result = envelope.optJSONObject("result");
                    if (result != null) {
                        String audio = result.optString("audio");
                        if (!audio.isEmpty()) return android.util.Base64.decode(audio, android.util.Base64.DEFAULT);
                    }
                }
            }
            return bytes;
        }
    }
}
