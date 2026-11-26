package com.builtbydeep.sovra.cloud;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CloudTtsRegistry {
    private static final Map<String, CloudTtsProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        register(new FishAudioProvider());
        register(new GroqOrpheusProvider());
        register(new CartesiaSonicProvider());
        register(new GradiumAiProvider());
        register(new CloudflareAuraProvider());
        register(new CapCutTtsProvider());
    }

    private static void register(CloudTtsProvider provider) {
        PROVIDERS.put(provider.getId(), provider);
    }

    public static Map<String, CloudTtsProvider> getProviders() {
        return PROVIDERS;
    }

    public static CloudTtsProvider get(String id) {
        return PROVIDERS.get(id);
    }

    public static boolean isCloudProvider(String id) {
        return id != null && PROVIDERS.containsKey(id);
    }
}
