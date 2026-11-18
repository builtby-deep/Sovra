package com.builtbydeep.sovra;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SettingFragmentActivity extends Fragment {
    private android.content.SharedPreferences preferences;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        preferences = requireContext().getSharedPreferences("sp3", 0);
        bindToggle(view, R.id.smart_punct, "smart_punct");
        bindToggle(view, R.id.emotion_tags, "emotion_tags");
        bindToggle(view, R.id.multi_speaker_tags, "multi_speaker_tags");
        bindToggle(view, R.id.mms_models_enabled, "mms_models_enabled");
        bindSlider(view, R.id.voice_pitch, R.id.pitch_value, "voice_pitch", 0.25f, 2.0f);
        bindSlider(view, R.id.voice_speed, R.id.speed_value, "voice_speed", 0.25f, 2.0f);
        bindSlider(view, R.id.silence_scale, R.id.silence_value, "silence_scale", 0.0f, 1.0f);

        Spinner style = view.findViewById(R.id.voice_style);
        List<String> styles = Arrays.asList(
                "Natural", "Balanced", "Expressive");
        style.setAdapter(new SovraSpinnerAdapter(requireContext(), styles));
        style.setSelection(Math.max(0, Math.min(2, preferences.getInt("voice_style", 0))));
        int[] previousStyle = {style.getSelectedItemPosition()};
        style.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View selectedView,
                                       int position, long id) {
                if (position == previousStyle[0]) return;
                previousStyle[0] = position;
                preferences.edit().putInt("voice_style", position).apply();
                float[] pitch = {0.33f, 0.667f, 0.85f};
                float[] speed = {0.50f, 0.80f, 0.90f};
                preferences.edit().putFloat("voice_pitch", pitch[position])
                        .putFloat("voice_speed", speed[position]).apply();
                setSlider(view, R.id.voice_pitch, R.id.pitch_value,
                        pitch[position], 0.25f, 2.0f);
                setSlider(view, R.id.voice_speed, R.id.speed_value,
                        speed[position], 0.25f, 2.0f);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        refreshStorage(view.findViewById(R.id.storage_usage));
        view.findViewById(R.id.android_tts_settings).setOnClickListener(v -> openSystemTtsSettings());
        view.findViewById(R.id.repository_link).setOnClickListener(v -> openRepository());
        view.findViewById(R.id.check_update).setOnClickListener(v ->
                openUrl("https://github.com/builtby-deep/Sovra/releases"));
        view.findViewById(R.id.privacy_policy).setOnClickListener(v ->
                openUrl("https://github.com/builtby-deep/Sovra#privacy"));
        view.findViewById(R.id.feedback).setOnClickListener(v -> sendFeedback());
        TextView versionView = view.findViewById(R.id.app_version);
        try {
            String version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            versionView.setText("App Version  " + version);
        } catch (android.content.pm.PackageManager.NameNotFoundException exception) {
            versionView.setText("App Version unavailable");
        }
        view.findViewById(R.id.factory_reset).setOnClickListener(v -> confirmFactoryReset(view));
    }

    private void bindToggle(View root, int viewId, String key) {
        SwitchMaterial toggle = root.findViewById(viewId);
        toggle.setChecked(preferences.getBoolean(key, key.equals("mms_models_enabled")));
        toggle.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(key, checked).apply());
    }

    private void bindSlider(
            View root, int sliderId, int valueId, String key, float min, float max) {
        float value = preferences.getFloat(key, key.equals("silence_scale") ? 0.5f : 1.0f);
        setSlider(root, sliderId, valueId, value, min, max);
        SeekBar seekBar = root.findViewById(sliderId);
        TextView valueView = root.findViewById(valueId);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float current = sliderValue(progress, min, max);
                valueView.setText(String.format(Locale.ROOT, "%.2f", current));
                if (fromUser) preferences.edit().putFloat(key, current).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
    }

    private static void setSlider(
            View root, int sliderId, int valueId, float value, float min, float max) {
        SeekBar slider = root.findViewById(sliderId);
        slider.setProgress(progressFor(value, min, max));
        ((TextView) root.findViewById(valueId)).setText(
                String.format(Locale.ROOT, "%.2f", value));
    }

    private static float sliderValue(int progress, float min, float max) {
        if (min == 0.25f && max == 2.0f) {
            return progress <= 50 ? 0.25f + progress * 0.015f
                    : 1.0f + (progress - 50) * 0.02f;
        }
        return min + progress / 100f * (max - min);
    }

    private static int progressFor(float value, float min, float max) {
        float clamped = Math.max(min, Math.min(max, value));
        if (min == 0.25f && max == 2.0f) {
            return clamped <= 1.0f
                    ? Math.round((clamped - 0.25f) / 0.015f)
                    : 50 + Math.round((clamped - 1.0f) / 0.02f);
        }
        return Math.round((clamped - min) / (max - min) * 100f);
    }

    private void openSystemTtsSettings() {
        Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(requireContext(), "System TTS settings are unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openRepository() {
        openUrl("https://github.com/builtby-deep/Sovra");
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(requireContext(), "No browser is available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void sendFeedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_SUBJECT, "Sovra feedback");
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(requireContext(), "No email app is available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmFactoryReset(View root) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear all cache?")
                .setMessage("Remove downloaded models and local audio history? This also resets Sovra preferences.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear cache", (dialog, which) -> {
                    for (String name : Arrays.asList("sp1", "sp2", "sp3", "sp5")) {
                        requireContext().getSharedPreferences(name, 0).edit().clear().apply();
                    }
                    try {
                        TtsEngineUtils.deleteTree(new File(requireContext().getFilesDir(), "models"));
                        TtsEngineUtils.deleteTree(new File(requireContext().getFilesDir(), "PiperModels"));
                        TtsEngineUtils.deleteTree(new File(requireContext().getFilesDir(), "espeak-ng-data"));
                        TtsEngineUtils.deleteTree(new File(requireContext().getFilesDir(), "tts_frontend_data"));
                        refreshStorage(root.findViewById(R.id.storage_usage));
                        Toast.makeText(requireContext(), "Sovra data reset", Toast.LENGTH_SHORT).show();
                    } catch (java.io.IOException exception) {
                        Toast.makeText(requireContext(), exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void refreshStorage(TextView view) {
        File filesDirectory = requireContext().getFilesDir();
        long bytes = sizeOf(new File(filesDirectory, "models"))
                + sizeOf(new File(filesDirectory, "PiperModels"));
        view.setText(String.format(Locale.ROOT, "ONNX Models Size: %.1f MB",
                bytes / (1024.0 * 1024.0)));
    }

    private static long sizeOf(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long bytes = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) bytes += sizeOf(child);
        }
        return bytes;
    }

}
