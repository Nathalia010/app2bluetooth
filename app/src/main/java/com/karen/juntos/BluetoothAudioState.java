package com.karen.juntos;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Observes public audio profiles. Never initiates or disconnects a Bluetooth connection. */
final class BluetoothAudioState {
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Runnable changed;
    private final Map<Integer, BluetoothProfile> profiles = new HashMap<>();
    private final Set<Integer> requested = new HashSet<>();
    private final Map<String, BluetoothDevice> connected = new HashMap<>();
    private final Set<String> music = new HashSet<>(), calls = new HashSet<>(), leAudio = new HashSet<>();
    private boolean active;
    private int generation;
    private final Handler main = new Handler(Looper.getMainLooper());

    BluetoothAudioState(Context context, BluetoothAdapter adapter, Runnable changed) {
        this.context = context; this.adapter = adapter; this.changed = changed;
    }

    private BluetoothProfile.ServiceListener listener(int session) { return new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            main.post(() -> {
                if (!active || session != generation) { close(profile, proxy); return; }
                BluetoothProfile previous = profiles.put(profile, proxy);
                if (previous != null && previous != proxy) close(profile, previous);
                refresh(); changed.run();
            });
        }
        @Override public void onServiceDisconnected(int profile) {
            main.post(() -> {
                if (!active || session != generation) return;
                profiles.remove(profile); requested.remove(profile);
                refresh(); changed.run();
            });
        }
    }; }

    void start() {
        if (adapter == null) return;
        if (!active) { active = true; generation++; }
        request(BluetoothProfile.A2DP); request(BluetoothProfile.HEADSET);
        if (Build.VERSION.SDK_INT >= 33) request(BluetoothProfile.LE_AUDIO);
        refresh();
    }

    private void request(int profile) {
        if (!requested.add(profile)) return;
        try {
            if (!adapter.getProfileProxy(context, listener(generation), profile)) requested.remove(profile);
        } catch (SecurityException ex) { requested.remove(profile); }
    }

    void refresh() {
        connected.clear(); music.clear(); calls.clear(); leAudio.clear();
        if (!active) return;
        for (Map.Entry<Integer, BluetoothProfile> entry : new HashMap<>(profiles).entrySet()) {
            try {
                for (BluetoothDevice device : entry.getValue().getConnectedDevices()) {
                    String address = device.getAddress(); connected.put(address, device);
                    if (entry.getKey() == BluetoothProfile.A2DP) music.add(address);
                    else if (entry.getKey() == BluetoothProfile.HEADSET) calls.add(address);
                    else leAudio.add(address);
                }
            } catch (SecurityException ignored) { /* Revoked permission: do not claim any connection. */ }
        }
    }

    List<BluetoothDevice> connectedDevices() { return new ArrayList<>(connected.values()); }

    String summary(String address) {
        if (music.contains(address) && calls.contains(address)) return "Conectado para música y llamadas";
        if (music.contains(address)) return "Conectado para música";
        if (calls.contains(address)) return "Conectado para llamadas";
        if (leAudio.contains(address)) return "Conectado para LE Audio";
        return null;
    }

    void stop() {
        active = false;
        generation++;
        // Detach before closing: some Bluetooth implementations call listeners from close.
        Map<Integer, BluetoothProfile> closing = new HashMap<>(profiles);
        profiles.clear(); requested.clear(); connected.clear(); music.clear(); calls.clear(); leAudio.clear();
        for (Map.Entry<Integer, BluetoothProfile> entry : closing.entrySet()) close(entry.getKey(), entry.getValue());
    }

    private void close(int profile, BluetoothProfile proxy) {
        try { adapter.closeProfileProxy(profile, proxy); }
        catch (SecurityException ignored) { /* The system owns the connection and its cleanup. */ }
    }
}
