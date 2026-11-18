package com.builtbydeep.sovra.system;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public final class TtsDefaultHelper {
    public static final String PREFERENCES_NAME = "sp5";
    private static final String KEY_PREFIX = "sys_tts_";
    private static final Gson GSON = new Gson();

    private TtsDefaultHelper() {
    }

    public static void save(Context context, String language, TtsDefault value) {
        if (context == null || value == null) {
            throw new IllegalArgumentException("Context and TTS default are required");
        }
        if (value.modelType == null || value.modelType.trim().isEmpty()) {
            throw new IllegalArgumentException("TTS default model_type must not be empty");
        }
        boolean saved = preferences(context).edit()
                .putString(preferenceKey(language), GSON.toJson(value))
                .commit();
        if (!saved) {
            throw new IllegalStateException("Unable to persist the system TTS default");
        }
    }

    public static TtsDefault get(Context context, String language) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        String json = preferences(context).getString(preferenceKey(language), null);
        if (json == null) {
            return null;
        }
        try {
            TtsDefault value = GSON.fromJson(json, TtsDefault.class);
            if (value == null || value.modelType == null || value.modelType.trim().isEmpty()) {
                throw new IllegalStateException("Stored system TTS default is missing model_type");
            }
            return value;
        } catch (JsonParseException exception) {
            throw new IllegalStateException("Stored system TTS default is invalid JSON", exception);
        }
    }

    public static boolean remove(Context context, String language) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        boolean removed = preferences(context).edit()
                .remove(preferenceKey(language))
                .commit();
        if (!removed) {
            throw new IllegalStateException("Unable to remove the system TTS default");
        }
        return true;
    }

    public static String preferenceKey(String language) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language must not be empty");
        }
        String normalized = language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z]{2,8}(-[a-z0-9]{1,8})*")) {
            throw new IllegalArgumentException("Invalid language tag: " + language);
        }
        return KEY_PREFIX + normalized;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static final class TtsDefault {
        @SerializedName("model_type")
        public String modelType;

        @SerializedName("onnx_path")
        public String onnxPath;

        @SerializedName("tokens_path")
        public String tokensPath;

        @SerializedName("voices_bin_path")
        public String voicesBinPath;

        @SerializedName("model_dir")
        public String modelDir;

        @SerializedName("speaker_id")
        public int speakerId;

        public TtsDefault() {
        }

        public TtsDefault(
                String modelType,
                String onnxPath,
                String tokensPath,
                String voicesBinPath,
                String modelDir,
                int speakerId) {
            this.modelType = modelType;
            this.onnxPath = onnxPath;
            this.tokensPath = tokensPath;
            this.voicesBinPath = voicesBinPath;
            this.modelDir = modelDir;
            this.speakerId = speakerId;
        }
    }
}
