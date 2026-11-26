package com.builtbydeep.sovra;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class CloudTtsClientsTest {
    @Test
    public void providerChunkSizesRespectSafeLimitsWithoutSplittingSurrogatePairs() {
        String text = repeat('a', 899) + "\uD83D\uDE42" + "b";

        List<String> rime = CloudTtsClients.splitText(text, 900);
        List<String> speechify = CloudTtsClients.splitText(repeat('b', 2001), 2000);
        List<String> elevenLabs = CloudTtsClients.splitText(repeat('c', 1501), 1500);

        assertEquals(2, rime.size());
        assertEquals(899, rime.get(0).length());
        assertEquals("\uD83D\uDE42b", rime.get(1));
        assertEquals(2000, speechify.get(0).length());
        assertEquals(1, speechify.get(1).length());
        assertEquals(1500, elevenLabs.get(0).length());
        assertEquals(1, elevenLabs.get(1).length());
    }

    @Test
    public void rimeVoiceCatalogIsFilteredBySelectedModel() {
        CloudTtsProvider.Voice coda = new CloudTtsProvider.Voice(
                "astra", "Astra", "en", CloudTtsClients.RIME_CODA, "English");
        CloudTtsProvider.Voice mist = new CloudTtsProvider.Voice(
                "mist-voice", "Mist Voice", "en", CloudTtsClients.RIME_MIST, "English");

        assertEquals(Arrays.asList(coda), CloudTtsClients.filterVoicesForModel(
                CloudTtsClients.RIME, CloudTtsClients.RIME_CODA, Arrays.asList(coda, mist)));
        assertEquals(Arrays.asList(mist), CloudTtsClients.filterVoicesForModel(
                CloudTtsClients.RIME, CloudTtsClients.RIME_MIST, Arrays.asList(coda, mist)));
    }

    @Test
    public void simba32OnlyAllowsEnglishVoices() {
        CloudTtsProvider.Voice english = new CloudTtsProvider.Voice(
                "en", "English", "en-US", "", "US");
        CloudTtsProvider.Voice hindi = new CloudTtsProvider.Voice(
                "hi", "Hindi", "hi-IN", "", "India");

        assertEquals(Arrays.asList(english), CloudTtsClients.filterVoicesForModel(
                CloudTtsClients.SPEECHIFY, CloudTtsClients.SPEECHIFY_32,
                Arrays.asList(english, hindi)));
    }

    @Test
    public void creditSavingModelsRemainDefaultAndFishLabelsMatchSelectedTier() {
        assertEquals(CloudTtsClients.ELEVEN_FLASH,
                CloudTtsClients.defaultModel(CloudTtsClients.ELEVENLABS));
        assertEquals(CloudTtsClients.SPEECHIFY_32,
                CloudTtsClients.defaultModel(CloudTtsClients.SPEECHIFY));
        assertEquals(CloudTtsClients.RIME_MIST,
                CloudTtsClients.defaultModel(CloudTtsClients.RIME));
        assertEquals(CloudTtsClients.FISH_FREE,
                CloudTtsClients.defaultModel(CloudTtsClients.FISH));
        assertEquals("S2.1 Pro Free",
                CloudTtsClients.modelLabel(CloudTtsClients.FISH, CloudTtsClients.FISH_FREE));
        assertEquals("Unlimited/mo",
                CloudTtsClients.quotaLabel(CloudTtsClients.FISH, CloudTtsClients.FISH_FREE));
        assertEquals("S2.1 Pro",
                CloudTtsClients.modelLabel(CloudTtsClients.FISH, CloudTtsClients.FISH_PRO));
        assertEquals("Paid credits",
                CloudTtsClients.quotaLabel(CloudTtsClients.FISH, CloudTtsClients.FISH_PRO));
        assertEquals("83 languages • English, Hindi",
                CloudTtsClients.languageLabel(CloudTtsClients.FISH, CloudTtsClients.FISH_FREE));
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
