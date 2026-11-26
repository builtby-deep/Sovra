package com.builtbydeep.sovra.cloud;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CloudTtsRegistry {
    private static final Map<String, CloudTtsProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        register(new CapCutTtsProvider());
        register(new EdgeTtsProvider());
        register(new FishAudioProvider());
        register(new GroqOrpheusProvider());
        register(new CartesiaSonicProvider());
        register(new GradiumAiProvider());
        register(new CloudflareAuraProvider());
        register(new ElevenLabsProvider());
        register(new SpeechifyAiProvider());
        register(new RimeAiProvider());
        register(new WitAiProvider());
    }

    private static void register(CloudTtsProvider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    public static Map<String, CloudTtsProvider> getProviders() {
        return Collections.unmodifiableMap(PROVIDERS);
    }

    public static CloudTtsProvider get(String id) {
        return PROVIDERS.get(id);
    }

    public static boolean isCloudProvider(String id) {
        return id != null && PROVIDERS.containsKey(id);
    }
}
