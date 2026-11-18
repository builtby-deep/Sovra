package com.builtbydeep.sovra;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SovraModels {
    private SovraModels() {
    }

    public static List<Item> load(Context context) throws IOException {
        List<Item> items = new ArrayList<>();
        for (VoiceModelCatalog.Model model : VoiceModelCatalog.getBuiltInModels()) {
            String size = model.size == null ? "" : model.size;
            items.add(new Item(model.id, model.name, model.type, model.language, size, null));
        }
        SharedPreferences preferences = context.getSharedPreferences("sp3", Context.MODE_PRIVATE);
        if (preferences.getBoolean("mms_models_enabled", true)) {
            for (MmsCatalog.Model model : MmsCatalog.load(context)) {
                items.add(new Item("mms-" + model.isoCode,
                        model.languageName, "mms", model.country, "", model));
            }
        }
        return Collections.unmodifiableList(items);
    }

    public static File directory(Context context, Item item) {
        if (item.type.equals("custom")) {
            return new File(new File(context.getFilesDir(), "PiperModels"), item.id);
        }
        return new File(new File(context.getFilesDir(), "models"), item.id);
    }

    public static boolean isInstalled(Context context, Item item) {
        if (item.id.equals(KokoroModelFiles.DIRECTORY_NAME)) {
            return new KokoroModelFiles(context.getFilesDir()).isComplete();
        }
        if (item.type.equals("supertonic")) {
            return new SupertonicModelFiles(context.getFilesDir()).isComplete();
        }
        File directory = directory(context, item);
        return TtsEngineUtils.hasNonEmptyFiles(directory, "model.onnx", "tokens.txt");
    }

    public static boolean isDownloaded(Context context, Item item) {
        if (isInstalled(context, item)) return true;
        File directory = item.id.equals(KokoroModelFiles.DIRECTORY_NAME)
                ? new KokoroModelFiles(context.getFilesDir()).getDirectory()
                : item.type.equals("supertonic")
                ? new SupertonicModelFiles(context.getFilesDir()).getDirectory()
                : directory(context, item);
        return sizeOf(directory) > 0;
    }

    public static Item customItem(String id, String name) {
        return new Item(id, name, "custom", "Custom", "", null);
    }

    public static void activate(Context context, Item item) throws IOException {
        if (!isInstalled(context, item)) {
            throw new IOException("Model is not completely installed: " + item.name);
        }
        SharedPreferences preferences = context.getSharedPreferences("sp1", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit()
                .putString("active_model_name", item.name)
                .putString("active_model_type", item.type.equals("custom") ? "vits" : item.type)
                .putString("active_language", item.language);
        if (item.type.equals("kokoro")) {
            KokoroModelFiles files = new KokoroModelFiles(context.getFilesDir());
            editor.putString("active_model", files.getModel().getAbsolutePath())
                    .putString("active_tokens", files.getTokens().getAbsolutePath())
                    .putString("active_voices_bin", files.getVoices().getAbsolutePath())
                    .putInt("active_kokoro_speaker",
                            preferences.getInt("active_kokoro_speaker",
                                    KokoroVoiceHelper.DEFAULT_SPEAKER_ID));
        } else if (item.type.equals("supertonic")) {
            editor.putString("active_supertonic_dir",
                    new SupertonicModelFiles(context.getFilesDir()).getDirectory()
                            .getAbsolutePath());
        } else {
            File directory = directory(context, item);
            editor.putString("active_model", new File(directory, "model.onnx").getAbsolutePath())
                    .putString("active_tokens", new File(directory, "tokens.txt").getAbsolutePath())
                    .putString("active_voices_bin", "")
                    .putString("active_language", item.type.equals("mms")
                            ? item.id.substring("mms-".length()) : item.language);
        }
        editor.apply();
    }

    public static void delete(Context context, Item item) throws IOException {
        File target = directory(context, item);
        if (item.type.equals("kokoro")) {
            target = new KokoroModelFiles(context.getFilesDir()).getDirectory();
        } else if (item.type.equals("supertonic")) {
            target = new SupertonicModelFiles(context.getFilesDir()).getDirectory();
        }
        if (target.exists()) {
            TtsEngineUtils.deleteTree(target);
        }
        SharedPreferences preferences = context.getSharedPreferences("sp1", Context.MODE_PRIVATE);
        if (item.name.equals(preferences.getString("active_model_name", null))) {
            preferences.edit().remove("active_model").remove("active_tokens")
                    .remove("active_model_type").remove("active_model_name")
                    .remove("active_voices_bin").remove("active_supertonic_dir").apply();
        }
    }

    public static Item fromDirectory(Context context, File directory) {
        String name = directory.getName();
        if (name.equals(KokoroModelFiles.DIRECTORY_NAME)) {
            return find("kokoro-multi-lang-v1_0", name, context);
        }
        if (name.equals(SupertonicModelFiles.DIRECTORY_NAME)) {
            return find("supertonic", name, context);
        }
        try {
            for (Item item : load(context)) {
                if (directory(context, item).equals(directory)) {
                    return item;
                }
            }
        } catch (IOException ignored) {
            // The built-in catalog remains usable when the optional MMS catalog is unavailable.
        }
        return customItem(name, name.replace('_', ' '));
    }

    private static Item find(String id, String fallbackName, Context context) {
        try {
            for (Item item : load(context)) {
                if (item.id.equals(id)) {
                    return item;
                }
            }
        } catch (IOException ignored) {
            // Fall through to the local model entry.
        }
        return id.equals("supertonic")
                ? new Item(id, fallbackName, "supertonic", "Multi-language", "", null)
                : new Item(id, "Kokoro Multi-Language", "kokoro", "Multi-language", "", null);
    }

    private static long sizeOf(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
        }
        return total;
    }

    public static final class Item {
        public final String id;
        public final String name;
        public final String type;
        public final String language;
        public final String size;
        public final MmsCatalog.Model mms;

        Item(String id, String name, String type, String language, String size,
             MmsCatalog.Model mms) {
            this.id = id;
            this.name = name;
            this.type = type.toLowerCase(Locale.ROOT);
            this.language = language == null ? "" : language;
            this.size = size == null ? "" : size;
            this.mms = mms;
        }
    }
}
