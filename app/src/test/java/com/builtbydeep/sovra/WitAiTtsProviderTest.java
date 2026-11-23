package com.builtbydeep.sovra;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WitAiTtsProviderTest {
    @Test
    public void curatedVoicesAreLocalAndLimitedToEight() throws Exception {
        List<CloudTtsProvider.Voice> voices = new WitAiTtsProvider().fetchVoices("");

        assertEquals(8, voices.size());
        assertEquals("Charlie", voices.get(0).id);
        assertEquals("en-US", voices.get(0).locale);
        assertEquals("Rosie", voices.get(6).id);
        assertEquals("en-CA", voices.get(6).locale);
        assertEquals("Colin", voices.get(7).id);
    }

    @Test
    public void sanitizerRemovesTagsEmojiAndCollapsesWhitespace() {
        assertEquals("Hello there.", WitAiTtsProvider.sanitizeText(
                "  [happy] Hello   😀   there.  "));
    }

    @Test
    public void chunkerUsesSentenceBoundaryAndNeverExceedsLimit() {
        String text = "Dr. Smith measured 3.14 units. Next sentence starts here. "
                + "word ".repeat(60);

        List<String> chunks = WitAiTtsProvider.splitIntoChunks(text);

        assertTrue(chunks.get(0).endsWith("units."));
        for (String chunk : chunks) assertTrue(chunk.length() <= 250);
        assertTrue(String.join("", chunks).contains("Next sentence starts here."));
    }

    @Test
    public void chunkerHardSlicesUnbrokenStringsAtTheLimit() {
        List<String> chunks = WitAiTtsProvider.splitIntoChunks("x".repeat(600));

        assertEquals(3, chunks.size());
        assertEquals(250, chunks.get(0).length());
        assertEquals(250, chunks.get(1).length());
        assertEquals(100, chunks.get(2).length());
    }

    @Test
    public void chunkerUsesClauseBoundariesBeforeWhitespace() {
        List<String> chunks = WitAiTtsProvider.splitIntoChunks(
                "x".repeat(240) + ", " + "y".repeat(300));

        assertTrue(chunks.get(0).endsWith(","));
        for (String chunk : chunks) assertTrue(chunk.length() <= 250);
    }

    @Test
    public void devanagariGuardChecksTheRequestedUnicodeBlock() {
        assertTrue(WitAiTtsProvider.containsDevanagari("Hello नमस्ते"));
        assertFalse(WitAiTtsProvider.containsDevanagari("Hello there"));
    }

    @Test
    public void stripId3v2RemovesOnlyTheLeadingTagPayload() {
        byte[] mp3 = {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 3, 1, 2, 3, 9, 8, 7, 6};

        assertEquals(4, WitAiTtsProvider.stripId3v2(mp3).length);
        assertEquals(9, WitAiTtsProvider.stripId3v2(mp3)[0]);
    }
}
