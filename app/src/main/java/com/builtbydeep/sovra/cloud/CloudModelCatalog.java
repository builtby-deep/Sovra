package com.builtbydeep.sovra.cloud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class CloudModelCatalog {
    public static List<HashMap<String, Object>> createEntries() {
        List<HashMap<String, Object>> entries = new ArrayList<>();
        for (CloudTtsProvider provider : CloudTtsRegistry.getProviders().values()) {
            HashMap<String, Object> entry = new HashMap<>();
            entry.put("name", provider.getName());
            entry.put("language", provider.getSubtitle());
            entry.put("gender", provider.getBadge());
            entry.put("size", "Cloud");
            entry.put("type", "cloud");
            entry.put("cloud_provider", provider.getId());
            entry.put("onnx_path", "");
            entry.put("tokens_path", "");
            entry.put("is_downloading", "false");
            entry.put("download_progress", "0");
            entries.add(entry);
        }
        return entries;
    }

    public static HashMap<String, Object> getEntry(String providerId) {
        for (HashMap<String, Object> entry : createEntries()) {
            if (providerId.equals(entry.get("cloud_provider"))) return entry;
        }
        return null;
    }
}
