package com.karen.juntos;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import com.karen.juntos.AudioOutputs.Entry;

/** Keeps the same two numbered outputs in the screen and the notification. */
final class OutputPanel {
    private final SharedPreferences panelPreferences;
    OutputPanel(SharedPreferences preferences) { panelPreferences = preferences; }
    /** Two shared positions, stable across refreshes, activity and notification. */
    List<Entry> select(List<Entry> current) {
        final int count = 2;
        List<Entry> panel = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = panelPreferences.getString("route" + i, "empty:" + i);
            Entry found = null;
            for (Entry entry : current) if (entry.connected && entry.id.equals(id)) { found = entry; break; }
            panel.add(found);
        }
        for (Entry entry : current) {
            boolean alreadyShown = false;
            for (Entry shown : panel) if (shown != null && shown.id.equals(entry.id)) alreadyShown = true;
            if (alreadyShown || !entry.connected) continue;
            int position = -1;
            for (int i = 0; i < count; i++) if (panel.get(i) == null || !panel.get(i).connected) { position = i; break; }
            if (position < 0 && entry.selected)
                for (int i = 0; i < count; i++) if (!panel.get(i).selected) { position = i; break; }
            if (position >= 0) panel.set(position, entry);
        }
        SharedPreferences.Editor edit = panelPreferences.edit().remove("route2").remove("name2").remove("icon2");
        for (int i = 0; i < count; i++) {
            Entry entry = panel.get(i);
            if (entry != null) {
                edit.putString("route" + i, entry.id).putString("name" + i, entry.name).putInt("icon" + i, entry.icon);
            } else {
                edit.remove("route" + i).remove("name" + i).remove("icon" + i);
                panel.set(i, new Entry("empty:" + i,
                        "Conectar nuevo dispositivo",
                        "Sin dispositivo conectado", false, true,
                        R.drawable.ic_bluetooth, 0, 100, false, false, false));
            }
        }
        edit.apply();
        return panel;
    }

}
