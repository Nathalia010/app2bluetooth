package com.karen.juntos;

import android.content.SharedPreferences;
import com.karen.juntos.AudioOutputs.Entry;
import java.lang.reflect.Proxy;
import java.util.*;

/** JVM regression checks using the real state classes and an in-memory preference store. */
public final class OutputControlsRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static SharedPreferences preferences() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { values.put((String) args[0], args[1]); return proxy; }
                    if (name.equals("remove")) { values.remove(args[0]); return proxy; }
                    if (name.equals("apply")) return null;
                    if (name.equals("commit")) return true;
                    throw new UnsupportedOperationException(name);
                });
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) return editor;
                    if (name.startsWith("get") && args != null && args.length == 2)
                        return values.getOrDefault(args[0], args[1]);
                    throw new UnsupportedOperationException(name);
                });
    }

    private static Entry entry(String id, boolean selected, boolean known, boolean editable, boolean connected) {
        return new Entry(id, "Same display name", "", selected, known, 0, 60, 100, editable, true, connected);
    }

    public static void main(String[] args) {
        SharedPreferences saved = preferences();
        OutputVolumeMemory screen = new OutputVolumeMemory(saved);
        OutputVolumeMemory notification = new OutputVolumeMemory(saved);
        screen.remember("speaker", 9, 15);
        screen.remember("headphones", 5, 20);
        screen.requestMute("speaker", true, 0, 1000);
        notification.reconcile("speaker", 9, 1100);
        check(!notification.muted("speaker"), "Do not show a mute before Android confirms zero");
        notification.reconcile("speaker", 0, 1200);
        check(notification.muted("speaker"), "Notification must observe the confirmed screen mute");
        check(notification.restoredVolume("speaker", 15, 0) == 9, "Muting must retain the previous level");
        check(notification.restoredVolume("speaker", 100, 0) == 60, "Preserve relative level if the scale changes");
        check(notification.restoredVolume("headphones", 100, 0) == 25, "Keep separate volumes for separate IDs");
        notification.requestMute("speaker", false, 9, 1300);
        screen.reconcile("speaker", 0, 1400);
        check(screen.muted("speaker"), "Wait for restoration to be confirmed");
        screen.reconcile("speaker", 9, 1500);
        check(!screen.muted("speaker"), "Screen must observe notification unmute");
        screen.requestMute("speaker", true, 0, 2000);
        screen.reconcile("speaker", 9, 6000);
        check(!screen.muted("speaker"), "Rejected or expired mute must not become a false check state");
        screen.setMuted("speaker", true);
        screen.reconcile("speaker", 3, 7000);
        check(!screen.muted("speaker"), "Physical volume keys must clear a stale mute");
        screen.remember("zero", 0, 15);
        check(notification.restoredVolume("zero", 100, 99) == 0, "An intentionally saved zero must remain zero");
        check(notification.restoredVolume("new", 15, 7) == 7, "Unknown IDs must not inherit another device's volume");

        SharedPreferences positions = preferences();
        OutputPanel panel = new OutputPanel(positions);
        List<Entry> empty = panel.select(Collections.emptyList());
        check(empty.size() == 2, "Only two positions exist");
        for (Entry slot : empty) {
            check(!slot.connected && !slot.canToggle, "Empty slots must be disabled");
            check(AudioOutputs.volumeText(slot).equals("0%"), "Empty slots must display zero");
        }
        Entry a = entry("a", true, true, true, true);
        Entry b = entry("b", false, true, false, true);
        Entry c = entry("c", false, false, false, true);
        panel.select(Arrays.asList(a, b, c));
        List<Entry> reordered = new OutputPanel(positions).select(Arrays.asList(c, b, a));
        check(reordered.get(0).id.equals("a") && reordered.get(1).id.equals("b"), "Notification must keep the same numbering after a reorder");
        check(reordered.size() == 2, "Extra devices must not create another position");
        List<Entry> disconnected = panel.select(Arrays.asList(a));
        check(disconnected.get(1).id.equals("empty:1") && !disconnected.get(1).connected, "A missing device must leave an anonymous slot");
        check(disconnected.get(1).name.equals("Conectar nuevo dispositivo"), "A missing device must not retain its name");
        check(!positions.getString("route1", "").equals("b"), "A disconnected identity must be removed");
        Entry d = entry("d", true, true, true, true);
        List<Entry> replaced = panel.select(Arrays.asList(a, c, d));
        check(replaced.get(1).id.equals("d"), "A new connection must fill a disconnected slot");
        Entry e = entry("e", true, true, true, true);
        List<Entry> active = panel.select(Arrays.asList(a, c, e));
        check(active.stream().anyMatch(slot -> slot.id.equals("e")), "A newly selected route must not stay hidden behind inactive routes");
        check(AudioOutputs.displayedVolume(b) == 0 && AudioOutputs.volumeText(b).equals("0%"), "A known unselected output displays zero");
        check(AudioOutputs.volumeText(c).equals("—"), "An unconfirmed output must not claim to be muted");
        check(AudioOutputs.volumeText(entry("group", true, true, false, true)).equals("—"), "Do not expose a group volume as an individual volume");
        SharedPreferences migrated = preferences();
        migrated.edit().putString("route2", "legacy").putString("name2", "Old device").putInt("icon2", 1).apply();
        OutputPanel twoPanel = new OutputPanel(migrated);
        twoPanel.select(Arrays.asList(a, b, c));
        List<Entry> two = twoPanel.select(Arrays.asList(a, c));
        check(two.size() == 2 && two.get(1).id.equals("c"), "Old third device must migrate into a visible empty slot");
        check(migrated.getString("route2", "removed").equals("removed") && migrated.getString("name2", "removed").equals("removed") && migrated.getInt("icon2", -1) == -1, "Legacy third position must be deleted");
        check(twoPanel.select(Arrays.asList(a, b, c)).size() == 2, "Only two slots may be exposed");
        check(twoPanel.select(Collections.emptyList()).stream().noneMatch(slot -> slot.connected), "Both slots clear on disconnect");
        System.out.println("PASS: volume memory, stable positions, disconnection, two-slot migration and unknown states");
    }
}
