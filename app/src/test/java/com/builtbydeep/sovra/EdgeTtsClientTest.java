package com.builtbydeep.sovra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EdgeTtsClientTest {

    @Test
    public void secMsGecV2TokenMatchesUpstreamVectorAndFormat() {
        String token = EdgeTtsClient.createSecMsGecToken(0);

        assertEquals("52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6",
                token);
        assertTrue(token.matches("[0-9A-F]{16}-[0-9A-F]{64}"));
        assertEquals("2-143.0.3650.75", EdgeTtsClient.SEC_MS_GEC_VERSION);
        assertEquals("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0",
                EdgeTtsClient.USER_AGENT);
    }

    @Test
    public void generatedMuidCookieMatchesUpstreamFormat() {
        assertTrue(EdgeTtsClient.createMuidCookie().matches("muid=[0-9A-F]{32};"));
    }

    @Test
    public void fallbackVoiceListIncludesRequestedDefaultSpeakers() {
        java.util.List<EdgeTtsClient.Voice> voices = EdgeTtsClient.fallbackVoices();

        assertEquals(8, voices.size());
        assertEquals("hi-IN-SwaraNeural", voices.get(0).name);
        assertEquals("hi-IN", voices.get(0).locale);
        assertEquals("Female", voices.get(0).gender);
        assertEquals("ja-JP-NanamiNeural", voices.get(7).name);
    }
}
