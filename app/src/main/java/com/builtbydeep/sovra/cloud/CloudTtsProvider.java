package com.builtbydeep.sovra.cloud;

import android.content.Context;

public interface CloudTtsProvider {
    String getId();
    String getName();
    String getSubtitle();
    String getBadge();
    boolean requiresApiKey();
    byte[] synthesize(Context context, String text, CloudVoiceOptions options) throws Exception;
    default long getRpmLimit() { return 350L; }
}
