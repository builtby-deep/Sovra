package com.builtbydeep.sovra;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VoiceModelCatalog {
    private VoiceModelCatalog() {
    }

    public static List<Model> getBuiltInModels() {
        List<Model> models = new ArrayList<>();
        models.add(new Model("kokoro-multi-lang-v1_0", "Kokoro Multi-Language",
                "kokoro", KokoroModelFiles.BASE_URL, "Multi-language", ""));
        models.add(new Model("supertonic", "Supertonic v3", "supertonic",
                SupertonicModelFiles.BASE_URL, "31 languages", "400 MB"));
        for (PiperModelCatalog.Model model : PiperModelCatalog.getModels()) {
            models.add(new Model(model.modelDirectory, model.name, "vits",
                    model.getDownloadUrl(), model.language, model.size));
        }
        return Collections.unmodifiableList(models);
    }

    public static List<Model> loadAll(Context context) throws IOException {
        List<Model> models = new ArrayList<>(getBuiltInModels());
        for (MmsCatalog.Model model : MmsCatalog.load(context)) {
            models.add(new Model("mms-" + model.isoCode, model.languageName, "mms",
                    model.modelUrl, model.country, ""));
        }
        return Collections.unmodifiableList(models);
    }

    public static final class Model {
        public final String id;
        public final String name;
        public final String type;
        public final String downloadUrl;
        public final String language;
        public final String size;

        private Model(
                String id,
                String name,
                String type,
                String downloadUrl,
                String language,
                String size) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.downloadUrl = downloadUrl;
            this.language = language;
            this.size = size;
        }
    }
}
