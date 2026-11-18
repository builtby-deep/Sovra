package com.builtbydeep.sovra.system;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class DebugActivity extends Activity {
    public static final String EXTRA_STACK_TRACE =
            "com.builtbydeep.sovra.extra.STACK_TRACE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String suppliedTrace = getIntent().getStringExtra(EXTRA_STACK_TRACE);
        final String stackTrace = suppliedTrace == null || suppliedTrace.trim().isEmpty()
                ? "No stack trace was provided." : suppliedTrace;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView heading = new TextView(this);
        heading.setText("Sovra encountered an error");
        heading.setTextSize(20);
        root.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        TextView details = new TextView(this);
        details.setText(stackTrace);
        details.setTextIsSelectable(true);
        details.setTypeface(android.graphics.Typeface.MONOSPACE);
        details.setPadding(0, dp(16), 0, dp(16));
        scrollView.addView(details);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        detailsParams.topMargin = dp(12);
        root.addView(scrollView, detailsParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button copy = new Button(this);
        copy.setText("Copy");
        copy.setOnClickListener(view -> copyStackTrace(stackTrace));
        actions.addView(copy);

        Button restart = new Button(this);
        restart.setText("Restart");
        restart.setOnClickListener(view -> restartApplication());
        actions.addView(restart);
        root.addView(actions);
        setContentView(root);
    }

    private void copyStackTrace(String stackTrace) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Sovra error", stackTrace));
        Toast.makeText(this, "Stack trace copied", Toast.LENGTH_SHORT).show();
    }

    private void restartApplication() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) {
            Toast.makeText(this, "Unable to restart Sovra", Toast.LENGTH_LONG).show();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launchIntent);
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
