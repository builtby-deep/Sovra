package com.builtbydeep.sovra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

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

    @Test
    public void displayNameUsesCleanSpeakerTokenAcrossLocales() {
        assertEquals("Madhur • Male",
                EdgeTtsClient.formatDisplayName("hi-IN-MadhurNeural", "Male"));
        assertEquals("Xiaobei • Female",
                EdgeTtsClient.formatDisplayName("zh-CN-liaoning-XiaobeiNeural", "Female"));
        assertEquals("Andrew (Multilingual) • Male",
                EdgeTtsClient.formatDisplayName("en-US-AndrewMultilingualNeural", "Male"));
    }

    @Test
    public void deduplicateVoicesPreservesFirstVoiceAndOrderIgnoringCase() {
        EdgeTtsClient.Voice first = new EdgeTtsClient.Voice("en-US-EmmaNeural", "en-US", "Female");
        EdgeTtsClient.Voice duplicate = new EdgeTtsClient.Voice("EN-us-emmaneural", "en-US", "Male");
        EdgeTtsClient.Voice next = new EdgeTtsClient.Voice("en-GB-SoniaNeural", "en-GB", "Female");

        List<EdgeTtsClient.Voice> voices =
                EdgeTtsClient.deduplicateVoices(Arrays.asList(first, duplicate, next));

        assertEquals(2, voices.size());
        assertTrue(voices.get(0) == first);
        assertTrue(voices.get(1) == next);
    }
}
