package com.builtbydeep.sovra;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public final class ApiKeysActivity extends AppCompatActivity {
    private static final int CYAN = Color.rgb(0, 229, 255);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int[] PROVIDER_BADGES = {0xFF00E5FF, 0xFF8B9EFF, 0xFFFFB86B, 0xFF84E1BC};
    private SharedPreferences preferences;
    private GridLayout providerGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("sp1", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(15, 20, 30));
        getWindow().setNavigationBarColor(Color.rgb(15, 20, 30));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 20, 30));
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        setContentView(root);

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
        providerGrid.setPadding(0, dp(18), 0, 0);
        scroll.addView(providerGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshProviders();
    }

    private void refreshProviders() {
        providerGrid.removeAllViews();
        String[] providers = {CloudTtsClients.RIME, CloudTtsClients.SPEECHIFY,
                CloudTtsClients.ELEVENLABS, CloudTtsClients.WITAI};
        for (int i = 0; i < providers.length; i++) {
            final String providerId = providers[i];
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2, 1, 1f), GridLayout.spec(i % 2, 1, 1f));
            params.width = 0;
            params.height = dp(166);
            params.setMargins(dp(5), dp(5), dp(5), dp(5));
            providerGrid.addView(createProviderCard(providerId, i), params);
        }
    }

    @NonNull
    private View createProviderCard(String providerId, int index) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(26, 29, 46));
        card.setStrokeColor(Color.rgb(42, 47, 69));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(12), dp(12));
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
}
