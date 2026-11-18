package com.builtbydeep.sovra;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public final class ModelsFragmentActivity extends Fragment {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final List<SovraModels.Item> catalog = new ArrayList<>();
    private final Map<String, FutureTask<Void>> downloads = new ConcurrentHashMap<>();
    private final Map<String, Integer> progressPercent = new ConcurrentHashMap<>();
    private final Map<String, String> progressLabels = new ConcurrentHashMap<>();
    private RecyclerView recyclerView;
    private ModelAdapter adapter;
    private TextView statusView;
    private ChipGroup languageChips;
    private String selectedFilter = "all";
    private String languageQuery = "";
    private String selectedLanguageChip = "";
    private Uri selectedModelUri;
    private String selectedModelName;
    private AudioTrack previewTrack;

    private final ActivityResultLauncher<String[]> modelPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onModelSelected);
    private final ActivityResultLauncher<String[]> tokensPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onTokensSelected);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_models, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        initializeOptionalFirebase();
        recyclerView = view.findViewById(R.id.model_list);
        statusView = view.findViewById(R.id.catalog_status);
        languageChips = view.findViewById(R.id.language_chips);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ModelAdapter();
        recyclerView.setAdapter(adapter);
        android.content.SharedPreferences preferences =
                requireContext().getSharedPreferences("sp1", 0);
        String persistedFilter = preferences.getString("sort_preference", "all_models");
        selectedFilter = persistedFilter.equals("download") ? "downloaded"
                : persistedFilter.equals("installed") ? "installed"
                : persistedFilter.equals("newest") ? "newest"
                : persistedFilter.equals("oldest") ? "oldest" : "all";
        ChipGroup filters = view.findViewById(R.id.model_filters);
        filters.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {
                    if (checkedIds.isEmpty()) return;
                    int id = checkedIds.get(0);
                    selectedFilter = id == R.id.filter_downloaded ? "downloaded"
                            : id == R.id.filter_installed ? "installed"
                            : id == R.id.filter_newest ? "newest"
                            : id == R.id.filter_oldest ? "oldest" : "all";
                    renderCatalog();
                });
        android.widget.EditText language = view.findViewById(R.id.language_filter);
        String savedLanguage = preferences.getString("language_filter", "");
        languageQuery = savedLanguage.toLowerCase(Locale.ROOT);
        selectedLanguageChip = preferences.getString("language_filter_chip", "");
        if (!savedLanguage.isEmpty()) language.setText(savedLanguage);
        language.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                languageQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                requireContext().getSharedPreferences("sp1", 0).edit()
                        .putString("language_filter", languageQuery).apply();
                renderCatalog();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        int selectedFilterId = selectedFilter.equals("downloaded") ? R.id.filter_downloaded
                : selectedFilter.equals("installed") ? R.id.filter_installed
                : selectedFilter.equals("newest") ? R.id.filter_newest
                : selectedFilter.equals("oldest") ? R.id.filter_oldest : R.id.filter_all;
        filters.check(selectedFilterId);
        view.findViewById(R.id.import_model_button).setOnClickListener(v ->
                modelPicker.launch(new String[]{"application/octet-stream", "*/*"}));
        loadCatalog();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) renderCatalog();
    }

    @Override
    public void onDestroy() {
        for (FutureTask<Void> task : downloads.values()) task.cancel(true);
        downloads.clear();
        executor.shutdownNow();
        releasePreview();
        super.onDestroy();
    }

    private void initializeOptionalFirebase() {
        try {
            if (!FirebaseApp.getApps(requireContext()).isEmpty()) {
                FirebaseDatabase.getInstance();
            }
        } catch (Throwable ignored) {
            // Offline catalogs and local imports remain fully usable without Firebase.
        }
    }

    private void loadCatalog() {
        catalog.clear();
        try {
            catalog.addAll(SovraModels.load(requireContext()));
            statusView.setText("Built-in Kokoro, Supertonic, Piper and MMS catalogs");
        } catch (IOException | RuntimeException exception) {
            for (VoiceModelCatalog.Model model : VoiceModelCatalog.getBuiltInModels()) {
                catalog.add(new SovraModels.Item(
                        model.id, model.name, model.type, model.language, model.size, null));
            }
            statusView.setText("Using offline catalogs. MMS catalog unavailable.");
        }
        File customRoot = new File(requireContext().getFilesDir(), "PiperModels");
        File[] custom = customRoot.listFiles();
        if (custom != null) {
            for (File directory : custom) {
                if (directory.isDirectory()) {
                    SovraModels.Item item = SovraModels.fromDirectory(requireContext(), directory);
                    if (SovraModels.isInstalled(requireContext(), item)) catalog.add(item);
                }
            }
        }
        populateLanguageChips();
        requireContext().getSharedPreferences("sp1", 0).edit()
                .putString("models_data", gson.toJson(catalog)).apply();
        renderCatalog();
    }

    private void renderCatalog() {
        if (adapter == null) return;
        List<SovraModels.Item> visible = new ArrayList<>();
        for (SovraModels.Item item : catalog) {
            boolean installed = SovraModels.isInstalled(requireContext(), item);
            if (selectedFilter.equals("downloaded") && !SovraModels.isDownloaded(requireContext(), item)) {
                continue;
            }
            if (selectedFilter.equals("installed") && !installed) continue;
            if (!languageQuery.isEmpty()
                    && !item.language.toLowerCase(Locale.ROOT).contains(languageQuery)
                    && !item.name.toLowerCase(Locale.ROOT).contains(languageQuery)) continue;
            if (!matchesLanguageChip(item)) continue;
            visible.add(item);
        }
        if (selectedFilter.equals("newest")) Collections.reverse(visible);
        if (selectedFilter.equals("oldest")) {
            visible.sort(Comparator.comparing(item -> item.name.toLowerCase(Locale.ROOT)));
        }
        adapter.submit(visible);
        String storedFilter = selectedFilter.equals("downloaded") ? "download"
                : selectedFilter.equals("all") ? "all_models" : selectedFilter;
        requireContext().getSharedPreferences("sp1", 0).edit()
                .putString("sort_preference", storedFilter).apply();
    }

    private void populateLanguageChips() {
        languageChips.removeAllViews();
        addLanguageChip("All languages", "");
        for (String language : new String[]{"English", "English UK", "Hindi", "Spanish",
                "French", "Italian", "Japanese", "Portuguese", "Chinese",
                "Multi-language", "MMS"}) {
            addLanguageChip(language, language);
        }
        int selectedId = languageChips.getChildAt(0).getId();
        for (int i = 0; i < languageChips.getChildCount(); i++) {
            Chip chip = (Chip) languageChips.getChildAt(i);
            if (selectedLanguageChip.equals(String.valueOf(chip.getTag()))) {
                selectedId = chip.getId();
                break;
            }
        }
        languageChips.check(selectedId);
        languageChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            View selected = group.findViewById(checkedIds.get(0));
            Object tag = selected == null ? null : selected.getTag();
            selectedLanguageChip = tag == null ? "" : tag.toString();
            requireContext().getSharedPreferences("sp1", 0).edit()
                    .putString("language_filter_chip", selectedLanguageChip).apply();
            renderCatalog();
        });
    }

    private void addLanguageChip(String label, String filter) {
        Chip chip = new Chip(requireContext());
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(filter);
        languageChips.addView(chip);
    }

    private boolean matchesLanguageChip(SovraModels.Item item) {
        if (selectedLanguageChip.isEmpty()) return true;
        if (selectedLanguageChip.equals("MMS")) return item.type.equals("mms");
        String languageCode;
        switch (selectedLanguageChip) {
            case "English": languageCode = "en"; break;
            case "English UK": languageCode = "en-GB"; break;
            case "Hindi": languageCode = "hi"; break;
            case "Spanish": languageCode = "es"; break;
            case "French": languageCode = "fr"; break;
            case "Italian": languageCode = "it"; break;
            case "Japanese": languageCode = "ja"; break;
            case "Portuguese": languageCode = "pt"; break;
            case "Chinese": languageCode = "zh"; break;
            default: languageCode = "";
        }
        if (item.type.equals("kokoro") && !languageCode.isEmpty()) {
            for (KokoroVoiceHelper.Voice voice : KokoroVoiceHelper.getVoices()) {
                if (languageCode.contains("-")
                        ? voice.language.equalsIgnoreCase(languageCode)
                        : voice.language.toLowerCase(Locale.ROOT).startsWith(languageCode)) {
                    return true;
                }
            }
        }
        if (item.type.equals("supertonic") && !languageCode.isEmpty()) {
            return SupertonicEngine.normalizeLanguage(languageCode)
                    .equals(languageCode.contains("-")
                            ? languageCode.substring(0, languageCode.indexOf('-')) : languageCode);
        }
        return item.language.toLowerCase(Locale.ROOT)
                .contains(selectedLanguageChip.toLowerCase(Locale.ROOT));
    }

    private void download(SovraModels.Item item) {
        if (downloads.containsKey(item.id)) {
            FutureTask<Void> task = downloads.remove(item.id);
            if (task != null) task.cancel(true);
            progressLabels.remove(item.id);
            progressPercent.remove(item.id);
            adapter.notifyDataSetChanged();
            return;
        }
        Context context = requireContext().getApplicationContext();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                downloadItem(context, item);
                mainHandler.post(() -> {
                    downloads.remove(item.id);
                    progressLabels.remove(item.id);
                    progressPercent.remove(item.id);
                    if (isAdded()) {
                        Toast.makeText(requireContext(), item.name + " installed",
                                Toast.LENGTH_SHORT).show();
                        renderCatalog();
                    }
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    downloads.remove(item.id);
                    progressLabels.remove(item.id);
                    progressPercent.remove(item.id);
                    if (isAdded()) {
                        String message = exception instanceof java.util.concurrent.CancellationException
                                || exception instanceof java.io.InterruptedIOException
                                ? item.name + " download cancelled"
                                : "Download failed: " + exception.getMessage();
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        renderCatalog();
                    }
                });
            }
            return null;
        });
        downloads.put(item.id, task);
        adapter.notifyDataSetChanged();
        executor.execute(task);
    }

    private void downloadItem(Context context, SovraModels.Item item) throws IOException {
        ModelFileDownloader.ProgressListener listener = (name, downloaded, total) -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Download cancelled");
            }
            int percent = total > 0 ? (int) Math.min(100, downloaded * 100 / total) : 0;
            progressPercent.put(item.id, percent);
            progressLabels.put(item.id, name);
            mainHandler.post(() -> {
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        };
        if (item.type.equals("kokoro")) {
            new KokoroModelFiles(context.getFilesDir()).download(listener);
        } else if (item.type.equals("supertonic")) {
            new SupertonicModelFiles(context.getFilesDir()).download(listener);
        } else if (item.type.equals("vits")) {
            PiperModelCatalog.Model piper = null;
            for (PiperModelCatalog.Model candidate : PiperModelCatalog.getModels()) {
                if (candidate.modelDirectory.equals(item.id)) {
                    piper = candidate;
                    break;
                }
            }
            if (piper == null) throw new IOException("Unknown Piper model: " + item.name);
            PiperModelCatalog.downloadAndExtract(
                    piper, context.getFilesDir(), listener);
        } else if (item.type.equals("mms") && item.mms != null) {
            File directory = SovraModels.directory(context, item);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Unable to create MMS model directory");
            }
            ModelFileDownloader.downloadAtomically(item.mms.modelUrl,
                    new File(directory, "model.onnx"), 30000, 60000, "Sovra", listener);
            ModelFileDownloader.downloadAtomically(item.mms.tokensUrl,
                    new File(directory, "tokens.txt"), 30000, 60000, "Sovra", listener);
        } else {
            throw new IOException("This model cannot be downloaded");
        }
    }

    private void activate(SovraModels.Item item) {
        try {
            SovraModels.activate(requireContext(), item);
            Toast.makeText(requireContext(), item.name + " is active", Toast.LENGTH_SHORT).show();
            renderCatalog();
        } catch (IOException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void delete(SovraModels.Item item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + item.name + "?")
                .setMessage("Downloaded model files will be removed from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        SovraModels.delete(requireContext(), item);
                        renderCatalog();
                    } catch (IOException exception) {
                        Toast.makeText(requireContext(), exception.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void preview(SovraModels.Item item) {
        try {
            SovraModels.activate(requireContext(), item);
        } catch (IOException exception) {
            Toast.makeText(requireContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                SovraAudio.GeneratedSpeech speech =
                        SovraAudio.generatePreview(context, "Hello. This is a Sovra voice preview.");
                mainHandler.post(() -> playPreview(speech));
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "Preview failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void playPreview(SovraAudio.GeneratedSpeech speech) {
        releasePreview();
        if (speech.pcm.length == 0 || speech.sampleRate <= 0) return;
        previewTrack = new AudioTrack.Builder()
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(speech.sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(speech.pcm.length)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        int written = previewTrack.write(speech.pcm, 0, speech.pcm.length,
                AudioTrack.WRITE_BLOCKING);
        if (written == speech.pcm.length) previewTrack.play();
        else releasePreview();
    }

    private void releasePreview() {
        if (previewTrack != null) {
            previewTrack.pause();
            previewTrack.flush();
            previewTrack.release();
            previewTrack = null;
        }
    }

    private void onModelSelected(Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers grant read access without a persistable grant.
        }
        String display = displayName(uri);
        if (!display.toLowerCase(Locale.ROOT).endsWith(".onnx")) {
            Toast.makeText(requireContext(), "Choose a .onnx model file",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        selectedModelUri = uri;
        selectedModelName = display.substring(0, display.length() - 5)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        tokensPicker.launch(new String[]{"text/plain", "text/*"});
    }

    private void onTokensSelected(Uri uri) {
        if (uri == null || selectedModelUri == null) return;
        String name = displayName(uri).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".txt")) {
            Toast.makeText(requireContext(), "Choose a .txt tokens file",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Uri modelUri = selectedModelUri;
        String modelName = selectedModelName == null || selectedModelName.isEmpty()
                ? "custom-voice" : selectedModelName;
        selectedModelUri = null;
        selectedModelName = null;
        Context context = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                File directory = new File(new File(context.getFilesDir(), "PiperModels"), modelName);
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IOException("Unable to create custom model directory");
                }
                copyUri(context, modelUri, new File(directory, "model.onnx"));
                copyUri(context, uri, new File(directory, "tokens.txt"));
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    catalog.removeIf(item -> item.type.equals("custom")
                            && item.id.equals(modelName));
                    catalog.add(SovraModels.customItem(modelName, modelName.replace('_', ' ')));
                    renderCatalog();
                    Toast.makeText(requireContext(), "Custom voice imported",
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "Model import failed: " + exception.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void copyUri(Context context, Uri uri, File destination) throws IOException {
        File staging = new File(destination.getAbsolutePath() + ".part");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(staging)) {
                if (input == null) throw new IOException("Unable to open selected file");
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        } catch (IOException | RuntimeException exception) {
            if (staging.exists() && !staging.delete()) {
                exception.addSuppressed(new IOException("Unable to remove partial import"));
            }
            throw exception;
        }
        if (!staging.isFile() || staging.length() == 0) {
            staging.delete();
            throw new IOException("Selected model or tokens file is empty");
        }
        if (destination.exists() && !destination.delete()) {
            staging.delete();
            throw new IOException("Unable to replace imported file");
        }
        if (!staging.renameTo(destination)) {
            staging.delete();
            throw new IOException("Unable to install imported file");
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private final class ModelAdapter extends RecyclerView.Adapter<ModelHolder> {
        private final List<SovraModels.Item> visible = new ArrayList<>();

        void submit(List<SovraModels.Item> items) {
            visible.clear();
            visible.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public ModelHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_model, parent, false);
            return new ModelHolder(view);
        }

        @Override
        public void onBindViewHolder(ModelHolder holder, int position) {
            SovraModels.Item item = visible.get(position);
            boolean installed = SovraModels.isInstalled(requireContext(), item);
            boolean running = downloads.containsKey(item.id);
            holder.title.setText(item.name);
            holder.details.setText(item.type.toUpperCase(Locale.ROOT) + " · "
                    + item.language + (item.size.isEmpty() ? "" : " · " + item.size));
            holder.status.setText(running
                    ? progressLabels.getOrDefault(item.id, "Downloading")
                    : installed ? "Installed"
                    : SovraModels.isDownloaded(requireContext(), item)
                    ? "Downloaded, incomplete" : "Not installed");
            holder.progress.setVisibility(running ? View.VISIBLE : View.GONE);
            holder.progress.setIndeterminate(running && !progressPercent.containsKey(item.id));
            if (running) holder.progress.setProgress(progressPercent.getOrDefault(item.id, 0));
            holder.primary.setText(running ? "Cancel" : installed ? "Activate" : "Download");
            holder.preview.setVisibility(installed ? View.VISIBLE : View.GONE);
            holder.delete.setVisibility(SovraModels.isDownloaded(requireContext(), item)
                    ? View.VISIBLE : View.GONE);
            holder.primary.setOnClickListener(v -> {
                if (running) download(item);
                else if (installed) activate(item);
                else download(item);
            });
            holder.preview.setOnClickListener(v -> preview(item));
            holder.delete.setOnClickListener(v -> delete(item));
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }
    }

    private static final class ModelHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView details;
        final TextView status;
        final ProgressBar progress;
        final MaterialButton primary;
        final MaterialButton preview;
        final MaterialButton delete;

        ModelHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.model_title);
            details = itemView.findViewById(R.id.model_details);
            status = itemView.findViewById(R.id.model_status);
            progress = itemView.findViewById(R.id.model_progress);
            primary = itemView.findViewById(R.id.model_primary_action);
            preview = itemView.findViewById(R.id.model_preview_action);
            delete = itemView.findViewById(R.id.model_delete_action);
        }
    }
}
