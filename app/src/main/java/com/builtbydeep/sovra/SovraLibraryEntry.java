package com.builtbydeep.sovra;

import com.google.gson.annotations.SerializedName;

public final class SovraLibraryEntry {
    public String title;
    public String text;
    @SerializedName("full_text")
    public String fullText;
    public String path;
    public long duration;
    public long timestamp;
    @SerializedName("voice_name")
    public String voiceName;
    @SerializedName("is_favorite")
    public boolean favorite;

    public SovraLibraryEntry() {
    }

    public SovraLibraryEntry(
            String title, String text, String path, long duration,
            long timestamp, String voiceName) {
        this.title = title;
        this.text = text;
        this.fullText = text;
        this.path = path;
        this.duration = duration;
        this.timestamp = timestamp;
        this.voiceName = voiceName;
    }
}
