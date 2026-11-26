package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import okhttp3.MediaType;
import okhttp3.RequestBody;

public final class CapCutTtsProvider implements CloudTtsProvider {
    private volatile String cachedToken = "";
    private volatile long tokenExpiresAt;

    public String getId() { return "capcut_tts"; }
    public String getName() { return "CapCut TTS"; }
    public String getSubtitle() { return "Keyless Neural Voices • Auto-Token"; }
    public String getBadge() { return "CLOUD | ONLINE"; }
    public boolean requiresApiKey() { return false; }

    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String wrapper = p.getString("capcut_wrapper_url", "").trim();
        if (!wrapper.isEmpty()) return synthesizeWrapper(wrapper, text, options);
        return synthesizeWebSocket(context, text, options);
    }

    private byte[] synthesizeWrapper(String baseUrl, String text, CloudVoiceOptions options) throws Exception {
        String speaker = options == null || options.voice.trim().isEmpty() ? "en_us_001" : options.voice.trim();
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?")
                + "text=" + java.net.URLEncoder.encode(text, "UTF-8")
                + "&type=0&pitch=10&speed=10&volume=10&method=buffer";
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) throw new java.io.IOException("CapCut wrapper HTTP " + response.code());
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }

    private byte[] synthesizeWebSocket(Context context, String text, CloudVoiceOptions options) throws Exception {
        String speaker = options == null || options.voice.trim().isEmpty() ? "en_us_001" : options.voice.trim();
        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        JSONObject synth = new JSONObject();
        synth.put("text", text).put("speaker", speaker)
                .put("pitch", 10).put("speed", 10).put("volume", 10)
                .put("audio_config", new JSONObject().put("format", "wav"));
        if (System.currentTimeMillis() >= tokenExpiresAt) {
            fetchAnonymousToken(client);
        }
        Request request = new Request.Builder()
                .url("wss://sami-sg.byteoversea.com/internal/api/v1/ws")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
                .header("Origin", "https://www.capcut.com")
                .header("Authorization", "Bearer " + cachedToken)
                .build();
        java.io.ByteArrayOutputStream audio = new java.io.ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        WebSocket[] sockets = new WebSocket[1];
        WebSocketListener listener = new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(synth.toString());
            }
            @Override public void onMessage(WebSocket webSocket, String message) {
                try {
                    JSONObject event = new JSONObject(message);
                    String type = event.optString("type");
                    if (type.contains("TaskCompleted") || type.contains("completed")) {
                        done.countDown();
                        webSocket.close(1000, "complete");
                    } else if (type.contains("Error") || event.optInt("status_code", 200) >= 400) {
                        failure[0] = new java.io.IOException("CapCut synthesis failed: " + message);
                        done.countDown();
                        webSocket.close(1011, "error");
                    }
                } catch (Exception e) {
                    failure[0] = e;
                    done.countDown();
                }
            }
            @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
                try { audio.write(bytes.toByteArray()); } catch (Exception e) {
                    failure[0] = e;
                    done.countDown();
                }
            }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                failure[0] = new java.io.IOException("CapCut WebSocket failed: " + t.getMessage(), t);
                done.countDown();
            }
            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }
        };
        sockets[0] = client.newWebSocket(request, listener);
        if (!done.await(15, TimeUnit.SECONDS)) {
            sockets[0].close(1001, "timeout");
            throw new java.net.SocketTimeoutException("CapCut synthesis timed out after 15 seconds");
        }
        sockets[0].close(1000, "complete");
        if (failure[0] != null) throw failure[0];
        return audio.toByteArray();
    }

    private synchronized void fetchAnonymousToken(OkHttpClient client) throws Exception {
        if (System.currentTimeMillis() < tokenExpiresAt) return;
        Request request = new Request.Builder()
                .url("https://sami-sg.byteoversea.com/internal/api/v1/token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mobile")
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new java.io.IOException("CapCut token HTTP " + response.code());
            JSONObject body = new JSONObject(response.body() == null ? "{}" : response.body().string());
            cachedToken = body.optString("token", body.optString("access_token"));
            if (cachedToken.isEmpty()) throw new java.io.IOException("CapCut anonymous token is unavailable");
            tokenExpiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        }
    }
}
