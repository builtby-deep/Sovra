package com.builtbydeep.sovra;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FishVoiceExplorerActivity extends AppCompatActivity {

    private static final class FilterChipSpec {
        final String id;
        final String label;
        final String sortBy;
        final String language;
        final String queryBoost;
        final String tagFilter;

        FilterChipSpec(String id, String label, String sortBy, String language, String queryBoost, String tagFilter) {
            this.id = id;
            this.label = label;
            this.sortBy = sortBy;
            this.language = language;
            this.queryBoost = queryBoost;
            this.tagFilter = tagFilter;
        }
    }

    private final List<FilterChipSpec> primaryChips = new ArrayList<>();
    private final List<FilterChipSpec> secondaryChips = new ArrayList<>();

    private String activePrimaryId = "trending";
    private String activeSecondaryId = "all";
    private String searchQuery = "";

    private EditText etSearch;
    private ImageView btnClearSearch;
    private TextView tvCatalogCount;
    private TextView btnFavShortcut;
    private LinearLayout rowPrimaryChips;
    private LinearLayout rowSecondaryChips;
    private RecyclerView rvVoices;
    private ProgressBar progressInitial;
    private ProgressBar progressLoadMore;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyMessage;
    private TextView btnRetry;

    private final List<FishVoiceStore.FishVoiceItem> displayedItems = new ArrayList<>();
    private VoiceAdapter adapter;

    private int currentPage = 1;
    private boolean hasMorePages = true;
    private boolean isFetching = false;
    private int requestGeneration = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchDebounceRunnable;

    private MediaPlayer samplePlayer;
    private String playingVoiceId = "";
    private String loadingVoiceId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FishVoiceStore.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_fish_voice_explorer);

        View root = findViewById(R.id.root_explorer);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        etSearch = findViewById(R.id.et_search_voices);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        tvCatalogCount = findViewById(R.id.tv_catalog_count);
        btnFavShortcut = findViewById(R.id.btn_fav_shortcut);
        rowPrimaryChips = findViewById(R.id.row_sort_lang_chips);
        rowSecondaryChips = findViewById(R.id.row_gender_fav_chips);
        rvVoices = findViewById(R.id.rv_fish_voices);
        progressInitial = findViewById(R.id.progress_initial);
        progressLoadMore = findViewById(R.id.progress_load_more);
        layoutEmpty = findViewById(R.id.layout_empty_state);
        tvEmptyMessage = findViewById(R.id.tv_empty_message);
        btnRetry = findViewById(R.id.btn_retry_fetch);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnRetry.setOnClickListener(v -> reloadCatalog());
        btnFavShortcut.setOnClickListener(v -> {
            activeSecondaryId = "favorites".equals(activeSecondaryId) ? "all" : "favorites";
            renderChipSelection();
            reloadCatalog();
        });

        setupChipDefinitions();
        renderChipSelection();
        updateFavoriteCounter();

        LinearLayoutManager lm = new LinearLayoutManager(this);
        rvVoices.setLayoutManager(lm);
        adapter = new VoiceAdapter();
        rvVoices.setAdapter(adapter);

        rvVoices.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                if ("favorites".equals(activeSecondaryId)) return;
                if (isFetching || !hasMorePages) return;
                int visibleCount = lm.getChildCount();
                int totalCount = lm.getItemCount();
                int firstVisible = lm.findFirstVisibleItemPosition();
                if ((visibleCount + firstVisible) >= totalCount - 5) {
                    loadNextPage();
                }
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s == null ? "" : s.toString().trim();
                btnClearSearch.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (searchDebounceRunnable != null) {
                    mainHandler.removeCallbacks(searchDebounceRunnable);
                }
                searchDebounceRunnable = () -> {
                    if (!text.equals(searchQuery)) {
                        searchQuery = text;
                        reloadCatalog();
                    }
                };
                mainHandler.postDelayed(searchDebounceRunnable, 380);
            }
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            reloadCatalog();
        });

        reloadCatalog();
    }

    private void setupChipDefinitions() {
        primaryChips.clear();
        primaryChips.add(new FilterChipSpec("trending", "Trending 🔥", "task_count", "", "", ""));
        primaryChips.add(new FilterChipSpec("hot", "Hot ⚡", "score", "", "", ""));
        primaryChips.add(new FilterChipSpec("new", "New ✨", "created_at", "", "", ""));
        primaryChips.add(new FilterChipSpec("en", "English 🇺🇸", "task_count", "en", "", ""));
        primaryChips.add(new FilterChipSpec("hi", "Hindi 🇮🇳", "task_count", "hi", "", ""));
        primaryChips.add(new FilterChipSpec("india", "India 🇮🇳", "task_count", "", "Indian", ""));
        primaryChips.add(new FilterChipSpec("uk", "UK 🇬🇧", "task_count", "en", "British", ""));
        primaryChips.add(new FilterChipSpec("ja", "Japanese 🇯🇵", "task_count", "ja", "", ""));

        secondaryChips.clear();
        secondaryChips.add(new FilterChipSpec("all", "All", "", "", "", ""));
        secondaryChips.add(new FilterChipSpec("female", "Female ♀", "", "", "", ""));
        secondaryChips.add(new FilterChipSpec("male", "Male ♂", "", "", "", ""));
        secondaryChips.add(new FilterChipSpec("favorites", "Favorites ⭐", "", "", "", ""));
    }

    private void renderChipSelection() {
        rowPrimaryChips.removeAllViews();
        for (FilterChipSpec chip : primaryChips) {
            TextView tv = buildChipView(chip.label, chip.id.equals(activePrimaryId));
            tv.setOnClickListener(v -> {
                if ("favorites".equals(activeSecondaryId)) {
                    activeSecondaryId = "all";
                }
                activePrimaryId = chip.id;
                renderChipSelection();
                reloadCatalog();
            });
            rowPrimaryChips.addView(tv);
        }

        rowSecondaryChips.removeAllViews();
        for (FilterChipSpec chip : secondaryChips) {
            TextView tv = buildChipView(chip.label, chip.id.equals(activeSecondaryId));
            tv.setOnClickListener(v -> {
                activeSecondaryId = chip.id;
                renderChipSelection();
                reloadCatalog();
            });
            rowSecondaryChips.addView(tv);
        }
    }

    private TextView buildChipView(String label, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12.5f);
        tv.setTextColor(selected ? Color.parseColor("#00E5FF") : Color.parseColor("#CBD5E1"));
        tv.setBackgroundResource(R.drawable.bg_fish_chip);
        tv.setSelected(selected);
        int padH = (int) (13 * density);
        int padV = (int) (7 * density);
        tv.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (8 * density));
        tv.setLayoutParams(lp);
        return tv;
    }

    private FilterChipSpec getActivePrimaryChip() {
        for (FilterChipSpec chip : primaryChips) {
            if (chip.id.equals(activePrimaryId)) return chip;
        }
        return primaryChips.get(0);
    }

    private void updateFavoriteCounter() {
        int count = FishVoiceStore.getFavorites(this).size();
        btnFavShortcut.setText("⭐ " + count);
    }

    private void reloadCatalog() {
        requestGeneration++;
        currentPage = 1;
        hasMorePages = true;
        isFetching = false;
        displayedItems.clear();
        adapter.notifyDataSetChanged();
        layoutEmpty.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        if ("favorites".equals(activeSecondaryId)) {
            progressInitial.setVisibility(View.GONE);
            progressLoadMore.setVisibility(View.GONE);
            List<FishVoiceStore.FishVoiceItem> favs = FishVoiceStore.getFavorites(this);
            String q = searchQuery.toLowerCase(Locale.ROOT);
            for (FishVoiceStore.FishVoiceItem item : favs) {
                if (q.isEmpty()
                        || item.title.toLowerCase(Locale.ROOT).contains(q)
                        || item.region.toLowerCase(Locale.ROOT).contains(q)) {
                    displayedItems.add(item);
                }
            }
            adapter.notifyDataSetChanged();
            tvCatalogCount.setText(favs.size() + " Starred • Synced with Home");
            if (displayedItems.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                tvEmptyMessage.setText(favs.isEmpty()
                        ? "No starred voices yet.\nTap ⭐ on any voice to pin it to your Home Dropdown!"
                        : "No starred voices match "" + searchQuery + """);
            }
            return;
        }

        progressInitial.setVisibility(View.VISIBLE);
        loadNextPage();
    }

    private void loadNextPage() {
        if (isFetching || !hasMorePages) return;
        isFetching = true;
        final int gen = requestGeneration;
        final int targetPage = currentPage;
        final FilterChipSpec primary = getActivePrimaryChip();
        final String genderFilter = activeSecondaryId;

        if (targetPage > 1) {
            progressLoadMore.setVisibility(View.VISIBLE);
        }

        String effectiveQuery = searchQuery;
        if (!TextUtils.isEmpty(primary.queryBoost)) {
            effectiveQuery = effectiveQuery.isEmpty()
                    ? primary.queryBoost
                    : (effectiveQuery + " " + primary.queryBoost);
        }
        if ("female".equals(genderFilter) && effectiveQuery.isEmpty() && primary.language.isEmpty()) {
            effectiveQuery = "female";
        } else if ("male".equals(genderFilter) && effectiveQuery.isEmpty() && primary.language.isEmpty()) {
            effectiveQuery = "male";
        }
        final String finalQuery = effectiveQuery;

        new Thread(() -> {
            try {
                ArrayList<FishVoiceStore.FishVoiceItem> batchMatches = new ArrayList<>();
                int pageCursor = targetPage;
                int totalServer = 0;
                boolean serverHasMore = false;
                int attempts = 0;

                while (attempts < 3) {
                    attempts++;
                    FishVoiceStore.SearchPage page = FishVoiceStore.fetchLiveModels(
                            getApplicationContext(),
                            finalQuery,
                            primary.sortBy,
                            primary.language,
                            primary.tagFilter,
                            pageCursor,
                            30
                    );
                    totalServer = page.total;
                    serverHasMore = page.hasMore;
                    pageCursor++;

                    for (FishVoiceStore.FishVoiceItem item : page.items) {
                        if ("female".equals(genderFilter) && !"Female".equalsIgnoreCase(item.gender)) {
                            continue;
                        }
                        if ("male".equals(genderFilter) && !"Male".equalsIgnoreCase(item.gender)) {
                            continue;
                        }
                        batchMatches.add(item);
                    }
                    if (!serverHasMore || batchMatches.size() >= 12 || "all".equals(genderFilter)) {
                        break;
                    }
                }

                final int nextPage = pageCursor;
                final int finalTotal = totalServer;
                final boolean finalHasMore = serverHasMore;

                mainHandler.post(() -> {
                    if (gen != requestGeneration || isFinishing()) return;
                    isFetching = false;
                    progressInitial.setVisibility(View.GONE);
                    progressLoadMore.setVisibility(View.GONE);
                    currentPage = nextPage;
                    hasMorePages = finalHasMore;

                    if (finalTotal > 0) {
                        tvCatalogCount.setText(String.format(Locale.US, "%,d Live Voices • s2.1-pro-free", finalTotal));
                    }

                    int insertStart = displayedItems.size();
                    for (FishVoiceStore.FishVoiceItem candidate : batchMatches) {
                        boolean exists = false;
                        for (FishVoiceStore.FishVoiceItem existing : displayedItems) {
                            if (existing.id.equals(candidate.id)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) displayedItems.add(candidate);
                    }
                    if (displayedItems.size() > insertStart) {
                        adapter.notifyItemRangeInserted(insertStart, displayedItems.size() - insertStart);
                    }

                    if (displayedItems.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setText("No matching speakers found.\nTry another filter or search keyword.");
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                    }
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    if (gen != requestGeneration || isFinishing()) return;
                    isFetching = false;
                    progressInitial.setVisibility(View.GONE);
                    progressLoadMore.setVisibility(View.GONE);

                    if (displayedItems.isEmpty()) {
                        displayedItems.addAll(FishVoiceStore.getOfflineFallbackCatalog());
                        adapter.notifyDataSetChanged();
                        tvCatalogCount.setText("Offline Backup • Check Internet for 2M+ Live Voices");
                        Toast.makeText(this, "Live fetch failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "fish-voice-explorer-fetch").start();
    }

    // --- Sample Audio Preview Playback ---

    private void toggleSamplePlayback(FishVoiceStore.FishVoiceItem item) {
        if (item == null) return;
        if (item.id.equals(playingVoiceId) || item.id.equals(loadingVoiceId)) {
            stopSamplePlayback();
            return;
        }
        stopSamplePlayback();

        if (!TextUtils.isEmpty(item.sampleUrl)) {
            playUrlStream(item.id, item.sampleUrl);
            return;
        }

        SharedPreferences sp = getSharedPreferences("sp1", MODE_PRIVATE);
        String apiKey = sp.getString(CloudTtsClients.getApiKeyPreference(CloudTtsClients.FISH), "").trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "No sample audio for this voice", Toast.LENGTH_SHORT).show();
            return;
        }
        loadingVoiceId = item.id;
        adapter.notifyDataSetChanged();
        new Thread(() -> {
            try {
                CloudTtsProvider provider = CloudTtsClients.get(CloudTtsClients.FISH);
                byte[] mp3 = provider.synthesize(
                        apiKey,
                        CloudTtsClients.FISH_FREE,
                        item.toCloudVoice(),
                        "Hello there.",
                        "en",
                        1.0f,
                        1.0f,
                        true
                );
                File tmp = File.createTempFile("fish-preview-", ".mp3", getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    fos.write(mp3);
                }
                mainHandler.post(() -> {
                    if (!item.id.equals(loadingVoiceId) || isFinishing()) {
                        tmp.delete();
                        return;
                    }
                    playLocalFile(item.id, tmp);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (item.id.equals(loadingVoiceId)) {
                        loadingVoiceId = "";
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(this, "Preview unavailable", Toast.LENGTH_SHORT).show();
                });
            }
        }, "fish-sample-synth").start();
    }

    private void playUrlStream(String voiceId, String url) {
        try {
            loadingVoiceId = voiceId;
            adapter.notifyDataSetChanged();
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(url);
            mp.setOnPreparedListener(player -> {
                if (!voiceId.equals(loadingVoiceId) || isFinishing()) {
                    player.release();
                    return;
                }
                loadingVoiceId = "";
                playingVoiceId = voiceId;
                adapter.notifyDataSetChanged();
                player.start();
            });
            mp.setOnCompletionListener(player -> stopSamplePlayback());
            mp.setOnErrorListener((player, what, extra) -> {
                stopSamplePlayback();
                Toast.makeText(this, "Unable to play sample audio", Toast.LENGTH_SHORT).show();
                return true;
            });
            samplePlayer = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            stopSamplePlayback();
            Toast.makeText(this, "Sample audio error", Toast.LENGTH_SHORT).show();
        }
    }

    private void playLocalFile(String voiceId, File file) {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(player -> {
                loadingVoiceId = "";
                playingVoiceId = voiceId;
                adapter.notifyDataSetChanged();
                player.start();
            });
            mp.setOnCompletionListener(player -> {
                file.delete();
                stopSamplePlayback();
            });
            mp.setOnErrorListener((player, what, extra) -> {
                file.delete();
                stopSamplePlayback();
                return true;
            });
            samplePlayer = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            file.delete();
            stopSamplePlayback();
        }
    }

    private void stopSamplePlayback() {
        loadingVoiceId = "";
        playingVoiceId = "";
        if (samplePlayer != null) {
            try {
                if (samplePlayer.isPlaying()) samplePlayer.stop();
            } catch (Exception ignored) {}
            try {
                samplePlayer.release();
            } catch (Exception ignored) {}
            samplePlayer = null;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSamplePlayback();
    }

    @Override
    protected void onDestroy() {
        stopSamplePlayback();
        super.onDestroy();
    }

    // --- RecyclerView Adapter ---

    private final class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final View btnPreviewAvatar;
            final ImageView ivAvatar;
            final ImageView ivPlayState;
            final TextView tvTitle;
            final TextView tvFlag;
            final TextView tvMeta;
            final LinearLayout layoutTags;
            final TextView tvTag1;
            final TextView tvTag2;
            final ImageView btnStar;
            final TextView btnUse;

            Holder(@NonNull View v) {
                super(v);
                btnPreviewAvatar = v.findViewById(R.id.btn_preview_avatar);
                ivAvatar = v.findViewById(R.id.iv_gender_avatar);
                ivPlayState = v.findViewById(R.id.iv_play_state);
                tvTitle = v.findViewById(R.id.tv_voice_title);
                tvFlag = v.findViewById(R.id.tv_voice_flag);
                tvMeta = v.findViewById(R.id.tv_voice_meta);
                layoutTags = v.findViewById(R.id.layout_tags);
                tvTag1 = v.findViewById(R.id.tv_tag_1);
                tvTag2 = v.findViewById(R.id.tv_tag_2);
                btnStar = v.findViewById(R.id.btn_star_favorite);
                btnUse = v.findViewById(R.id.btn_use_voice_item);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_fish_voice, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            FishVoiceStore.FishVoiceItem item = displayedItems.get(position);
            boolean isFemale = "Female".equalsIgnoreCase(item.gender);
            h.ivAvatar.setImageResource(isFemale ? R.drawable.ic_female_avatar : R.drawable.ic_male_avatar);

            boolean isPlaying = item.id.equals(playingVoiceId);
            boolean isBuffering = item.id.equals(loadingVoiceId);
            h.ivPlayState.setImageResource((isPlaying || isBuffering)
                    ? R.drawable.icon_pause_circle
                    : R.drawable.icon_play_circle);
            h.ivPlayState.setColorFilter(Color.parseColor(isPlaying ? "#00E5FF" : "#38BDF8"));

            h.tvTitle.setText(item.title);
            h.tvFlag.setText(item.flag);
            h.tvMeta.setText("s2.1-pro-free • " + item.formattedUses());

            List<String> cleanTags = new ArrayList<>();
            for (String t : item.tags) {
                if (!t.equalsIgnoreCase("female") && !t.equalsIgnoreCase("male") && !t.isEmpty()) {
                    cleanTags.add(t);
                    if (cleanTags.size() == 2) break;
                }
            }
            if (cleanTags.isEmpty()) {
                h.layoutTags.setVisibility(View.VISIBLE);
                h.tvTag1.setVisibility(View.VISIBLE);
                h.tvTag1.setText(item.region);
                h.tvTag2.setVisibility(View.GONE);
            } else {
                h.layoutTags.setVisibility(View.VISIBLE);
                h.tvTag1.setVisibility(View.VISIBLE);
                h.tvTag1.setText(cleanTags.get(0));
                if (cleanTags.size() > 1) {
                    h.tvTag2.setVisibility(View.VISIBLE);
                    h.tvTag2.setText(cleanTags.get(1));
                } else {
                    h.tvTag2.setVisibility(View.GONE);
                }
            }

            boolean starred = FishVoiceStore.isFavorite(FishVoiceExplorerActivity.this, item.id);
            h.btnStar.setImageResource(starred ? R.drawable.icon_favorite : R.drawable.icon_favorite_outline);
            h.btnStar.setColorFilter(Color.parseColor(starred ? "#FBBF24" : "#64748B"));

            h.btnPreviewAvatar.setOnClickListener(v -> toggleSamplePlayback(item));

            h.btnStar.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                FishVoiceStore.FishVoiceItem target = displayedItems.get(pos);
                boolean nowStarred = FishVoiceStore.toggleFavorite(FishVoiceExplorerActivity.this, target);
                updateFavoriteCounter();
                if ("favorites".equals(activeSecondaryId) && !nowStarred) {
                    displayedItems.remove(pos);
                    notifyItemRemoved(pos);
                    tvCatalogCount.setText(displayedItems.size() + " Starred • Synced with Home");
                    if (displayedItems.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setText("No starred voices yet.\nTap ⭐ on any voice to pin it to your Home Dropdown!");
                    }
                } else {
                    notifyItemChanged(pos);
                }
                Toast.makeText(
                        FishVoiceExplorerActivity.this,
                        nowStarred ? "⭐ Added to Home Screen Dropdown" : "Removed from Favorites",
                        Toast.LENGTH_SHORT
                ).show();
            });

            h.btnUse.setOnClickListener(v -> {
                stopSamplePlayback();
                FishVoiceStore.useVoiceForHome(FishVoiceExplorerActivity.this, item);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return displayedItems.size();
        }
    }
}
