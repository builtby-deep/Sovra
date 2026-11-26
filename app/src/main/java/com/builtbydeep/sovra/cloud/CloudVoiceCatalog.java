package com.builtbydeep.sovra.cloud;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CloudVoiceCatalog {
    public static final List<String> GROQ_VOICES =
            Collections.unmodifiableList(Arrays.asList("autumn", "diana", "hannah", "austin", "daniel", "troy"));
    public static final List<String> CLOUDFLARE_VOICES =
            Collections.unmodifiableList(Arrays.asList("luna", "asteria", "athena", "hera", "orion",
                    "arcas", "perseus", "angus", "orpheus", "helios", "zeus"));
    public static final List<String> GRADIUM_VOICES =
            Collections.unmodifiableList(Arrays.asList("YTpq7expH9539ERJ", "Bla6SbVMczYnOhfK"));
    public static final List<String> CAPCUT_VOICES =
            Collections.unmodifiableList(Arrays.asList("en_us_001", "en_us_006", "en_uk_001",
                    "en_au_001", "jp_001", "BV001_streaming", "BV421_vivn_streaming"));
}
