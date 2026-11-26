package com.builtbydeep.sovra;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class FishVoiceStore {
    public static final String PREF_NAME = "sp1";
    public static final String KEY_FAVORITES_JSON = "fish_favorite_voices_json";
    public static final String KEY_TEMP_USE_JSON = "fish_temp_use_voice_json";
    public static final String KEY_FAVORITES_REV = "fish_favorites_rev";
    public static final String KEY_SWITCH_TO_GENERATE = "switch_to_generate_tab";

    private static volatile Context appContext;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();

    private static final Pattern FEMALE_PATTERN = Pattern.compile(
            "\\b(female|woman|women|girl|girls|lady|actress|queen|princess|feminine|mother|sister|she|her)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MALE_PATTERN = Pattern.compile(
            "\\b(male|man|men|boy|boys|guy|actor|king|prince|masculine|father|brother|he|him|hero)\\b",
            Pattern.CASE_INSENSITIVE);

    private FishVoiceStore() {}

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static SharedPreferences prefs(Context context) {
        Context ctx = context != null ? context.getApplicationContext() : appContext;
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static int getRevision(Context context) {
        SharedPreferences sp = prefs(context);
        return sp == null ? 0 : sp.getInt(KEY_FAVORITES_REV, 0);
    }

    private static void bumpRevision(SharedPreferences sp) {
        if (sp == null) return;
        int next = sp.getInt(KEY_FAVORITES_REV, 0) + 1;
        sp.edit().putInt(KEY_FAVORITES_REV, next).apply();
    }

    public static final class FishVoiceItem {
        public final String id;
        public final String title;
        public final String gender;
        public final String locale;
        public final String region;
        public final String flag;
        public final long taskCount;
        public final String sampleUrl;
        public final List<String> tags;

        public FishVoiceItem(String id, String title, String gender, String locale,
                             String region, String flag, long taskCount,
                             String sampleUrl, List<String> tags) {
            this.id = id == null ? "" : id.trim();
            this.title = title == null || title.trim().isEmpty() ? "Fish Voice" : title.trim();
            this.gender = "Female".equalsIgnoreCase(gender) ? "Female" : "Male";
            this.locale = locale == null || locale.trim().isEmpty() ? "en-US" : locale.trim();
            this.region = region == null || region.trim().isEmpty() ? "English (US)" : region.trim();
            this.flag = flag == null || flag.trim().isEmpty() ? "🌐" : flag.trim();
            this.taskCount = Math.max(0L, taskCount);
            this.sampleUrl = sampleUrl == null ? "" : sampleUrl.trim();
            this.tags = tags == null ? Collections.<String>emptyList() : tags;
        }

        public String formattedUses() {
            if (taskCount >= 1_000_000L) {
                return String.format(Locale.US, "%.1fM uses", taskCount / 1_000_000.0);
            }
            if (taskCount >= 1_000L) {
                return String.format(Locale.US, "%.0fK uses", taskCount / 1_000.0);
            }
            if (taskCount > 0L) {
                return taskCount + " uses";
            }
            return "Live API";
        }

        public CloudTtsProvider.Voice toCloudVoice() {
            return new CloudTtsProvider.Voice(id, title, locale, gender, region);
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("title", title);
                obj.put("gender", gender);
                obj.put("locale", locale);
                obj.put("region", region);
                obj.put("flag", flag);
                obj.put("taskCount", taskCount);
                obj.put("sampleUrl", sampleUrl);
                JSONArray arr = new JSONArray();
                for (String t : tags) arr.put(t);
                obj.put("tags", arr);
            } catch (JSONException ignored) {}
            return obj;
        }

        public static FishVoiceItem fromJson(JSONObject obj) {
            if (obj == null) return null;
            String id = obj.optString("id", obj.optString("_id", "")).trim();
            if (id.isEmpty()) return null;
            String title = obj.optString("title", "Fish Voice");
            String gender = obj.optString("gender", "Male");
            String locale = obj.optString("locale", "en-US");
            String region = obj.optString("region", "English (US)");
            String flag = obj.optString("flag", "🌐");
            long taskCount = obj.optLong("taskCount", obj.optLong("task_count", 0L));
            String sampleUrl = obj.optString("sampleUrl", "");
            ArrayList<String> tagList = new ArrayList<>();
            JSONArray arr = obj.optJSONArray("tags");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String t = arr.optString(i, "").trim();
                    if (!t.isEmpty()) tagList.add(t);
                }
            }
            return new FishVoiceItem(id, title, gender, locale, region, flag, taskCount, sampleUrl, tagList);
        }
    }

    public static final class SearchPage {
        public final List<FishVoiceItem> items;
        public final int total;
        public final boolean hasMore;

        public SearchPage(List<FishVoiceItem> items, int total, boolean hasMore) {
            this.items = items;
            this.total = total;
            this.hasMore = hasMore;
        }
    }

    public static SearchPage fetchLiveModels(Context context, String query, String sortBy,
                                             String languageCode, String tagFilter,
                                             int pageNumber, int pageSize) throws IOException {
        SharedPreferences sp = prefs(context);
        String apiKey = sp == null ? "" : sp.getString(
                CloudTtsClients.getApiKeyPreference(CloudTtsClients.FISH), "").trim();

        Uri.Builder uri = Uri.parse("https://api.fish.audio/model").buildUpon()
                .appendQueryParameter("page_size", String.valueOf(Math.max(10, Math.min(50, pageSize))))
                .appendQueryParameter("page_number", String.valueOf(Math.max(1, pageNumber)));

        if (!TextUtils.isEmpty(sortBy)) {
            uri.appendQueryParameter("sort_by", sortBy.trim());
        }
        if (!TextUtils.isEmpty(query)) {
            uri.appendQueryParameter("title", query.trim());
        }
        if (!TextUtils.isEmpty(languageCode)) {
            uri.appendQueryParameter("language", languageCode.trim());
        }
        if (!TextUtils.isEmpty(tagFilter)) {
            uri.appendQueryParameter("tag", tagFilter.trim());
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(uri.build().toString())
                .header("Accept", "application/json")
                .get();
        if (!apiKey.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response res = HTTP.newCall(reqBuilder.build()).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("Fish Audio API HTTP " + res.code());
            }
            String raw = res.body().string();
            JSONObject root = new JSONObject(raw);
            int total = root.optInt("total", 0);
            JSONArray itemsArr = root.optJSONArray("items");
            ArrayList<FishVoiceItem> parsed = new ArrayList<>();
            if (itemsArr != null) {
                for (int i = 0; i < itemsArr.length(); i++) {
                    JSONObject itemObj = itemsArr.optJSONObject(i);
                    FishVoiceItem item = parseApiEntity(itemObj);
                    if (item != null) parsed.add(item);
                }
            }
            boolean hasMore = (pageNumber * pageSize) < total && !parsed.isEmpty();
            return new SearchPage(parsed, total, hasMore);
        } catch (JSONException e) {
            throw new IOException("Invalid Fish Audio catalog response.", e);
        }
    }

    private static FishVoiceItem parseApiEntity(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("_id", obj.optString("id", "")).trim();
        if (id.isEmpty()) return null;

        String title = obj.optString("title", "").trim();
        if (title.isEmpty()) title = "Voice " + id.substring(0, Math.min(6, id.length()));
        String description = obj.optString("description", "").trim();
        long taskCount = obj.optLong("task_count", 0L);

        ArrayList<String> tags = new ArrayList<>();
        JSONArray tagsArr = obj.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                String t = tagsArr.optString(i, "").trim();
                if (!t.isEmpty() && !tags.contains(t)) {
                    tags.add(t);
                }
            }
        }

        ArrayList<String> languages = new ArrayList<>();
        JSONArray langArr = obj.optJSONArray("languages");
        if (langArr != null) {
            for (int i = 0; i < langArr.length(); i++) {
                String l = langArr.optString(i, "").trim().toLowerCase(Locale.ROOT);
                if (!l.isEmpty()) languages.add(l);
            }
        }

        String sampleUrl = "";
        JSONArray samplesArr = obj.optJSONArray("samples");
        if (samplesArr != null && samplesArr.length() > 0) {
            for (int i = 0; i < samplesArr.length(); i++) {
                JSONObject sObj = samplesArr.optJSONObject(i);
                if (sObj != null) {
                    String audio = sObj.optString("audio", sObj.optString("url", sObj.optString("sample_audio", ""))).trim();
                    if (!audio.isEmpty()) {
                        sampleUrl = audio;
                        break;
                    }
                } else {
                    String directUrl = samplesArr.optString(i, "").trim();
                    if (directUrl.startsWith("http")) {
                        sampleUrl = directUrl;
                        break;
                    }
                }
            }
        }

        String gender = detectGender(title, description, tags);
        String[] localeRegionFlag = detectLocaleRegionFlag(languages, tags, title, description);
        return new FishVoiceItem(
                id,
                title,
                gender,
                localeRegionFlag[0],
                localeRegionFlag[1],
                localeRegionFlag[2],
                taskCount,
                sampleUrl,
                tags
        );
    }

    public static String detectGender(String title, String description, List<String> tags) {
        StringBuilder tagBlob = new StringBuilder();
        if (tags != null) {
            for (String t : tags) {
                tagBlob.append(' ').append(t);
            }
        }
        String tagText = tagBlob.toString();
        if (FEMALE_PATTERN.matcher(tagText).find() && !MALE_PATTERN.matcher(tagText).find()) {
            return "Female";
        }
        if (MALE_PATTERN.matcher(tagText).find() && !FEMALE_PATTERN.matcher(tagText).find()) {
            return "Male";
        }
        String combined = (title == null ? "" : title) + " " + (description == null ? "" : description);
        boolean hasFemale = FEMALE_PATTERN.matcher(combined).find();
        boolean hasMale = MALE_PATTERN.matcher(combined).find();
        if (hasFemale && !hasMale) return "Female";
        if (hasMale && !hasFemale) return "Male";
        if (FEMALE_PATTERN.matcher(tagText).find()) return "Female";
        return "Male";
    }

    private static String[] detectLocaleRegionFlag(List<String> languages, List<String> tags,
                                                   String title, String description) {
        String blob = ((title == null ? "" : title) + " "
                + (description == null ? "" : description) + " "
                + TextUtils.join(" ", tags == null ? Collections.<String>emptyList() : tags))
                .toLowerCase(Locale.ROOT);

        boolean hasHi = languages.contains("hi") || blob.contains("hindi") || blob.contains("indian") || blob.contains("india");
        if (hasHi) return new String[]{"hi-IN", "Hindi & Indian", "🇮🇳"};

        boolean hasJa = languages.contains("ja") || blob.contains("japanese") || blob.contains("japan") || blob.contains("anime");
        if (hasJa) return new String[]{"ja-JP", "Japanese", "🇯🇵"};

        boolean hasKo = languages.contains("ko") || blob.contains("korean") || blob.contains("korea");
        if (hasKo) return new String[]{"ko-KR", "Korean", "🇰🇷"};

        boolean hasZh = languages.contains("zh") || blob.contains("chinese") || blob.contains("mandarin");
        if (hasZh) return new String[]{"zh-CN", "Chinese", "🇨🇳"};

        boolean hasUk = blob.contains("british") || blob.contains("england") || blob.contains("london")
                || blob.matches(".*\\buk\\b.*");
        if (hasUk) return new String[]{"en-GB", "English (UK)", "🇬🇧"};

        boolean hasAu = blob.contains("australian") || blob.contains("aussie");
        if (hasAu) return new String[]{"en-AU", "English (AU)", "🇦🇺"};

        boolean hasEs = languages.contains("es") || blob.contains("spanish");
        if (hasEs) return new String[]{"es-ES", "Spanish", "🇪🇸"};

        boolean hasFr = languages.contains("fr") || blob.contains("french");
        if (hasFr) return new String[]{"fr-FR", "French", "🇫🇷"};

        boolean hasDe = languages.contains("de") || blob.contains("german");
        if (hasDe) return new String[]{"de-DE", "German", "🇩🇪"};

        boolean hasAr = languages.contains("ar") || blob.contains("arabic");
        if (hasAr) return new String[]{"ar-SA", "Arabic", "🇸🇦"};

        return new String[]{"en-US", "English (US)", "🇺🇸"};
    }

    // --- Favorites & Home Screen 2-Way Sync ---

    public static synchronized List<FishVoiceItem> getFavorites(Context context) {
        SharedPreferences sp = prefs(context);
        ArrayList<FishVoiceItem> list = new ArrayList<>();
        if (sp == null) return list;
        String raw = sp.getString(KEY_FAVORITES_JSON, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                FishVoiceItem item = FishVoiceItem.fromJson(arr.optJSONObject(i));
                if (item != null) list.add(item);
            }
        } catch (JSONException ignored) {}
        return list;
    }

    public static synchronized boolean isFavorite(Context context, String voiceId) {
        if (TextUtils.isEmpty(voiceId)) return false;
        for (FishVoiceItem item : getFavorites(context)) {
            if (item.id.equals(voiceId)) return true;
        }
        return false;
    }

    public static synchronized boolean toggleFavorite(Context context, FishVoiceItem target) {
        SharedPreferences sp = prefs(context);
        if (sp == null || target == null || TextUtils.isEmpty(target.id)) return false;
        List<FishVoiceItem> current = getFavorites(context);
        boolean removed = false;
        ArrayList<FishVoiceItem> updated = new ArrayList<>();
        for (FishVoiceItem item : current) {
            if (item.id.equals(target.id)) {
                removed = true;
            } else {
                updated.add(item);
            }
        }
        if (!removed) {
            updated.add(0, target);
        }
        JSONArray arr = new JSONArray();
        for (FishVoiceItem item : updated) {
            arr.put(item.toJson());
        }
        SharedPreferences.Editor ed = sp.edit().putString(KEY_FAVORITES_JSON, arr.toString());
        FishVoiceItem temp = getTempUseVoice(context);
        if (temp != null && temp.id.equals(target.id)) {
            ed.remove(KEY_TEMP_USE_JSON);
        }
        if (!removed && TextUtils.isEmpty(sp.getString("cloud_voice_fish", ""))) {
            ed.putString("cloud_voice_fish", target.id)
                    .putString("cloud_locale_fish", target.locale)
                    .putString("cloud_region_fish", target.region)
                    .putString("cloud_filter_region_fish", target.locale + "|" + target.region);
        }
        ed.apply();
        bumpRevision(sp);
        return !removed;
    }

    public static synchronized void useVoiceForHome(Context context, FishVoiceItem item) {
        SharedPreferences sp = prefs(context);
        if (sp == null || item == null || TextUtils.isEmpty(item.id)) return;
        SharedPreferences.Editor ed = sp.edit()
                .putString("active_model", "cloud-fish")
                .putString("active_tokens", "")
                .putString("active_model_name", "Fish Audio")
                .putString("active_model_type", "cloud-fish")
                .putString("active_voices_bin", "")
                .putString("active_language", item.locale)
                .putString("active_supertonic_dir", "")
                .putInt("active_speaker_count", 1)
                .putString("cloud_voice_fish", item.id)
                .putString("cloud_locale_fish", item.locale)
                .putString("cloud_region_fish", item.region)
                .putString("cloud_filter_region_fish", item.locale + "|" + item.region)
                .putBoolean(KEY_SWITCH_TO_GENERATE, true);

        if (isFavorite(context, item.id)) {
            ed.remove(KEY_TEMP_USE_JSON);
        } else {
            ed.putString(KEY_TEMP_USE_JSON, item.toJson().toString());
        }
        ed.apply();
        bumpRevision(sp);
    }

    public static synchronized FishVoiceItem getTempUseVoice(Context context) {
        SharedPreferences sp = prefs(context);
        if (sp == null) return null;
        String raw = sp.getString(KEY_TEMP_USE_JSON, "");
        if (TextUtils.isEmpty(raw)) return null;
        try {
            return FishVoiceItem.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return null;
        }
    }

    public static synchronized void clearTempUseVoice(Context context) {
        SharedPreferences sp = prefs(context);
        if (sp != null && sp.contains(KEY_TEMP_USE_JSON)) {
            sp.edit().remove(KEY_TEMP_USE_JSON).apply();
            bumpRevision(sp);
        }
    }

    public static synchronized String getSavedVoiceDisplayName(Context context) {
        SharedPreferences sp = prefs(context);
        if (sp == null) return "";
        String savedId = sp.getString("cloud_voice_fish", "");
        if (TextUtils.isEmpty(savedId)) return "";
        for (CloudTtsProvider.Voice v : getHomeDropdownVoices()) {
            if (v.id.equals(savedId)) {
                return v.displayName();
            }
        }
        return savedId;
    }

    public static synchronized List<CloudTtsProvider.Voice> getHomeDropdownVoices() {
        ArrayList<CloudTtsProvider.Voice> result = new ArrayList<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();

        List<FishVoiceItem> favorites = getFavorites(appContext);
        for (FishVoiceItem fav : favorites) {
            if (seenIds.add(fav.id)) {
                result.add(fav.toCloudVoice());
            }
        }

        FishVoiceItem temp = getTempUseVoice(appContext);
        if (temp != null && seenIds.add(temp.id)) {
            result.add(temp.toCloudVoice());
        }

        if (!result.isEmpty()) {
            return result;
        }

        return Arrays.asList(
                new CloudTtsProvider.Voice("933563129e564b19a115bedd57b7406a", "Sarah (Warm Female)", "en-US", "Female", "English (US)"),
                new CloudTtsProvider.Voice("7f92f8afb8ec43bf81429cc1c9199cb1", "Energetic Male", "en-US", "Male", "English (US)"),
                new CloudTtsProvider.Voice("0de8162a9e384545a0106046b57488a7", "Narration (Hindi)", "hi-IN", "Male", "Hindi & Indian")
        );
    }

    public static List<FishVoiceItem> getOfflineFallbackCatalog() {
        return Arrays.asList(
                new FishVoiceItem("933563129e564b19a115bedd57b7406a", "Sarah (Warm Female)", "Female", "en-US", "English (US)", "🇺🇸", 3100000L, "", Arrays.asList("Female", "Warm")),
                new FishVoiceItem("7f92f8afb8ec43bf81429cc1c9199cb1", "Energetic Male", "Male", "en-US", "English (US)", "🇺🇸", 1900000L, "", Arrays.asList("Male", "Energetic")),
                new FishVoiceItem("b545c585f631496c914815291da4e893", "Storyteller Calm", "Male", "en-US", "English (US)", "🇺🇸", 1450000L, "", Arrays.asList("Male", "Narration")),
                new FishVoiceItem("0de8162a9e384545a0106046b57488a7", "Narration (Hindi)", "Male", "hi-IN", "Hindi & Indian", "🇮🇳", 980000L, "", Arrays.asList("Hindi", "Narration")),
                new FishVoiceItem("4d7609058bd34213b1378b29efbde1f1", "Girl Hindi (Expressive)", "Female", "hi-IN", "Hindi & Indian", "🇮🇳", 840000L, "", Arrays.asList("Female", "Hindi")),
                new FishVoiceItem("8ec9973300874562962bbcb300f82213", "Guyzo (Heroic Male)", "Male", "en-US", "English (US)", "🇺🇸", 890000L, "", Arrays.asList("Male", "Epic"))
        );
    }
}
