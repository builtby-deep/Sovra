package com.builtbydeep.sovra.system;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Handler;

import androidx.core.content.ContextCompat;

@SuppressWarnings("deprecation")
public final class SovraMediaController {
    public static final String ACTION_START =
            "com.builtbydeep.sovra.action.MEDIA_START";
    public static final String ACTION_UPDATE =
            "com.builtbydeep.sovra.action.MEDIA_UPDATE";
    public static final String ACTION_STOP =
            "com.builtbydeep.sovra.action.MEDIA_STOP";
    public static final String ACTION_PLAY =
            "com.builtbydeep.sovra.action.MEDIA_PLAY";
    public static final String ACTION_PAUSE =
            "com.builtbydeep.sovra.action.MEDIA_PAUSE";
    public static final String ACTION_NEXT =
            "com.builtbydeep.sovra.action.MEDIA_NEXT";
    public static final String ACTION_PREVIOUS =
            "com.builtbydeep.sovra.action.MEDIA_PREVIOUS";
    public static final String ACTION_SEEK_TO =
            "com.builtbydeep.sovra.action.MEDIA_SEEK_TO";
    public static final String ACTION_MEDIA_CONTROL =
            "com.builtbydeep.sovra.action.MEDIA_CONTROL";
    public static final String EXTRA_CONTROL =
            "com.builtbydeep.sovra.extra.MEDIA_CONTROL";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_PLAYBACK_STATE = "playback_state";
    public static final String EXTRA_PLAYBACK_SPEED = "playback_speed";

    private final MediaController controller;

    public SovraMediaController(Context context, MediaSession.Token sessionToken) {
        if (context == null || sessionToken == null) {
            throw new IllegalArgumentException("Context and media session token are required");
        }
        controller = new MediaController(context.getApplicationContext(), sessionToken);
    }

    public MediaController getMediaController() {
        return controller;
    }

    public void play() {
        controller.getTransportControls().play();
    }

    public void pause() {
        controller.getTransportControls().pause();
    }

    public void stop() {
        controller.getTransportControls().stop();
    }

    public void skipToNext() {
        controller.getTransportControls().skipToNext();
    }

    public void skipToPrevious() {
        controller.getTransportControls().skipToPrevious();
    }

    public void seekTo(long positionMs) {
        controller.getTransportControls().seekTo(Math.max(0, positionMs));
    }

    public void registerCallback(MediaController.Callback callback, Handler handler) {
        controller.registerCallback(callback, handler);
    }

    public void unregisterCallback(MediaController.Callback callback) {
        controller.unregisterCallback(callback);
    }

    public static void start(Context context, String title, String artist, long durationMs) {
        Intent intent = new Intent(context, SovraMediaService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_DURATION, Math.max(0, durationMs));
        ContextCompat.startForegroundService(context, intent);
    }

    public static void updateMetadata(
            Context context, String title, String artist, long durationMs) {
        Intent intent = new Intent(context, SovraMediaService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_DURATION, Math.max(0, durationMs));
        ContextCompat.startForegroundService(context, intent);
    }

    public static void updatePlaybackState(
            Context context, int state, long positionMs, float speed) {
        Intent intent = new Intent(context, SovraMediaService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_PLAYBACK_STATE, state)
                .putExtra(EXTRA_POSITION, Math.max(0, positionMs))
                .putExtra(EXTRA_PLAYBACK_SPEED, Float.isFinite(speed) ? speed : 1.0f);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, SovraMediaService.class));
    }

}
