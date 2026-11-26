package com.builtbydeep.sovra.cloud;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public final class CloudApiKeyManager {
    public static void show(Context context) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);

        EditText fish = addField(form, context, "Fish Audio API Key", "fish_api_key", false);
        EditText groq = addField(form, context, "Groq API Key", "groq_api_key", false);
        EditText cartesia = addField(form, context, "Cartesia API Key", "cartesia_api_key", false);
        EditText gradium = addField(form, context, "Gradium AI API Key", "gradium_api_key", false);
        EditText cfAccount = addField(form, context, "Cloudflare Account ID", "cloudflare_account_id", false);
        EditText cfToken = addField(form, context, "Cloudflare API Token", "cloudflare_api_token", false);
        EditText capCutWrapper = addField(form, context, "CapCut Wrapper URL (optional)", "capcut_wrapper_url", false);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(form);
        new AlertDialog.Builder(context)
                .setTitle("Cloud TTS API Keys")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE).edit()
                            .putString("fish_api_key", fish.getText().toString().trim())
                            .putString("groq_api_key", groq.getText().toString().trim())
                            .putString("cartesia_api_key", cartesia.getText().toString().trim())
                            .putString("gradium_api_key", gradium.getText().toString().trim())
                            .putString("cloudflare_account_id", cfAccount.getText().toString().trim())
                            .putString("cloudflare_api_token", cfToken.getText().toString().trim())
                            .putString("capcut_wrapper_url", capCutWrapper.getText().toString().trim())
                            .apply();
                    Toast.makeText(context, "Cloud TTS settings saved", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static EditText addField(LinearLayout form, Context context, String label,
                                     String prefKey, boolean numeric) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(0xFFFFFFFF);
        form.addView(title);
        EditText input = new EditText(context);
        input.setText(context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE).getString(prefKey, ""));
        input.setTextColor(0xFFFFFFFF);
        input.setHint("Enter " + label);
        input.setHintTextColor(0xFF94A3B8);
        if (numeric) input.setInputType(InputType.TYPE_CLASS_NUMBER);
        else input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(input);
        return input;
    }

    public static String exportJson(Context context) throws Exception {
        JSONObject root = new JSONObject();
        android.content.SharedPreferences keys = context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE);
        String[] keyNames = {
                "fish_api_key", "groq_api_key", "cartesia_api_key", "gradium_api_key",
                "cloudflare_account_id", "cloudflare_api_token", "capcut_wrapper_url"
        };
        for (String key : keyNames) root.put(key, keys.getString(key, ""));
        android.content.SharedPreferences usage = context.getSharedPreferences("spCloudUsage", Context.MODE_PRIVATE);
        JSONObject usageJson = new JSONObject();
        for (CloudTtsProvider provider : CloudTtsRegistry.getProviders().values()) {
            JSONObject item = new JSONObject();
            item.put("characters", usage.getLong("usage_chars_" + provider.getId(), 0L));
            item.put("bytes", usage.getLong("usage_bytes_" + provider.getId(), 0L));
            usageJson.put(provider.getId(), item);
        }
        root.put("cloud_usage", usageJson);
        return root.toString(2);
    }

    public static void importJson(Context context, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        android.content.SharedPreferences.Editor keys =
                context.getSharedPreferences("spCloudKeys", Context.MODE_PRIVATE).edit();
        for (String key : new String[]{"fish_api_key", "groq_api_key", "cartesia_api_key",
                "gradium_api_key", "cloudflare_account_id", "cloudflare_api_token", "capcut_wrapper_url"}) {
            if (root.has(key)) keys.putString(key, root.optString(key));
        }
        keys.apply();
        if (root.has("cloud_usage")) {
            JSONObject usage = root.getJSONObject("cloud_usage");
            android.content.SharedPreferences.Editor usageEditor =
                    context.getSharedPreferences("spCloudUsage", Context.MODE_PRIVATE).edit();
            java.util.Iterator<String> ids = usage.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                JSONObject item = usage.optJSONObject(id);
                if (item != null) {
                    usageEditor.putLong("usage_chars_" + id, item.optLong("characters", 0L));
                    usageEditor.putLong("usage_bytes_" + id, item.optLong("bytes", 0L));
                }
            }
            usageEditor.apply();
        }
    }
}
