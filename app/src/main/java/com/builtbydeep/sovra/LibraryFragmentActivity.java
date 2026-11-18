package com.builtbydeep.sovra;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.builtbydeep.sovra.system.SovraMediaController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LibraryFragmentActivity extends Fragment {
    private static final String LIBRARY_PREFS = "sp2";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final List<SovraLibraryEntry> entries = new ArrayList<>();
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };
    private LinearLayout entryContainer;
    private TextView nowPlaying;
    private SeekBar seekBar;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private int currentIndex = -1;
    private boolean showFavorites;
    private boolean userSeeking;
    private boolean playerPrepared;
    private BroadcastReceiver mediaControlReceiver;
    private boolean receiverRegistered;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle state) {
        entryContainer = view.findViewById(R.id.library_entries);
        nowPlaying = view.findViewById(R.id.now_playing);
        seekBar = view.findViewById(R.id.library_seek);
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        view.findViewById(R.id.library_all).setOnClickListener(v -> {
            showFavorites = false;
            renderEntries();
        });
        view.findViewById(R.id.library_favorites).setOnClickListener(v -> {
            showFavorites = true;
            renderEntries();
        });
        view.findViewById(R.id.library_play_pause).setOnClickListener(v -> togglePlayback());
        view.findViewById(R.id.library_previous).setOnClickListener(v -> playRelative(-1));
        view.findViewById(R.id.library_next).setOnClickListener(v -> playRelative(1));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                userSeeking = fromUser;
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && playerPrepared) {
                    player.seekTo((int) (bar.getProgress() / 1000f * player.getDuration()));
                }
                userSeeking = false;
            }
        });
        createFocusListener();
        loadEntries();
        renderEntries();
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(progressUpdater);
        loadEntries();
        if (entryContainer != null) renderEntries();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mediaControlReceiver == null) {
            mediaControlReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    handleMediaControl(intent);
                }
            };
        }
        ContextCompat.registerReceiver(requireContext(), mediaControlReceiver,
                new IntentFilter(SovraMediaController.ACTION_MEDIA_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    @Override
    public void onStop() {
        if (receiverRegistered) {
            requireContext().unregisterReceiver(mediaControlReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(progressUpdater);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        releasePlayer();
        SovraMediaController.stopService(requireContext());
        super.onDestroyView();
    }

    private void loadEntries() {
        String json = requireContext().getSharedPreferences(LIBRARY_PREFS, 0)
                .getString("library_list", "[]");
        try {
            Type type = new TypeToken<ArrayList<SovraLibraryEntry>>() { }.getType();
            List<SovraLibraryEntry> loaded = gson.fromJson(json, type);
            entries.clear();
            if (loaded != null) entries.addAll(loaded);
        } catch (JsonSyntaxException exception) {
            Toast.makeText(requireContext(), "Audio library data is invalid",
                    Toast.LENGTH_LONG).show();
            entries.clear();
        }
    }

    private void renderEntries() {
        if (entryContainer == null) return;
        entryContainer.removeAllViews();
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            SovraLibraryEntry entry = entries.get(i);
            if (showFavorites && !entry.favorite) continue;
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setCardBackgroundColor(0xff1a1d2e);
            card.setStrokeColor(0xff2a2f45);
            card.setStrokeWidth(dp(1));
            LinearLayout content = new LinearLayout(requireContext());
            content.setPadding(dp(14), dp(12), dp(12), dp(8));
            content.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(requireContext());
            title.setText(entry.title == null ? "Sovra recording" : entry.title);
            title.setTextColor(0xfff5f6fa);
            title.setTextSize(17);
            content.addView(title);
            TextView details = new TextView(requireContext());
            details.setText(entry.voiceName == null ? "" : entry.voiceName);
            details.setTextColor(0xffa5a9b8);
            content.addView(details);
            LinearLayout actions = new LinearLayout(requireContext());
            MaterialButton play = new MaterialButton(requireContext());
            play.setText("Play");
            play.setOnClickListener(v -> playEntry(index));
            actions.addView(play);
            MaterialButton favorite = new MaterialButton(requireContext());
            favorite.setText(entry.favorite ? "★ Favorite" : "☆ Favorite");
            favorite.setOnClickListener(v -> {
                entry.favorite = !entry.favorite;
                persistEntries();
                renderEntries();
            });
            actions.addView(favorite);
            content.addView(actions);
            card.addView(content);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            entryContainer.addView(card, params);
        }
        if (entryContainer.getChildCount() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText(showFavorites ? "No favorites yet" : "Generated WAV files appear here");
            empty.setTextColor(0xffa5a9b8);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            entryContainer.addView(empty);
        }
    }

    private void playEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        releasePlayer();
        if (!requestFocus()) {
            Toast.makeText(requireContext(), "Audio focus is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        currentIndex = index;
        SovraLibraryEntry entry = entries.get(index);
        nowPlaying.setText(entry.title == null ? "Sovra recording" : entry.title);
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(requireContext(), Uri.parse(entry.path));
            player.setOnPreparedListener(mediaPlayer -> {
                playerPrepared = true;
                mediaPlayer.start();
                ((MaterialButton) requireView().findViewById(R.id.library_play_pause))
                        .setText("Pause");
                SovraMediaController.start(requireContext(), entry.title, entry.voiceName,
                        mediaPlayer.getDuration());
                publishState();
            });
            player.setOnCompletionListener(mediaPlayer -> playRelative(1));
            player.prepareAsync();
        } catch (Exception exception) {
            Toast.makeText(requireContext(), "Unable to play this audio: "
                    + exception.getMessage(), Toast.LENGTH_LONG).show();
            releasePlayer();
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (!entries.isEmpty()) playEntry(0);
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            requireView().findViewById(R.id.library_play_pause).setEnabled(true);
            ((MaterialButton) requireView().findViewById(R.id.library_play_pause)).setText("Play");
        } else {
            player.start();
            ((MaterialButton) requireView().findViewById(R.id.library_play_pause)).setText("Pause");
        }
        publishState();
    }

    private void playRelative(int offset) {
        if (entries.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + offset + entries.size()) % entries.size();
        playEntry(next);
    }

    private boolean requestFocus() {
        if (audioManager == null) return false;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build();
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void createFocusListener() {
        focusListener = change -> {
            if (player == null) return;
            if (change == AudioManager.AUDIOFOCUS_LOSS
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (player.isPlaying()) {
                    player.pause();
                    if (getView() != null) {
                        ((MaterialButton) requireView().findViewById(R.id.library_play_pause))
                                .setText("Play");
                    }
                    publishState();
                }
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && !player.isPlaying()) {
                player.start();
                publishState();
            }
        };
    }

    private void updateProgress() {
        if (player != null && playerPrepared && !userSeeking) {
            int duration = player.getDuration();
            if (duration > 0) {
                seekBar.setProgress((int) (player.getCurrentPosition() * 1000f / duration));
            }
        }
    }

    private void publishState() {
        if (player == null || currentIndex < 0 || currentIndex >= entries.size()) return;
        int state = player.isPlaying()
                ? android.media.session.PlaybackState.STATE_PLAYING
                : android.media.session.PlaybackState.STATE_PAUSED;
        SovraMediaController.updatePlaybackState(requireContext(), state,
                player.getCurrentPosition(), player.isPlaying() ? 1f : 0f);
    }

    private void handleMediaControl(Intent intent) {
        String control = intent.getStringExtra(SovraMediaController.EXTRA_CONTROL);
        if (SovraMediaController.ACTION_PLAY.equals(control)) {
            if (player == null && currentIndex >= 0) playEntry(currentIndex);
            else if (player != null && !player.isPlaying()) togglePlayback();
        } else if (SovraMediaController.ACTION_PAUSE.equals(control)) {
            if (player != null && player.isPlaying()) togglePlayback();
        } else if (SovraMediaController.ACTION_NEXT.equals(control)) {
            playRelative(1);
        } else if (SovraMediaController.ACTION_PREVIOUS.equals(control)) {
            playRelative(-1);
        } else if (SovraMediaController.ACTION_SEEK_TO.equals(control)
                && player != null && playerPrepared) {
            player.seekTo((int) Math.max(0,
                    intent.getLongExtra(SovraMediaController.EXTRA_POSITION, 0)));
        } else if (SovraMediaController.ACTION_STOP.equals(control)) {
            releasePlayer();
            currentIndex = -1;
            nowPlaying.setText("Nothing playing");
        }
    }

    private void persistEntries() {
        requireContext().getSharedPreferences(LIBRARY_PREFS, 0).edit()
                .putString("library_list", gson.toJson(entries)).apply();
    }

    private void releasePlayer() {
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
        }
        playerPrepared = false;
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
