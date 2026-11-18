package com.builtbydeep.sovra;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GenerateFragmentActivity extends Fragment {
    private static final String[] KOKORO_REGIONS = {
            "en-US", "en-GB", "es", "fr", "hi", "it", "ja", "pt", "zh"
    };
    private static final String[] SUPERTONIC_PROFILES = {
            "F1 - Calm, Clear", "F2 - Bright, Professional", "F3 - Soft, Expressive",
            "F4 - Energetic, Versatile", "F5 - Airy, Warm", "M1 - Lively, Upbeat",
            "M2 - Warm, Narrative", "M3 - Deep, Authoritative",
            "M4 - Measured, Resonant", "M5 - Dynamic, Dramatic"
    };

    private final ExecutorService synthesisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SovraModels.Item> installedModels = new ArrayList<>();
    private final List<KokoroVoiceHelper.Voice> kokoroVoices = new ArrayList<>();
    private TextInputEditText editor;
    private Spinner modelSelector;
    private Spinner regionSelector;
    private Spinner voiceSelector;
    private Spinner supertonicVoiceSelector;
    private Spinner stepsSelector;
    private View supertonicOptions;
    private ProgressBar progress;
    private TextView synthesisStatus;
    private WaveformView waveform;
    private MaterialButton playButton;
    private byte[] generatedPcm;
    private int sampleRate;
    private AudioTrack audioTrack;
    private boolean suppressSelectionEvents = true;
    private boolean awaitingTokensImport;

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onDocumentSelected);
    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) launchSpeechRecognition();
                        else showMessage("Microphone permission is required for speech input");
                    });
    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK
                        && result.getData() != null) {
                    ArrayList<String> results = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty() && editor != null) {
                        editor.append(results.get(0));
                    }
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_generate, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        editor = view.findViewById(R.id.text_editor);
        modelSelector = view.findViewById(R.id.model_selector);
        regionSelector = view.findViewById(R.id.kokoro_region_selector);
        voiceSelector = view.findViewById(R.id.voice_selector);
        supertonicVoiceSelector = view.findViewById(R.id.supertonic_voice_selector);
        stepsSelector = view.findViewById(R.id.supertonic_steps_selector);
        supertonicOptions = view.findViewById(R.id.supertonic_options);
        progress = view.findViewById(R.id.synthesis_progress);
        synthesisStatus = view.findViewById(R.id.synthesis_status);
        waveform = view.findViewById(R.id.waveform);
        playButton = view.findViewById(R.id.play_button);

        view.findViewById(R.id.history_button).setOnClickListener(v ->
                ((com.google.android.material.bottomnavigation.BottomNavigationView)
                        requireActivity().findViewById(R.id.bottom_navigation))
                        .setSelectedItemId(R.id.nav_library));
        view.findViewById(R.id.reader_button).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EpubReaderActivity.class)));
        view.findViewById(R.id.import_button).setOnClickListener(v ->
                importLauncher.launch(new String[]{"text/plain", "application/pdf"}));
        view.findViewById(R.id.mic_button).setOnClickListener(v ->
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO));
        view.findViewById(R.id.paste_button).setOnClickListener(v -> pasteFromClipboard());
        view.findViewById(R.id.clear_button).setOnClickListener(v -> editor.setText(""));
        view.findViewById(R.id.generate_button).setOnClickListener(v -> generate());
        view.findViewById(R.id.export_button).setOnClickListener(v -> exportWav());
        playButton.setOnClickListener(v -> togglePlayback());
        waveform.setVisibility(View.GONE);
        playButton.setVisibility(View.GONE);
        waveform.setSeekListener(this::seekTo);

        setItems(regionSelector, Arrays.asList(KOKORO_REGIONS));
        int regionPosition = Arrays.asList(KOKORO_REGIONS).indexOf(currentRegion());
        regionSelector.setSelection(Math.max(0, regionPosition));
        List<String> profiles = Arrays.asList(SUPERTONIC_PROFILES);
        setItems(supertonicVoiceSelector, profiles);
        List<String> steps = new ArrayList<>();
        for (int step = 1; step <= 16; step++) steps.add(Integer.toString(step));
        setItems(stepsSelector, steps);
        stepsSelector.setSelection(15);
        stepsSelector.setOnItemSelectedListener(new SelectionListener(position ->
                requireContext().getSharedPreferences("sp3", 0).edit()
                        .putInt("supertonic_num_steps", position + 1).apply()));
        supertonicVoiceSelector.setOnItemSelectedListener(new SelectionListener(position ->
                requireContext().getSharedPreferences("sp1", 0).edit()
                        .putInt("active_supertonic_speaker", position).apply()));
        regionSelector.setOnItemSelectedListener(new SelectionListener(position -> {
            if (!suppressSelectionEvents) populateKokoroVoices(KOKORO_REGIONS[position]);
        }));
        modelSelector.setOnItemSelectedListener(new SelectionListener(position -> {
            if (suppressSelectionEvents || position < 0 || position >= installedModels.size()) return;
            try {
                SovraModels.activate(requireContext(), installedModels.get(position));
                updateVoiceSelectors();
            } catch (IOException exception) {
                showMessage(exception.getMessage());
            }
        }));
        loadInstalledModels();
        populateKokoroVoices(currentRegion());
        updateVoiceSelectors();
        if (state == null) importSharedText();
        mainHandler.post(() -> suppressSelectionEvents = false);
    }

    @Override
    public void onDestroy() {
        synthesisExecutor.shutdownNow();
        releaseAudioTrack();
        super.onDestroy();
    }

    private void loadInstalledModels() {
        installedModels.clear();
        try {
            java.io.File models = new java.io.File(requireContext().getFilesDir(), "models");
            java.io.File[] children = models.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    if (child.isDirectory()) {
                        SovraModels.Item item = SovraModels.fromDirectory(requireContext(), child);
                        if (SovraModels.isInstalled(requireContext(), item)) installedModels.add(item);
                    }
                }
            }
            java.io.File custom = new java.io.File(requireContext().getFilesDir(), "PiperModels");
            children = custom.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    if (child.isDirectory()) {
                        SovraModels.Item item = SovraModels.fromDirectory(requireContext(), child);
                        if (SovraModels.isInstalled(requireContext(), item)) installedModels.add(item);
                    }
                }
            }
        } catch (RuntimeException exception) {
            showMessage("Unable to load installed models: " + exception.getMessage());
        }
        List<String> names = new ArrayList<>();
        for (SovraModels.Item item : installedModels) {
            names.add(item.name + " · " + item.type);
        }
        if (names.isEmpty()) names.add("No installed models — open Models");
        setItems(modelSelector, names);
        String activeName = requireContext().getSharedPreferences("sp1", 0)
                .getString("active_model_name", "");
        for (int i = 0; i < installedModels.size(); i++) {
            if (installedModels.get(i).name.equals(activeName)) {
                modelSelector.setSelection(i);
                break;
            }
        }
    }

    private void updateVoiceSelectors() {
        String type = requireContext().getSharedPreferences("sp1", 0)
                .getString("active_model_type", "");
        boolean kokoro = "kokoro".equals(type);
        boolean supertonic = "supertonic".equals(type);
        regionSelector.setVisibility(kokoro ? View.VISIBLE : View.GONE);
        voiceSelector.setVisibility(supertonic ? View.GONE : View.VISIBLE);
        supertonicOptions.setVisibility(supertonic ? View.VISIBLE : View.GONE);
        voiceSelector.setEnabled(kokoro);
        if (!kokoro && !supertonic) {
            voiceSelector.setOnItemSelectedListener(null);
            setItems(voiceSelector, Arrays.asList("Default voice"));
        }
        if (kokoro) populateKokoroVoices(currentRegion());
        if (supertonic) {
            int speaker = requireContext().getSharedPreferences("sp1", 0)
                    .getInt("active_supertonic_speaker", 0);
            supertonicVoiceSelector.setSelection(Math.max(0, Math.min(9, speaker)));
            int steps = requireContext().getSharedPreferences("sp3", 0)
                    .getInt("supertonic_num_steps", 16);
            stepsSelector.setSelection(Math.max(0, Math.min(15, steps - 1)));
        }
    }

    private String currentRegion() {
        String active = requireContext().getSharedPreferences("sp1", 0)
                .getString("active_language", null);
        for (String region : KOKORO_REGIONS) {
            if (active != null && region.equalsIgnoreCase(active)) return region;
        }
        int speaker = requireContext().getSharedPreferences("sp1", 0)
                .getInt("active_kokoro_speaker", KokoroVoiceHelper.DEFAULT_SPEAKER_ID);
        return KokoroVoiceHelper.getVoice(speaker).language;
    }

    private void populateKokoroVoices(String region) {
        kokoroVoices.clear();
        List<String> labels = new ArrayList<>();
        for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
            if (voice.language.equalsIgnoreCase(region)
                    || (region.length() == 2 && voice.language.equalsIgnoreCase(region))) {
                kokoroVoices.add(voice);
                labels.add(voice.displayName + " · " + voice.gender);
            }
        }
        if (labels.isEmpty()) {
            for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
                if (voice.language.equalsIgnoreCase("en-US")) {
                    kokoroVoices.add(voice);
                    labels.add(voice.displayName + " · " + voice.gender);
                }
            }
        }
        setItems(voiceSelector, labels);
        voiceSelector.setOnItemSelectedListener(new SelectionListener(position -> {
            if (!suppressSelectionEvents && position < kokoroVoices.size()) {
                requireContext().getSharedPreferences("sp1", 0).edit()
                        .putInt("active_kokoro_speaker", kokoroVoices.get(position).id)
                        .putString("active_language", kokoroVoices.get(position).language)
                        .apply();
            }
        }));
        int selectedSpeaker = requireContext().getSharedPreferences("sp1", 0)
                .getInt("active_kokoro_speaker", KokoroVoiceHelper.DEFAULT_SPEAKER_ID);
        for (int i = 0; i < kokoroVoices.size(); i++) {
            if (kokoroVoices.get(i).id == selectedSpeaker) {
                voiceSelector.setSelection(i);
                break;
            }
        }
    }

    private void onDocumentSelected(Uri uri) {
        if (uri == null) return;
        try {
            String text = TextImportHelper.read(requireContext(), uri);
            editor.setText(text);
            editor.setSelection(editor.length());
        } catch (IOException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void pasteFromClipboard() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()
                && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
            editor.setText(text == null ? "" : text.subSequence(0,
                    Math.min(TextImportHelper.MAX_CHARACTERS, text.length())));
            editor.setSelection(editor.length());
        }
    }

    private void launchSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak text to add");
        try {
            speechLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException exception) {
            showMessage("Speech recognition is not available on this device");
        }
    }

    private void importSharedText() {
        Intent intent = requireActivity().getIntent();
        CharSequence shared = null;
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            shared = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }
        if (shared != null && editor.length() == 0) {
            editor.setText(shared.subSequence(0,
                    Math.min(TextImportHelper.MAX_CHARACTERS, shared.length())));
        }
    }

    private void generate() {
        String text = editor.getText() == null ? "" : editor.getText().toString().trim();
        if (text.isEmpty()) {
            showMessage("Enter text to synthesize");
            return;
        }
        if (text.length() > TextImportHelper.MAX_CHARACTERS) {
            text = text.substring(0, TextImportHelper.MAX_CHARACTERS);
        }
        final String requestText = text;
        Context context = requireContext().getApplicationContext();
        progress.setVisibility(View.VISIBLE);
        synthesisStatus.setText("SYNTHESIZING...");
        viewEnable(false);
        synthesisExecutor.execute(() -> {
            try {
                SovraAudio.GeneratedSpeech result = SovraAudio.generate(context, requestText);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    generatedPcm = result.pcm;
                    sampleRate = result.sampleRate;
                    waveform.setPcm(generatedPcm);
                    waveform.setVisibility(generatedPcm.length == 0 ? View.GONE : View.VISIBLE);
                    playButton.setVisibility(generatedPcm.length == 0 ? View.GONE : View.VISIBLE);
                    synthesisStatus.setText(generatedPcm.length == 0
                            ? "READY TO SYNTHESIZE" : "AUDIO READY");
                    prepareAudioTrack();
                    progress.setVisibility(View.GONE);
                    viewEnable(true);
                    if (generatedPcm.length == 0) showMessage("No audio was generated");
                });
            } catch (Throwable failure) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    synthesisStatus.setText("SYNTHESIS FAILED");
                    viewEnable(true);
                    String message = failure.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = failure.getClass().getSimpleName();
                    }
                    showMessage("Synthesis failed: " + message);
                });
            }
        });
    }

    private void viewEnable(boolean enabled) {
        if (getView() != null) getView().findViewById(R.id.generate_button).setEnabled(enabled);
    }

    private void prepareAudioTrack() {
        releaseAudioTrack();
        if (generatedPcm == null || generatedPcm.length == 0 || sampleRate <= 0) return;
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(generatedPcm.length)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        int written = audioTrack.write(generatedPcm, 0, generatedPcm.length,
                AudioTrack.WRITE_BLOCKING);
        if (written != generatedPcm.length) {
            releaseAudioTrack();
            showMessage("Unable to load generated audio for playback");
        }
    }

    private void togglePlayback() {
        if (audioTrack == null) {
            showMessage("Generate speech first");
            return;
        }
        if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause();
            playButton.setIconResource(R.drawable.ic_play);
            playButton.setContentDescription("Play generated audio");
        } else {
            if (audioTrack.getPlaybackHeadPosition() >= generatedPcm.length / 2) {
                audioTrack.setPlaybackHeadPosition(0);
            }
            audioTrack.play();
            playButton.setIconResource(R.drawable.ic_pause);
            playButton.setContentDescription("Pause generated audio");
            updateWaveformProgress();
        }
    }

    private void seekTo(float fraction) {
        if (audioTrack == null || generatedPcm == null) return;
        boolean wasPlaying = audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
        if (wasPlaying) audioTrack.pause();
        int frames = generatedPcm.length / 2;
        audioTrack.setPlaybackHeadPosition((int) (frames * fraction));
        if (wasPlaying) audioTrack.play();
    }

    private void updateWaveformProgress() {
        if (audioTrack == null || generatedPcm == null || !isAdded()) return;
        int frames = generatedPcm.length / 2;
        int position = audioTrack.getPlaybackHeadPosition();
        waveform.setProgress(frames == 0 ? 0f : Math.min(1f, position / (float) frames));
        if (position >= frames) {
            audioTrack.pause();
            playButton.setIconResource(R.drawable.ic_play);
            playButton.setContentDescription("Play generated audio");
            return;
        }
        mainHandler.postDelayed(this::updateWaveformProgress, 40);
    }

    private void exportWav() {
        if (generatedPcm == null || generatedPcm.length == 0 || sampleRate <= 0) {
            showMessage("Generate audio before exporting");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String filename = "Sovra_" + stamp + ".wav";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Sovra");
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri = null;
        try {
            uri = requireContext().getContentResolver().insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Unable to create WAV media item");
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Unable to open WAV output");
                writeWavHeader(output, generatedPcm.length, sampleRate);
                output.write(generatedPcm);
            }
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.Audio.Media.IS_PENDING, 0);
            requireContext().getContentResolver().update(uri, complete, null, null);
            long durationMs = generatedPcm.length * 500L / sampleRate;
            saveToLibrary(uri, filename, durationMs);
            showMessage("Saved to Music/Sovra/" + filename);
        } catch (IOException | RuntimeException exception) {
            if (uri != null) requireContext().getContentResolver().delete(uri, null, null);
            showMessage("WAV export failed: " + exception.getMessage());
        }
    }

    private void saveToLibrary(Uri uri, String filename, long durationMs) throws IOException {
        android.content.SharedPreferences library = requireContext()
                .getSharedPreferences("sp2", 0);
        String json = library.getString("library_list", "[]");
        Type type = new TypeToken<ArrayList<SovraLibraryEntry>>() { }.getType();
        List<SovraLibraryEntry> entries;
        try {
            entries = new Gson().fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            throw new IOException("Saved library data is invalid", exception);
        }
        if (entries == null) entries = new ArrayList<>();
        String text = editor.getText() == null ? "" : editor.getText().toString();
        String title = text.length() > 32 ? text.substring(0, 32) + "…" : text;
        String voice = libraryVoiceName();
        entries.add(0, new SovraLibraryEntry(title.isEmpty() ? filename : title,
                text, uri.toString(), durationMs, System.currentTimeMillis(), voice));
        library.edit().putString("library_list", new Gson().toJson(entries)).apply();
    }

    private String libraryVoiceName() {
        android.content.SharedPreferences model = requireContext().getSharedPreferences("sp1", 0);
        return model.getString("active_model_name", "Sovra");
    }

    private static void writeWavHeader(OutputStream output, int dataLength, int rate)
            throws IOException {
        long byteRate = rate * 2L;
        output.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeInt(output, dataLength + 36);
        output.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, 1);
        writeInt(output, rate);
        writeInt(output, (int) byteRate);
        writeShort(output, 2);
        writeShort(output, 16);
        output.write("data".getBytes(StandardCharsets.US_ASCII));
        writeInt(output, dataLength);
    }

    private static void writeInt(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static void writeShort(OutputStream output, int value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    private void releaseAudioTrack() {
        if (audioTrack != null) {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }

    private void showMessage(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private static void setItems(Spinner spinner, List<String> values) {
        spinner.setAdapter(new SovraSpinnerAdapter(spinner.getContext(), values));
    }

    private interface ItemSelected {
        void onSelected(int position);
    }

    private static final class SelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final ItemSelected callback;

        SelectionListener(ItemSelected callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                   int position, long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
