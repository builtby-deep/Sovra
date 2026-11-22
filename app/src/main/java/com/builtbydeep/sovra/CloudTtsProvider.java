package com.builtbydeep.sovra;

import java.io.IOException;
import java.util.List;

public interface CloudTtsProvider {
    String getId();
    int getSafeChunkLimit();
    List<Voice> fetchVoices(String apiKey) throws IOException;
    byte[] synthesize(String apiKey, String modelId, Voice voice, String text,
                      String language, float speed, float pitch, boolean smartPunctuation)
            throws IOException;

    final class Voice {
        public final String id;
        public final String name;
        public final String locale;
        public final String modelId;
        public final String region;

        public Voice(String id, String name, String locale, String modelId, String region) {
            this.id = id;
            this.name = name;
            this.locale = locale;
            this.modelId = modelId;
            this.region = region;
        }

        public String displayName() {
            return name + " • " + locale;
        }
    }
}
