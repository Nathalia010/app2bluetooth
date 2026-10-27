package com.karen.juntos;

import android.content.SharedPreferences;

/** Shared by the activity and notification; values belong to exact route IDs. */
final class OutputVolumeMemory {
    private final SharedPreferences preferences;

    OutputVolumeMemory(SharedPreferences preferences) { this.preferences = preferences; }

    void remember(String id, int value, int maximum) {
        if (maximum <= 0) return;
        preferences.edit().putInt("level:" + id,
                Math.round(10000f * Math.max(0, Math.min(maximum, value)) / maximum)).apply();
    }

    int restoredVolume(String id, int maximum, int fallback) {
        int saved = preferences.getInt("level:" + id, -1);
        return saved < 0 ? Math.max(0, Math.min(maximum, fallback))
                : Math.round(Math.max(0, Math.min(10000, saved)) * maximum / 10000f);
    }

    boolean muted(String id) { return preferences.getBoolean("muted:" + id, false); }

    void setMuted(String id, boolean muted) {
        preferences.edit().putBoolean("muted:" + id, muted).apply();
    }

    void requestMute(String id, boolean muted, int target, long now) {
        preferences.edit().putBoolean("wanted:" + id, muted).putInt("target:" + id, target)
                .putLong("deadline:" + id, now + 3000).apply();
    }

    void cancelRequest(String id) { preferences.edit().remove("deadline:" + id).apply(); }

    boolean pending(String id, long now) {
        return preferences.getLong("deadline:" + id, 0) > now;
    }

    void reconcile(String id, int actual, long now) {
        if (pending(id, now) && actual == preferences.getInt("target:" + id, -1)) {
            setMuted(id, preferences.getBoolean("wanted:" + id, false));
            cancelRequest(id);
        }
        if (!pending(id, now) && actual > 0 && muted(id)) setMuted(id, false);
    }
}
