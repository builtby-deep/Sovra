package com.builtbydeep.sovra.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.IBinder;

import androidx.core.app.ServiceCompat;

@SuppressWarnings("deprecation")
public final class SovraMediaService extends Service {
    public static final String SESSION_TAG = "SovraMediaSession";
    public static final String CHANNEL_ID = "SovraMedia";
    private static final int NOTIFICATION_ID = 5201;
    private static final String CHANNEL_NAME = "SovraMedia";
    private static final long SESSION_ACTIONS =
            PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SEEK_TO;

    private final IBinder binder = new LocalBinder();
    private MediaSession mediaSession;
    private String title = "Sovra";
    private String artist = "";
    private long durationMs;
    private long positionMs;
    private int playbackState = PlaybackState.STATE_NONE;
    private float playbackSpeed = 1.0f;
    private boolean foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mediaSession = new MediaSession(this, SESSION_TAG);
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                handleControl(SovraMediaController.ACTION_PLAY);
            }

            @Override
            public void onPause() {
                handleControl(SovraMediaController.ACTION_PAUSE);
            }

            @Override
            public void onStop() {
                handleControl(SovraMediaController.ACTION_STOP);
            }

            @Override
            public void onSkipToNext() {
                handleControl(SovraMediaController.ACTION_NEXT);
            }

            @Override
            public void onSkipToPrevious() {
                handleControl(SovraMediaController.ACTION_PREVIOUS);
            }

            @Override
            public void onSeekTo(long position) {
                positionMs = Math.max(0, position);
                publishPlaybackState();
                refreshForegroundNotification();
                publishControl(SovraMediaController.ACTION_SEEK_TO);
            }
        });
        mediaSession.setActive(true);
        publishPlaybackState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (SovraMediaController.ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }
        if (SovraMediaController.ACTION_PLAY.equals(action)
                || SovraMediaController.ACTION_PAUSE.equals(action)
                || SovraMediaController.ACTION_NEXT.equals(action)
                || SovraMediaController.ACTION_PREVIOUS.equals(action)) {
            handleControl(action);
        } else {
            updateFromIntent(intent);
            startForegroundNow();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    private void updateFromIntent(Intent intent) {
        if (intent != null) {
            String incomingTitle = intent.getStringExtra(SovraMediaController.EXTRA_TITLE);
            String incomingArtist = intent.getStringExtra(SovraMediaController.EXTRA_ARTIST);
            if (incomingTitle != null) {
                title = incomingTitle;
            }
            if (incomingArtist != null) {
                artist = incomingArtist;
            }
            if (intent.hasExtra(SovraMediaController.EXTRA_DURATION)) {
                durationMs = Math.max(0,
                        intent.getLongExtra(SovraMediaController.EXTRA_DURATION, 0));
            }
            if (intent.hasExtra(SovraMediaController.EXTRA_POSITION)) {
                positionMs = Math.max(0,
                        intent.getLongExtra(SovraMediaController.EXTRA_POSITION, 0));
            }
            if (intent.hasExtra(SovraMediaController.EXTRA_PLAYBACK_STATE)) {
                playbackState = intent.getIntExtra(
                        SovraMediaController.EXTRA_PLAYBACK_STATE, PlaybackState.STATE_PAUSED);
            }
            if (intent.hasExtra(SovraMediaController.EXTRA_PLAYBACK_SPEED)) {
                float speed = intent.getFloatExtra(
                        SovraMediaController.EXTRA_PLAYBACK_SPEED, 1.0f);
                playbackSpeed = Float.isFinite(speed) ? speed : 1.0f;
            }
        }
        publishMetadata();
        publishPlaybackState();
        refreshForegroundNotification();
    }

    private void handleControl(String action) {
        if (SovraMediaController.ACTION_PLAY.equals(action)) {
            playbackState = PlaybackState.STATE_PLAYING;
            playbackSpeed = 1.0f;
        } else if (SovraMediaController.ACTION_PAUSE.equals(action)) {
            playbackState = PlaybackState.STATE_PAUSED;
            playbackSpeed = 0.0f;
        } else if (SovraMediaController.ACTION_STOP.equals(action)) {
            playbackState = PlaybackState.STATE_STOPPED;
            playbackSpeed = 0.0f;
        }
        publishPlaybackState();
        refreshForegroundNotification();
        publishControl(action);
        if (SovraMediaController.ACTION_STOP.equals(action)) {
            stopPlayback();
        }
    }

    private void publishControl(String control) {
        Intent event = new Intent(SovraMediaController.ACTION_MEDIA_CONTROL)
                .setPackage(getPackageName())
                .putExtra(SovraMediaController.EXTRA_CONTROL, control)
                .putExtra(SovraMediaController.EXTRA_POSITION, positionMs);
        sendBroadcast(event);
    }

    private void publishMetadata() {
        if (mediaSession == null) {
            return;
        }
        MediaMetadata metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void publishPlaybackState() {
        if (mediaSession == null) {
            return;
        }
        PlaybackState state = new PlaybackState.Builder()
                .setActions(SESSION_ACTIONS)
                .setState(playbackState, positionMs, playbackSpeed)
                .build();
        mediaSession.setPlaybackState(state);
    }

    private void refreshForegroundNotification() {
        if (foreground) {
            startForegroundNow();
        }
    }

    private void startForegroundNow() {
        Notification notification = buildNotification();
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        foreground = true;
    }

    private Notification buildNotification() {
        boolean playing = playbackState == PlaybackState.STATE_PLAYING;
        PendingIntent previous = servicePendingIntent(
                SovraMediaController.ACTION_PREVIOUS, 1);
        PendingIntent playPause = servicePendingIntent(
                playing ? SovraMediaController.ACTION_PAUSE : SovraMediaController.ACTION_PLAY, 2);
        PendingIntent next = servicePendingIntent(SovraMediaController.ACTION_NEXT, 3);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist.isEmpty() ? "Sovra" : artist)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Previous", previous)
                .addAction(playing ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", playPause)
                .addAction(android.R.drawable.ic_media_next, "Next", next);
        if (mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(mediaSession.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, SovraMediaService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void stopPlayback() {
        playbackState = PlaybackState.STATE_STOPPED;
        playbackSpeed = 0.0f;
        publishPlaybackState();
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
        }
        stopSelf();
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            throw new IllegalStateException("NotificationManager is unavailable");
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Sovra media playback controls");
        notificationManager.createNotificationChannel(channel);
    }

    public final class LocalBinder extends Binder {
        public MediaSession.Token getSessionToken() {
            return mediaSession == null ? null : mediaSession.getSessionToken();
        }

        public void updateMetadata(String trackTitle, String trackArtist, long trackDurationMs) {
            title = trackTitle == null ? "Sovra" : trackTitle;
            artist = trackArtist == null ? "" : trackArtist;
            durationMs = Math.max(0, trackDurationMs);
            publishMetadata();
            refreshForegroundNotification();
        }

        public void updatePlaybackState(int state, long position, float speed) {
            playbackState = state;
            positionMs = Math.max(0, position);
            playbackSpeed = Float.isFinite(speed) ? speed : 1.0f;
            publishPlaybackState();
            refreshForegroundNotification();
        }
    }
}
