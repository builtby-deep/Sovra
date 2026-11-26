package com.builtbydeep.sovra.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class CapCutTtsProvider implements CloudTtsProvider {
    private static final String DEFAULT_SAMI_WS = "wss://sami-sg1.byteintlapi.com/internal/api/v1/ws";
    private static final String DEFAULT_TOKEN_URL = "https://edit-api-sg.capcut.com/lv/v1/audio/speech/token";
    private volatile String cachedToken = "";
    private volatile String cachedAppKey = "";
    private volatile long tokenExpiresAt;

    @Override public String getId() { return "capcut_tts"; }
    @Override public String getName() { return "CapCut TTS"; }
    @Override public String getSubtitle() { return "Neural Creator Voices • Wrapper / Direct"; }
    @Override public String getBadge() { return "CLOUD | ONLINE"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        SharedPreferences p = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String wrapper = p.getString("capcut_wrapper_url", "").trim();
        if (!wrapper.isEmpty()) {
            return synthesizeWrapper(wrapper, text, options);
        }
        return synthesizeWebSocket(text, options);
    }

    private byte[] synthesizeWrapper(String baseUrl, String text, CloudVoiceOptions options) throws Exception {
        String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String speaker = (options == null || options.voice == null || options.voice.trim().isEmpty())
                ? "en_us_001" : options.voice.trim();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();

        Request request;
        if (cleanBase.endsWith("/v2/synthesize")) {
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("speaker", speaker);
            body.put("method", "buffer");
            request = new Request.Builder()
                    .url(cleanBase)
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
        } else {
            String endpoint = cleanBase.endsWith("/v1/synthesize") ? cleanBase : (cleanBase + "/v1/synthesize");
            String url = endpoint + "?text=" + URLEncoder.encode(text, "UTF-8")
                    + "&speaker=" + URLEncoder.encode(speaker, "UTF-8")
                    + "&type=0&pitch=10&speed=10&volume=10&method=buffer";
            request = new Request.Builder().url(url).get().build();
        }

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("CapCut server HTTP " + response.code());
            }
            return response.body() == null ? new byte[0] : response.body().bytes();
        }
    }

    private byte[] synthesizeWebSocket(String text, CloudVoiceOptions options) throws Exception {
        String speaker = (options == null || options.voice == null || options.voice.trim().isEmpty())
                ? "en_us_001" : options.voice.trim();
        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        if (System.currentTimeMillis() >= tokenExpiresAt || cachedToken.isEmpty()) {
            fetchSpeechToken(client);
        }

        JSONObject payload = new JSONObject();
        payload.put("text", text);
        payload.put("speaker", speaker);
        payload.put("pitch", 10);
        payload.put("speed", 10);
        payload.put("volume", 10);
        payload.put("audio_config", new JSONObject().put("format", "wav").put("sample_rate", 24000));

        JSONObject startTask = new JSONObject();
        startTask.put("token", cachedToken);
        if (!cachedAppKey.isEmpty()) startTask.put("appkey", cachedAppKey);
        startTask.put("namespace", "TTS");
        startTask.put("event", "StartTask");
        startTask.put("payload", payload.toString());

        Request request = new Request.Builder()
                .url(DEFAULT_SAMI_WS)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .header("Origin", "https://www.capcut.com")
                .build();

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        final WebSocket[] sockets = new WebSocket[1];

        sockets[0] = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                webSocket.send(startTask.toString());
            }
            @Override public void onMessage(WebSocket webSocket, String message) {
                try {
                    JSONObject event = new JSONObject(message);
                    String type = event.optString("event", event.optString("type", ""));
                    int status = event.optInt("status_code", 20000000);
                    if (type.contains("TaskFinished") || type.contains("TaskCompleted") || type.contains("completed")) {
                        done.countDown();
                        webSocket.close(1000, "complete");
                    } else if (type.contains("TaskFailed") || type.contains("Error") || (status >= 400 && status != 20000000)) {
                        failure[0] = new IOException("CapCut WS error: " + message);
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
                failure[0] = new IOException("CapCut WS failed: " + t.getMessage(), t);
                done.countDown();
            }
            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
                done.countDown();
            }
        });

        if (!done.await(15, TimeUnit.SECONDS)) {
            sockets[0].close(1001, "timeout");
            throw new SocketTimeoutException("CapCut synthesis timed out after 15 seconds");
        }
        sockets[0].close(1000, "complete");
        if (failure[0] != null) throw failure[0];
        return audio.toByteArray();
    }

    private synchronized void fetchSpeechToken(OkHttpClient client) throws Exception {
        if (System.currentTimeMillis() < tokenExpiresAt && !cachedToken.isEmpty()) return;
        Request request = new Request.Builder()
                .url(DEFAULT_TOKEN_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .header("Origin", "https://www.capcut.com")
                .header("Referer", "https://www.capcut.com/")
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Direct CapCut token blocked (HTTP " + response.code() + "). Set CapCut Wrapper URL in settings.");
            }
            JSONObject root = new JSONObject(response.body() == null ? "{}" : response.body().string());
            JSONObject data = root.has("data") ? root.optJSONObject("data") : root;
            if (data != null) {
                cachedToken = data.optString("token", data.optString("access_token", ""));
                cachedAppKey = data.optString("appkey", data.optString("app_key", ""));
            }
            if (cachedToken.isEmpty()) {
                throw new IOException("CapCut token empty. Set CapCut Wrapper URL in settings.");
            }
            tokenExpiresAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(6);
        }
    }
}
