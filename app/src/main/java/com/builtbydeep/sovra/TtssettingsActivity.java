package com.builtbydeep.sovra;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.builtbydeep.sovra.system.SovraTtsService;
import com.builtbydeep.sovra.system.TtsDefaultHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TtssettingsActivity extends AppCompatActivity {
    private Spinner languageSpinner;
    private Spinner modelSpinner;
    private Spinner voiceSpinner;
    private final List<VoiceChoice> voices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tts_settings);
        languageSpinner = findViewById(R.id.tts_language);
        modelSpinner = findViewById(R.id.tts_model_type);
        voiceSpinner = findViewById(R.id.tts_voice);
        List<String> languages = new ArrayList<>();
        for (String tag : SovraTtsService.getAvailableLocaleTags()) {
            languages.add(tag);
        }
        setItems(languageSpinner, languages);
        setItems(modelSpinner, java.util.Arrays.asList("kokoro", "supertonic", "vits", "mms"));
        languageSpinner.setOnItemSelectedListener(new SpinnerAction(this::refreshVoices));
        modelSpinner.setOnItemSelectedListener(new SpinnerAction(this::refreshVoices));
        refreshVoices();

        Button save = findViewById(R.id.save_tts_default);
        save.setOnClickListener(view -> saveDefault());
        findViewById(R.id.clear_tts_default).setOnClickListener(view -> {
            String language = selected(languageSpinner);
            if (language != null) {
                TtsDefaultHelper.remove(this, language);
                Toast.makeText(this, "Language default cleared", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshVoices() {
        String language = selected(languageSpinner);
        String type = selected(modelSpinner);
        voices.clear();
        if (language == null || type == null) return;
        String primary = Locale.forLanguageTag(language).getLanguage();
        if (type.equals("kokoro")) {
            for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
                if (Locale.forLanguageTag(voice.language).getLanguage().equalsIgnoreCase(primary)) {
                    voices.add(new VoiceChoice(voice.displayName, voice.id));
                }
            }
        } else if (type.equals("supertonic")) {
            String[] profiles = {"F1 - Calm, Clear", "F2 - Bright, Professional",
                    "F3 - Soft, Expressive", "F4 - Energetic, Versatile", "F5 - Airy, Warm",
                    "M1 - Lively, Upbeat", "M2 - Warm, Narrative", "M3 - Deep, Authoritative",
                    "M4 - Measured, Resonant", "M5 - Dynamic, Dramatic"};
            for (int i = 0; i < profiles.length; i++) voices.add(new VoiceChoice(profiles[i], i));
        } else {
            for (PiperModelCatalog.Model model : PiperModelCatalog.getModels()) {
                if (model.language.toLowerCase(Locale.ROOT).contains(primary)) {
                    voices.add(new VoiceChoice(model.name, 0));
                }
            }
            String activeType = getSharedPreferences("sp1", MODE_PRIVATE)
                    .getString("active_model_type", "");
            if (activeType != null && activeType.equals(type)) {
                voices.add(new VoiceChoice(getSharedPreferences("sp1", MODE_PRIVATE)
                        .getString("active_model_name", "Active model"), 0));
            }
            if (voices.isEmpty()) voices.add(new VoiceChoice("Default", 0));
        }
        List<String> labels = new ArrayList<>();
        for (VoiceChoice choice : voices) labels.add(choice.name);
        setItems(voiceSpinner, labels);
    }

    private void saveDefault() {
        String language = selected(languageSpinner);
        String type = selected(modelSpinner);
        if (language == null || type == null || voices.isEmpty()) return;
        int speaker = voices.get(Math.max(0, voiceSpinner.getSelectedItemPosition())).speakerId;
        File model = null;
        File tokens = null;
        File voicesFile = null;
        String modelDir = null;
        if (type.equals("kokoro")) {
            KokoroModelFiles files = new KokoroModelFiles(getFilesDir());
            if (!files.isComplete()) {
                Toast.makeText(this, "Download Kokoro before saving this default",
                        Toast.LENGTH_LONG).show();
                return;
            }
            model = files.getModel();
            tokens = files.getTokens();
            voicesFile = files.getVoices();
        } else if (type.equals("supertonic")) {
            SupertonicModelFiles files = new SupertonicModelFiles(getFilesDir());
            if (!files.isComplete()) {
                Toast.makeText(this, "Download Supertonic before saving this default",
                        Toast.LENGTH_LONG).show();
                return;
            }
            modelDir = files.getDirectory().getAbsolutePath();
        } else {
            String activeType = getSharedPreferences("sp1", MODE_PRIVATE)
                    .getString("active_model_type", "");
            String activeLanguage = getSharedPreferences("sp1", MODE_PRIVATE)
                    .getString("active_language", "");
            if (activeType == null || !activeType.equals(type)
                    || (type.equals("mms") && !language.equalsIgnoreCase(activeLanguage))) {
                Toast.makeText(this, "Activate a matching installed model first",
                        Toast.LENGTH_LONG).show();
                return;
            }
            String modelPath = getSharedPreferences("sp1", MODE_PRIVATE)
                    .getString("active_model", null);
            String tokensPath = getSharedPreferences("sp1", MODE_PRIVATE)
                    .getString("active_tokens", null);
            if (modelPath == null || tokensPath == null) return;
            model = new File(modelPath);
            tokens = new File(tokensPath);
        }
        TtsDefaultHelper.save(this, language, new TtsDefaultHelper.TtsDefault(
                type, model == null ? null : model.getAbsolutePath(),
                tokens == null ? null : tokens.getAbsolutePath(),
                voicesFile == null ? null : voicesFile.getAbsolutePath(),
                modelDir, speaker));
        Toast.makeText(this, "System voice default saved", Toast.LENGTH_SHORT).show();
    }

    private static String selected(Spinner spinner) {
        return spinner.getSelectedItem() == null ? null : spinner.getSelectedItem().toString();
    }

    private static void setItems(Spinner spinner, List<String> items) {
        spinner.setAdapter(new ArrayAdapter<>(spinner.getContext(),
                android.R.layout.simple_spinner_dropdown_item, items));
    }

    private interface ItemSelected {
        void onSelected();
    }

    private static final class SpinnerAction implements android.widget.AdapterView.OnItemSelectedListener {
        private final ItemSelected callback;

        SpinnerAction(ItemSelected callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                   int position, long id) {
            callback.onSelected();
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }

    private static final class VoiceChoice {
        final String name;
        final int speakerId;

        VoiceChoice(String name, int speakerId) {
            this.name = name;
            this.speakerId = speakerId;
        }
    }
}
