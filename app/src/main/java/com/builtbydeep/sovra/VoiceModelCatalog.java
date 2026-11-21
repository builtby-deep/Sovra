package com.builtbydeep.sovra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class VoiceModelCatalog {

    private VoiceModelCatalog() {}

    public static List<HashMap<String, Object>> createBuiltInEntries() {
        List<HashMap<String, Object>> entries = new ArrayList<>();
        HashMap<String, Object> edgeTts = new HashMap<>();
        edgeTts.put("name", "Microsoft Edge TTS");
        edgeTts.put("type", EdgeTtsClient.MODEL_TYPE);
        edgeTts.put("language", "Multilingual");
        edgeTts.put("gender", "300+ speakers");
        edgeTts.put("size", "0 MB");
        edgeTts.put("model_url", EdgeTtsClient.MODEL_ID);
        entries.add(edgeTts);
        entries.add(KokoroModelFiles.createCatalogEntry());
        entries.add(SupertonicModelFiles.createCatalogEntry());
        entries.addAll(PiperModelCatalog.createCatalogEntries());
        return entries;
    }
}
