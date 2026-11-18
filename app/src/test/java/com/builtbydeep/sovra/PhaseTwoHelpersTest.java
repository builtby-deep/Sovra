package com.builtbydeep.sovra;

import org.junit.Test;

import com.builtbydeep.sovra.system.TtsDefaultHelper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PhaseTwoHelpersTest {
    @Test
    public void kokoroVoiceCatalogHasAllIdsAndDefaultSpeaker() {
        assertEquals(53, KokoroVoiceHelper.getVoices().size());
        assertEquals(31, KokoroVoiceHelper.DEFAULT_SPEAKER_ID);
        assertEquals("hf_alpha", KokoroVoiceHelper.getVoice(31).modelName);
        assertEquals(52, KokoroVoiceHelper.getVoice(52).id);
    }

    @Test
    public void emotionTagsChangeSynthesisProfileAndApplyLinearVolumeAttack() {
        RecordingSynthesizer synthesizer = new RecordingSynthesizer(2500);
        AudioEmotionHelper helper = new AudioEmotionHelper(synthesizer);

        byte[] pcm = helper.processAndGenerate(
                "A[whisper]B", false, true, 1.0f, 1.0f, 0.8f, 0.5f);

        assertEquals(2, synthesizer.calls.size());
        assertEquals("A", synthesizer.calls.get(0).text);
        assertEquals("B", synthesizer.calls.get(1).text);
        assertEquals(0.95f, synthesizer.calls.get(1).speed, 0.0001f);
        assertEquals(1.05f, synthesizer.calls.get(1).pitch, 0.0001f);
        assertEquals(2500 * 2 * 2, pcm.length);
        assertEquals(8000, readSample(pcm, 0));
        assertTrue(readSample(pcm, 2501) < 8000);
        assertTrue(Math.abs(readSample(pcm, 3750) - 7250) < 5);
        assertTrue(Math.abs(readSample(pcm, 4999) - 6500) < 5);
    }

    @Test
    public void punctuationUsesScaledSilenceAndSpeakerGapIsEvenAlignedPcm() {
        RecordingSynthesizer synthesizer = new RecordingSynthesizer(1);
        AudioEmotionHelper helper = new AudioEmotionHelper(synthesizer);
        byte[] punctuated = helper.processAndGenerate(
                "A,B", true, false, 1.0f, 1.0f, 1.0f, 0.0f);
        assertEquals(4, punctuated.length);

        AudioEmotionHelper.triggerSpeakerGap();
        byte[] withSpeakerGap = helper.processAndGenerate(
                "A", false, false, 1.0f, 1.0f, 1.0f, 0.5f);
        assertEquals(1002, withSpeakerGap.length);
        for (int i = 0; i < 1000; i++) {
            assertEquals(0, withSpeakerGap[i]);
        }
    }

    @Test
    public void punctuationDelayHasExactBaseAndJitterBounds() {
        assertEquals(0, AudioEmotionHelper.punctuationDelayMs(",", 0.0f, 1.0f));
        for (int i = 0; i < 100; i++) {
            int delay = AudioEmotionHelper.punctuationDelayMs("...", 2.0f, 1.0f);
            assertTrue(delay >= 810 && delay <= 990);
        }
    }

    @Test
    public void modelCatalogContainsAllNineUpstreamPiperArchives() {
        List<PiperModelCatalog.Model> models = PiperModelCatalog.getModels();
        assertEquals(9, models.size());
        assertEquals("vits-piper-en_US-libritts-high.tar.bz2", models.get(0).archiveName);
        assertEquals("vits-piper-hi_IN-rohan-medium.tar.bz2",
                models.get(models.size() - 1).archiveName);
    }

    @Test
    public void devanagariMatraOrderIsNormalized() {
        assertEquals("कि", TextImportHelper.normalizeDevanagari("िक"));
        assertEquals("क़ि", TextImportHelper.normalizeDevanagari("िक़"));
    }

    @Test
    public void systemTtsDefaultsUseNormalizedSp5Keys() {
        assertEquals("sys_tts_en-us", TtsDefaultHelper.preferenceKey("en_US"));
    }

    private static int readSample(byte[] pcm, int sampleIndex) {
        int offset = sampleIndex * 2;
        return (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
    }

    private static final class RecordingSynthesizer
            implements AudioEmotionHelper.SegmentSynthesizer {
        private final int sampleCount;
        final List<Call> calls = new ArrayList<>();

        RecordingSynthesizer(int sampleCount) {
            this.sampleCount = sampleCount;
        }

        @Override
        public AudioEmotionHelper.AudioSegment generate(String text, float speed, float pitch) {
            calls.add(new Call(text, speed, pitch));
            byte[] pcm = new byte[sampleCount * 2];
            for (int i = 0; i < sampleCount; i++) {
                pcm[i * 2] = 0x10;
                pcm[i * 2 + 1] = 0x27;
            }
            return new AudioEmotionHelper.AudioSegment(pcm, 1000);
        }

        @Override
        public int getSampleRate() {
            return 1000;
        }
    }

    private static final class Call {
        final String text;
        final float speed;
        final float pitch;

        Call(String text, float speed, float pitch) {
            this.text = text;
            this.speed = speed;
            this.pitch = pitch;
        }
    }
}
