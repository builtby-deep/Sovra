package com.builtbydeep.sovra;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MmsCatalog {
    public static final String BASE_URL =
            "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/";

    private MmsCatalog() {
    }

    public static List<Model> load(Context context) throws IOException {
        List<Model> models = new ArrayList<>();
        try (InputStream input = context.getAssets().open("languages-supported.json");
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                throw new IOException("MMS language catalog must be a JSON array");
            }
            JsonArray rows = root.getAsJsonArray();
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject row = element.getAsJsonObject();
                if (!row.has("ONNX Exists") || !row.get("ONNX Exists").getAsBoolean()) {
                    continue;
                }
                String code = getString(row, "Iso Code").toLowerCase(Locale.ROOT);
                if (code.isEmpty()) {
                    continue;
                }
                String name = getString(row, "Language Name");
                String country = getString(row, "Country");
                String base = BASE_URL + code + "/";
                models.add(new Model(
                        code,
                        name,
                        country,
                        base + "model.onnx",
                        base + "tokens.txt",
                        row.has("Sample Exists") && row.get("Sample Exists").getAsBoolean()
                                ? base + "sample.wav" : null));
            }
        } catch (IllegalStateException exception) {
            throw new IOException("MMS language catalog contains an invalid field", exception);
        }
        return Collections.unmodifiableList(models);
    }

    private static String getString(JsonObject row, String name) {
        JsonElement element = row.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    public static final class Model {
        public final String isoCode;
        public final String languageName;
        public final String country;
        public final String modelUrl;
        public final String tokensUrl;
        public final String sampleUrl;

        private Model(
                String isoCode,
                String languageName,
                String country,
                String modelUrl,
                String tokensUrl,
                String sampleUrl) {
            this.isoCode = isoCode;
            this.languageName = languageName;
            this.country = country;
            this.modelUrl = modelUrl;
            this.tokensUrl = tokensUrl;
            this.sampleUrl = sampleUrl;
        }
    }
}
