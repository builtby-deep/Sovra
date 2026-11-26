package com.builtbydeep.sovra.cloud;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okio.ByteString;

public final class EdgeTtsProvider implements CloudTtsProvider {
    private static final String TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";

    @Override public String getId() { return "edge_tts"; }
    @Override public String getName() { return "Microsoft Edge TTS"; }
    @Override public String getSubtitle() { return "Keyless Neural • Hindi & English"; }
    @Override public String getBadge() { return "CLOUD | ONLINE"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception {
        String voice = (options == null || options.voice == null || options.voice.trim().isEmpty())
                ? "en-US-AriaNeural" : options.voice.trim();
        String lang = voice.length() >= 5 ? voice.substring(0, 5) : "en-US";
        String secMsGec = generateSecMsGec();
        String connId = UUID.randomUUID().toString().replace("-", "");
        String reqId = UUID.randomUUID().toString().replace("-", "");

        String wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
                + "?TrustedClientToken=" + TRUSTED_TOKEN
                + "&Sec-MS-GEC=" + secMsGec
                + "&Sec-MS-GEC-Version=1-130.0.2849.68"
                + "&ConnectionId=" + connId;

        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(wsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .build();

        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");

        String configMsg = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";

        String ssmlMsg = "X-RequestId:" + reqId + "\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n"
                + "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + lang + "'>"
                + "<voice name='" + voice + "'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>"
                + escaped + "</prosody></voice></speak>";

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        final Exception[] failure = new Exception[1];
        final WebSocket[] sockets = new WebSocket[1];

        sockets[0] = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response r) {
                ws.send(configMsg);
                ws.send(ssmlMsg);
            }
            @Override public void onMessage(WebSocket ws, String msg) {
                if (msg.contains("Path:turn.end")) {
                    done.countDown();
                    ws.close(1000, "done");
                }
            }
            @Override public void onMessage(WebSocket ws, ByteString bytes) {
                try {
                    byte[] raw = bytes.toByteArray();
                    if (raw.length > 2) {
                        int headerLen = ((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF);
                        int audioStart = 2 + headerLen;
                        if (audioStart < raw.length) {
                            audio.write(raw, audioStart, raw.length - audioStart);
                        }
                    }
                } catch (Exception e) {
                    failure[0] = e;
                    done.countDown();
                }
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response r) {
                failure[0] = new IOException("Edge TTS WS failed: " + t.getMessage(), t);
                done.countDown();
            }
            @Override public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
                done.countDown();
            }
        });

        if (!done.await(20, TimeUnit.SECONDS)) {
            sockets[0].close(1001, "timeout");
            throw new SocketTimeoutException("Edge TTS timed out");
        }
        sockets[0].close(1000, "done");
        if (failure[0] != null) throw failure[0];
        if (audio.size() == 0) throw new IOException("Edge TTS returned empty audio");
        return audio.toByteArray();
    }

    private static String generateSecMsGec() throws Exception {
        long ticks = ((System.currentTimeMillis() / 1000L) + 11644473600L) * 10000000L;
        ticks -= (ticks % 3000000000L);
        String input = ticks + TRUSTED_TOKEN;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.US_ASCII));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format(Locale.US, "%02X", b));
        return sb.toString();
    }
}
