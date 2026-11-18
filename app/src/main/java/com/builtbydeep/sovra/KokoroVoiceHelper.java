package com.builtbydeep.sovra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KokoroVoiceHelper {
    public static final int DEFAULT_SPEAKER_ID = 31;

    private static final List<Voice> VOICES;

    static {
        List<Voice> voices = new ArrayList<>();
        addRange(voices, "en-US", "af_alloy", "Alloy", "F", "af_aoede", "Aoede", "F",
                "af_bella", "Bella", "F", "af_heart", "Heart", "F", "af_jessica", "Jessica", "F",
                "af_kore", "Kore", "F", "af_nicole", "Nicole", "F", "af_nova", "Nova", "F",
                "af_river", "River", "F", "af_sarah", "Sarah", "F", "af_sky", "Sky", "F",
                "am_adam", "Adam", "M", "am_echo", "Echo", "M", "am_eric", "Eric", "M",
                "am_fenrir", "Fenrir", "M", "am_liam", "Liam", "M", "am_michael", "Michael", "M",
                "am_onyx", "Onyx", "M", "am_puck", "Puck", "M", "am_santa", "Santa", "M");
        addRange(voices, "en-GB", "bf_alice", "Alice", "F", "bf_emma", "Emma", "F",
                "bf_isabella", "Isabella", "F", "bf_lily", "Lily", "F", "bm_daniel", "Daniel", "M",
                "bm_fable", "Fable", "M", "bm_george", "George", "M", "bm_lewis", "Lewis", "M");
        addRange(voices, "es", "ef_dora", "Dora", "F", "em_alex", "Alex", "M");
        addRange(voices, "fr", "ff_siwis", "Siwis", "F");
        addRange(voices, "hi", "hf_alpha", "Alpha", "F", "hf_beta", "Beta", "F",
                "hm_omega", "Omega", "M", "hm_psi", "Psi", "M");
        addRange(voices, "it", "if_sara", "Sara", "F", "im_nicola", "Nicola", "M");
        addRange(voices, "ja", "jf_alpha", "Alpha", "F", "jf_gongitsune", "Gongitsune", "F",
                "jf_nezumi", "Nezumi", "F", "jf_tebukuro", "Tebukuro", "F", "jm_kumo", "Kumo", "M");
        addRange(voices, "pt", "pf_dora", "Dora", "F", "pm_alex", "Alex", "M",
                "pm_santa", "Santa", "M");
        addRange(voices, "zh", "zf_xiaobei", "Xiaobei", "F", "zf_xiaoni", "Xiaoni", "F",
                "zf_xiaoxiao", "Xiaoxiao", "F", "zf_xiaoyi", "Xiaoyi", "F",
                "zm_yunjian", "Yunjian", "M", "zm_yunxi", "Yunxi", "M",
                "zm_yunxia", "Yunxia", "M", "zm_yunyang", "Yunyang", "M");
        if (voices.size() != 53) {
            throw new ExceptionInInitializerError("Kokoro voice catalog must contain 53 voices");
        }
        VOICES = Collections.unmodifiableList(voices);
    }

    private KokoroVoiceHelper() {
    }

    private static void addRange(List<Voice> voices, String language, String... values) {
        for (int i = 0; i < values.length; i += 3) {
            voices.add(new Voice(voices.size(), values[i], values[i + 1], values[i + 2], language));
        }
    }

    public static List<Voice> getVoices() {
        return VOICES;
    }

    public static Voice getVoice(int speakerId) {
        if (speakerId < 0 || speakerId >= VOICES.size()) {
            throw new IllegalArgumentException("Unknown Kokoro speaker ID: " + speakerId);
        }
        return VOICES.get(speakerId);
    }

    public static final class Voice {
        public final int id;
        public final String modelName;
        public final String displayName;
        public final String gender;
        public final String language;

        private Voice(int id, String modelName, String displayName, String gender, String language) {
            this.id = id;
            this.modelName = modelName;
            this.displayName = displayName;
            this.gender = gender;
            this.language = language;
        }
    }
}
