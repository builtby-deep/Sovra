package com.builtbydeep.sovra;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiKeysActivity extends AppCompatActivity {
    private static final int CYAN = Color.rgb(0, 229, 255);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int SURFACE = Color.rgb(26, 29, 46);
    private static final int[] PROVIDER_BADGES = {0xFF00E5FF, 0xFF8B9EFF, 0xFFFFB86B, 0xFF84E1BC};
    private static final String[] PROVIDERS = {CloudTtsClients.RIME, CloudTtsClients.SPEECHIFY,
            CloudTtsClients.ELEVENLABS, CloudTtsClients.WITAI};
    private SharedPreferences preferences;
    private GridLayout providerGrid;
    private View popupScrim;
    private View popupControls;
    private LinearLayout popupMenu;
    private ImageButton menuFab;
    private boolean popupOpen;
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<android.content.Intent> backupLauncher;
    private ActivityResultLauncher<android.content.Intent> restoreLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        backupToUri(result.getData().getData());
                    }
                });
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        restoreFromUri(result.getData().getData());
                    }
                });
        preferences = getSharedPreferences("sp1", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(15, 20, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 20, 30));

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.rgb(15, 20, 30));
        setContentView(screen);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        screen.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, Color.WHITE, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        back.setOnClickListener(view -> finish());
        TextView title = text("API Keys", 20, Color.WHITE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        View spacer = new View(this);
        toolbar.addView(spacer, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        providerGrid = new GridLayout(this);
        providerGrid.setColumnCount(2);
        providerGrid.setUseDefaultMargins(false);
        providerGrid.setPadding(0, dp(18), 0, dp(12));
        scroll.addView(providerGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addBackupPopup(screen);
        ViewCompat.setOnApplyWindowInsetsListener(screen, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPadding(dp(16) + bars.left, dp(12) + bars.top,
                    dp(16) + bars.right, 0);
            FrameLayout.LayoutParams controlsParams =
                    (FrameLayout.LayoutParams) popupControls.getLayoutParams();
            controlsParams.rightMargin = dp(20) + bars.right;
            controlsParams.bottomMargin = dp(20) + bars.bottom;
            popupControls.setLayoutParams(controlsParams);
            return insets;
        });
        ViewCompat.requestApplyInsets(screen);
        refreshProviders();
    }

    private void refreshProviders() {
        providerGrid.removeAllViews();
        for (int i = 0; i < PROVIDERS.length; i++) {
            final String providerId = PROVIDERS[i];
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1, 1f));
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            providerGrid.addView(createProviderCard(providerId, i), params);
        }
    }

    @NonNull
    private View createProviderCard(String providerId, int index) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(SURFACE);
        card.setStrokeColor(Color.rgb(42, 47, 69));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(content);

        TextView badge = text(providerBadge(providerId), 12,
                Color.rgb(15, 20, 30), Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundBackground(PROVIDER_BADGES[index], dp(13)));
        content.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView name = text(providerName(providerId), 15, Color.WHITE, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(10);
        content.addView(name, nameParams);

        boolean active = !savedKey(providerId).isEmpty();
        TextView status = text(active ? "Active" : "Not Set", 11,
                active ? CYAN : MUTED, Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(3), dp(8), dp(3));
        status.setBackground(roundBackground(active ? 0x3320E5FF : 0x33475568, dp(20)));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        content.addView(status, statusParams);

        TextView quota = text(quotaLine(providerId), 11, MUTED, Typeface.NORMAL);
        quota.setSingleLine(true);
        quota.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams quotaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quotaParams.topMargin = dp(10);
        content.addView(quota, quotaParams);
        card.setOnClickListener(view -> showProviderDialog(providerId));
        return card;
    }

    private void addBackupPopup(FrameLayout screen) {
        popupScrim = new View(this);
        popupScrim.setBackgroundColor(Color.TRANSPARENT);
        popupScrim.setVisibility(View.GONE);
        popupScrim.setAlpha(0f);
        popupScrim.setClickable(true);
        popupScrim.setOnClickListener(view -> closeBackupPopup());
        screen.addView(popupScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        controlsParams.setMargins(0, 0, dp(20), dp(20));
        screen.addView(controls, controlsParams);
        popupControls = controls;

        popupMenu = new LinearLayout(this);
        popupMenu.setOrientation(LinearLayout.VERTICAL);
        popupMenu.setGravity(Gravity.CENTER);
        popupMenu.setPadding(dp(8), dp(8), dp(8), dp(8));
        popupMenu.setBackground(outlinedRoundBackground(SURFACE, 0xFF3A415B, dp(24)));
        popupMenu.setVisibility(View.GONE);
        popupMenu.setAlpha(0f);
        popupMenu.setScaleY(0.85f);
        popupMenu.setElevation(dp(8));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuParams.bottomMargin = dp(12);
        controls.addView(popupMenu, menuParams);

        ImageButton upload = popupButton(R.drawable.ic_cloud_upload, "Backup API Keys");
        upload.setOnClickListener(view -> {
            closeBackupPopup();
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(android.content.Intent.EXTRA_TITLE, "api_keys_backup.json");
            backupLauncher.launch(intent);
        });
        popupMenu.addView(upload);

        ImageButton download = popupButton(R.drawable.ic_cloud_download, "Restore API Keys");
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        downloadParams.topMargin = dp(8);
        popupMenu.addView(download, downloadParams);
        download.setOnClickListener(view -> {
            closeBackupPopup();
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain"});
            restoreLauncher.launch(intent);
        });

        menuFab = new ImageButton(this);
        menuFab.setContentDescription("Open API key backup options");
        menuFab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        menuFab.setPadding(dp(16), dp(16), dp(16), dp(16));
        menuFab.setImageResource(R.drawable.ic_api_add);
        menuFab.setBackground(roundBackground(CYAN, dp(28)));
        menuFab.setElevation(dp(8));
        controls.addView(menuFab, new LinearLayout.LayoutParams(dp(56), dp(56)));
        menuFab.setOnClickListener(view -> {
            if (popupOpen) closeBackupPopup();
            else openBackupPopup();
        });
    }

    private ImageButton popupButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setImageResource(icon);
        button.setBackground(roundBackground(0xFF202438, dp(24)));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private void openBackupPopup() {
        popupOpen = true;
        menuFab.setImageResource(R.drawable.ic_api_close);
        menuFab.setContentDescription("Close API key backup options");
        popupScrim.setVisibility(View.VISIBLE);
        popupScrim.animate().alpha(1f).setDuration(160).start();
        popupMenu.setVisibility(View.VISIBLE);
        popupMenu.animate().alpha(1f).scaleY(1f).setDuration(180).start();
    }

    private void closeBackupPopup() {
        if (!popupOpen) return;
        popupOpen = false;
        menuFab.setImageResource(R.drawable.ic_api_add);
        menuFab.setContentDescription("Open API key backup options");
        popupMenu.animate().alpha(0f).scaleY(0.85f).setDuration(140)
                .withEndAction(() -> popupMenu.setVisibility(View.GONE)).start();
        popupScrim.animate().alpha(0f).setDuration(140)
                .withEndAction(() -> popupScrim.setVisibility(View.GONE)).start();
    }

    private void backupToUri(Uri uri) {
        backupExecutor.execute(() -> {
            try {
                JSONObject backup = createBackup();
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Unable to open the backup destination.");
                    output.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                runOnUiThread(() -> Toast.makeText(this, "API keys backed up successfully",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                android.util.Log.e("ApiKeysActivity", "Unable to back up API keys.", error);
                runOnUiThread(() -> Toast.makeText(this, "Unable to back up API keys",
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private JSONObject createBackup() throws JSONException {
        JSONObject providerData = new JSONObject();
        for (String providerId : PROVIDERS) {
            JSONObject data = new JSONObject();
            data.put("apiKey", preferences.getString(
                    CloudTtsClients.getApiKeyPreference(providerId), ""));
            data.put("usageMonth", currentMonth());
            data.put("usageChars", CloudStudioAdapter.getMonthlyUsage(preferences, providerId));
            providerData.put(providerId, data);
        }
        JSONObject backup = new JSONObject();
        backup.put("version", 1);
        backup.put("providers", providerData);
        return backup;
    }

    private void restoreFromUri(Uri uri) {
        backupExecutor.execute(() -> {
            try {
                JSONObject providers = readAndValidateBackup(uri);
                SharedPreferences.Editor editor = preferences.edit();
                int restored = 0;
                for (String providerId : PROVIDERS) {
                    if (!providers.has(providerId)) continue;
                    JSONObject provider = providers.getJSONObject(providerId);
                    String apiKey = provider.getString("apiKey").trim();
                    if (apiKey.isEmpty()) continue;
                    editor.putString(CloudTtsClients.getApiKeyPreference(providerId), apiKey);
                    if (provider.has("usageMonth")) {
                        editor.putString("cloud_usage_month_" + providerId,
                                provider.getString("usageMonth"));
                        editor.putInt("cloud_usage_chars_" + providerId,
                                provider.getInt("usageChars"));
                    }
                    restored++;
                }
                editor.apply();
                int restoredCount = restored;
                runOnUiThread(() -> {
                    refreshProviders();
                    Toast.makeText(this, "Restored and activated " + restoredCount + " API keys",
                            Toast.LENGTH_SHORT).show();
                });
            } catch (InvalidBackupException error) {
                runOnUiThread(() -> Toast.makeText(this, "Invalid backup file",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception error) {
                android.util.Log.e("ApiKeysActivity", "Unable to restore API keys.", error);
                runOnUiThread(() -> Toast.makeText(this, "Unable to restore API keys",
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    private JSONObject readAndValidateBackup(Uri uri) throws IOException, InvalidBackupException {
        StringBuilder json = new StringBuilder();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Unable to open the backup file.");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (json.length() + count > 1_000_000) throw new InvalidBackupException();
                    json.append(buffer, 0, count);
                }
            }
        }

        try {
            JSONObject root = new JSONObject(json.toString());
            Object version = root.get("version");
            if (!(version instanceof Number) || ((Number) version).intValue() != 1
                    || ((Number) version).doubleValue() != 1.0) {
                throw new InvalidBackupException();
            }
            JSONObject providers = root.getJSONObject("providers");
            for (String providerId : PROVIDERS) {
                if (!providers.has(providerId)) continue;
                JSONObject provider = providers.getJSONObject(providerId);
                if (!(provider.get("apiKey") instanceof String)) {
                    throw new InvalidBackupException();
                }
                if (provider.has("usageMonth")) {
                    if (!(provider.get("usageMonth") instanceof String)
                            || !provider.getString("usageMonth").matches(
                            "\\d{4}-(0[1-9]|1[0-2])")) {
                        throw new InvalidBackupException();
                    }
                    if (!provider.has("usageChars")) throw new InvalidBackupException();
                    Object usage = provider.get("usageChars");
                    if (!(usage instanceof Number) || ((Number) usage).intValue() < 0
                            || ((Number) usage).doubleValue() != ((Number) usage).intValue()) {
                        throw new InvalidBackupException();
                    }
                } else if (provider.has("usageChars")) {
                    throw new InvalidBackupException();
                }
            }
            return providers;
        } catch (JSONException error) {
            throw new InvalidBackupException(error);
        }
    }

    private String currentMonth() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d", calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private static final class InvalidBackupException extends Exception {
        InvalidBackupException() {}

        InvalidBackupException(Throwable cause) {
            super(cause);
        }
    }

    private void showProviderDialog(String providerId) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(8), dp(4), 0);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(providerName(providerId), 18, Color.WHITE, Typeface.BOLD);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        boolean active = !savedKey(providerId).isEmpty();
        TextView status = text(active ? "Active" : "Not Set", 12,
                active ? CYAN : MUTED, Typeface.BOLD);
        heading.addView(status);
        TextView close = text("×", 24, MUTED, Typeface.NORMAL);
        close.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        closeParams.leftMargin = dp(8);
        heading.addView(close, closeParams);
        content.addView(heading);

        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint(providerId.equals(CloudTtsClients.WITAI)
                ? "Server access token" : "API key");
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.rgb(100, 116, 139));
        keyInput.setBackground(roundBackground(Color.rgb(15, 20, 30), dp(12)));
        keyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        inputParams.topMargin = dp(18);
        content.addView(keyInput, inputParams);
        keyInput.setText(savedKey(providerId));

        LinearLayout usageRow = new LinearLayout(this);
        usageRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView usage = text(usageLine(providerId), 12, MUTED, Typeface.NORMAL);
        usageRow.addView(usage, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView reset = text("Reset", 12, CYAN, Typeface.BOLD);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(12), dp(7), dp(12), dp(7));
        reset.setBackground(outlineBackground(0x9900E5FF, dp(14)));
        usageRow.addView(reset);
        LinearLayout.LayoutParams usageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        usageParams.topMargin = dp(14);
        content.addView(usageRow, usageParams);

        Button activate = new Button(this);
        activate.setText("Activate");
        activate.setTransformationMethod(null);
        activate.setTextColor(Color.rgb(15, 20, 30));
        activate.setTextSize(15);
        activate.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activate.setBackground(roundBackground(CYAN, dp(12)));
        LinearLayout.LayoutParams activateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        activateParams.topMargin = dp(20);
        content.addView(activate, activateParams);

        TextView cancel = text("Cancel", 13, MUTED, Typeface.NORMAL);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(12), 0, dp(4));
        content.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        close.setOnClickListener(view -> dialog.dismiss());
        cancel.setOnClickListener(view -> dialog.dismiss());
        reset.setOnClickListener(view -> {
            CloudStudioAdapter.resetMonthlyUsage(preferences, providerId);
            usage.setText(usageLine(providerId));
            Toast.makeText(this, "Monthly usage reset.", Toast.LENGTH_SHORT).show();
        });
        activate.setOnClickListener(view -> {
            String key = keyInput.getText().toString().trim();
            if (key.isEmpty()) {
                keyInput.setError("Enter a key to activate this provider.");
                return;
            }
            preferences.edit().putString(CloudTtsClients.getApiKeyPreference(providerId), key)
                    .apply();
            Toast.makeText(this, providerName(providerId) + " activated.",
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshProviders();
        });
        dialog.show();
    }

    private String quotaLine(String providerId) {
        if (CloudTtsClients.WITAI.equals(providerId)) return "Unlimited";
        int limit = CloudTtsClients.monthlyCharacterLimit(providerId, selectedModel(providerId));
        int used = CloudStudioAdapter.getMonthlyUsage(preferences, providerId);
        return String.format(Locale.US, "%,d / %s", used, compact(limit));
    }

    private String usageLine(String providerId) {
        if (CloudTtsClients.WITAI.equals(providerId)) {
            return "Used: " + String.format(Locale.US, "%,d", CloudStudioAdapter
                    .getMonthlyUsage(preferences, providerId)) + " chars • Unlimited (250/chunk)";
        }
        int limit = CloudTtsClients.monthlyCharacterLimit(providerId, selectedModel(providerId));
        int used = CloudStudioAdapter.getMonthlyUsage(preferences, providerId);
        return "Used: " + String.format(Locale.US, "%,d / %,d chars", used, limit);
    }

    private String selectedModel(String providerId) {
        return preferences.getString(CloudTtsClients.getSelectedModelPreference(providerId),
                CloudTtsClients.defaultModel(providerId));
    }

    private String savedKey(String providerId) {
        return preferences.getString(CloudTtsClients.getApiKeyPreference(providerId), "").trim();
    }

    private static String compact(int value) {
        if (value >= 1000000) return String.format(Locale.US, "%.2fM", value / 1000000f);
        if (value >= 1000) return String.format(Locale.US, "%dK", value / 1000);
        return String.valueOf(value);
    }

    private static String providerName(String id) {
        if (CloudTtsClients.RIME.equals(id)) return "Rime AI";
        if (CloudTtsClients.SPEECHIFY.equals(id)) return "SpeechifyAI";
        if (CloudTtsClients.ELEVENLABS.equals(id)) return "ElevenLabs";
        return "Wit.ai";
    }

    private static String providerBadge(String id) {
        if (CloudTtsClients.RIME.equals(id)) return "R";
        if (CloudTtsClients.SPEECHIFY.equals(id)) return "S";
        if (CloudTtsClients.ELEVENLABS.equals(id)) return "E";
        return "W";
    }

    private GradientDrawable roundBackground(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private GradientDrawable outlineBackground(int strokeColor, int radius) {
        GradientDrawable background = roundBackground(Color.TRANSPARENT, radius);
        background.setStroke(dp(1), strokeColor);
        return background;
    }

    private GradientDrawable outlinedRoundBackground(int color, int strokeColor, int radius) {
        GradientDrawable background = roundBackground(color, radius);
        background.setStroke(dp(1), strokeColor);
        return background;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        backupExecutor.shutdown();
        super.onDestroy();
    }
}
