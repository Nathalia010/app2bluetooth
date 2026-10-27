package com.karen.juntos;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.AudioManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;
import android.media.RouteDiscoveryPreference;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual routing selections, separate from pairing, connection and saved favorites. */
final class AudioOutputs {
    static final class Entry {
        final String id, name, detail;
        final boolean selected, known;
        final int icon;
        final int volume, volumeMax;
        final boolean volumeEditable, canToggle;
        final boolean connected;
        Entry(String id, String name, String detail, boolean selected, boolean known, int icon) {
            this(id, name, detail, selected, known, icon, 0, 0, false, false);
        }
        Entry(String id, String name, String detail, boolean selected, boolean known, int icon,
                int volume, int volumeMax, boolean volumeEditable, boolean canToggle) {
            this(id, name, detail, selected, known, icon, volume, volumeMax, volumeEditable, canToggle, true);
        }
        Entry(String id, String name, String detail, boolean selected, boolean known, int icon,
                int volume, int volumeMax, boolean volumeEditable, boolean canToggle, boolean connected) {
            this.id = id; this.name = name; this.detail = detail;
            this.selected = selected; this.known = known; this.icon = icon;
            this.volume = volume; this.volumeMax = volumeMax;
            this.volumeEditable = volumeEditable; this.canToggle = canToggle;
            this.connected = connected;
        }
    }

    private final Context context;
    private final Runnable changed;
    private Backend backend;
    private BluetoothAudioState observedConnections;
    private final SharedPreferences panelPreferences;
    private final OutputVolumeMemory volumeMemory;
    AudioOutputs(Context context, Runnable changed) {
        this.context = context; this.changed = changed;
        panelPreferences = context.getSharedPreferences("output_panel", Context.MODE_PRIVATE);
        volumeMemory = new OutputVolumeMemory(context.getSharedPreferences("output_volumes", Context.MODE_PRIVATE));
    }
    void start() {
        if (Build.VERSION.SDK_INT < 30 || backend != null) return;
        try { backend = new Backend(context, changed); }
        catch (RuntimeException ignored) { backend = null; }
    }
    void stop() {
        try { if (Build.VERSION.SDK_INT >= 30 && backend != null) backend.stop(); }
        catch (RuntimeException ignored) { /* Cleanup must not crash the activity or service. */ }
        backend = null;
    }

    List<Entry> entries(BluetoothAudioState connections) {
        observedConnections = connections;
        List<Entry> entries = new ArrayList<>();
        List<BluetoothDevice> connected = connections.connectedDevices();
        MediaOutputSnapshot media = MediaOutputSnapshot.read(context);
        if (Build.VERSION.SDK_INT >= 30 && backend != null) {
            try { entries.addAll(backend.entries(connected.size() > 1)); }
            catch (RuntimeException ignored) { /* Unknown routing state must not become a tick. */ }
        }
        for (BluetoothDevice device : connected) {
            String name = null;
            try { name = device.getName(); } catch (SecurityException ignored) { }
            if (name == null || name.trim().isEmpty()) name = "Equipo Bluetooth";
            boolean alreadyShown = false;
            // Deduplicate labels only. All actionable rows still use the exact system route ID.
            // Never infer the selected state of a Bluetooth address from its display name.
            int icon = deviceIcon(device);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (entry.id.startsWith("bt:")) continue;
                if (!entry.name.equalsIgnoreCase(name)) continue;
                if (connected.size() > 1 && !entry.canToggle) {
                    // Replace a non-actionable label with the physical device below, without
                    // transferring route control or inferring identity from the display name.
                    entries.remove(i--); continue;
                }
                alreadyShown = true;
                // Display metadata only; selection and controls retain the exact route identity.
                if (entry.icon == R.drawable.ic_bluetooth && icon != R.drawable.ic_bluetooth)
                    entries.set(i, new Entry(entry.id, entry.name, entry.detail, entry.selected, entry.known, icon,
                            entry.volume, entry.volumeMax, entry.volumeEditable, entry.canToggle, entry.connected));
            }
            if (!alreadyShown) {
                boolean detected = media.includes(device.getAddress());
                entries.add(new Entry("bt:" + device.getAddress(), name,
                        detected ? "Salida multimedia detectada · control individual no disponible"
                                : connections.summary(device.getAddress()) + " · selección sin confirmar",
                        detected, detected, icon));
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.selected && entry.volumeEditable)
                volumeMemory.reconcile(entry.id, entry.volume, System.currentTimeMillis());
            boolean muted = entry.selected && entry.volumeEditable && entry.volume == 0 && volumeMemory.muted(entry.id);
            if (muted) entries.set(i, new Entry(entry.id, entry.name, "Silenciado desde Juntos",
                    false, true, entry.icon, 0, entry.volumeMax, false, entry.volumeEditable || entry.canToggle, entry.connected));
            else if (entry.volumeEditable) entries.set(i, new Entry(entry.id, entry.name, entry.detail,
                    entry.selected, entry.known, entry.icon, entry.volume, entry.volumeMax, true, true, entry.connected));
        }
        return entries;
    }

    List<Entry> panelEntries(BluetoothAudioState connections) {
        List<Entry> candidates = entries(connections);
        if (connections.connectedDevices().isEmpty()) candidates.clear();
        else candidates.removeIf(entry -> entry.icon == R.drawable.ic_phone);
        return new OutputPanel(panelPreferences).select(candidates);
    }
    static int displayedVolume(Entry entry) { return !entry.connected || entry.known && !entry.selected ? 0 : entry.volume; }

    static String volumeText(Entry entry) {
        if (!entry.connected || entry.known && !entry.selected) return "0%";
        return entry.volumeEditable && entry.volumeMax > 0
                ? Math.round(100f * entry.volume / entry.volumeMax) + "%" : "—";
    }

    private static int deviceIcon(BluetoothDevice device) {
            try {
                BluetoothClass info = device.getBluetoothClass();
                if (info == null) return R.drawable.ic_bluetooth;
                switch (info.getDeviceClass()) {
                    case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                    case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                    case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES: return R.drawable.ic_headphones;
                    case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                    case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                    case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO: return R.drawable.ic_speaker;
                    case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                    case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR: return R.drawable.ic_tv;
                }
                if (info.getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE) return R.drawable.ic_watch;
            } catch (SecurityException ignored) { }
            return R.drawable.ic_bluetooth;
    }

    boolean requestToggle(String routeId) {
        if (Build.VERSION.SDK_INT < 30 || backend == null || routeId == null) return false;
        try {
            // A sole controllable output can be silenced without disconnecting it.
            // A grouped session is never muted as if it were one physical member.
            Entry entry = currentEntry(routeId);
            if (entry != null && entry.selected && entry.volumeEditable) {
                long now = System.currentTimeMillis();
                volumeMemory.reconcile(routeId, entry.volume, now);
                if (volumeMemory.pending(routeId, now)) return false;
                boolean mute = !volumeMemory.muted(routeId);
                int target = mute ? 0 : volumeMemory.restoredVolume(routeId, entry.volumeMax, entry.volume);
                if (mute) volumeMemory.remember(routeId, entry.volume, entry.volumeMax);
                volumeMemory.requestMute(routeId, mute, target, now);
                if (!backend.setVolume(routeId, target)) { volumeMemory.cancelRequest(routeId); return false; }
                Entry actual = currentEntry(routeId);
                if (actual != null) volumeMemory.reconcile(routeId, actual.volume, now);
                changed.run();
                return true;
            }
            return backend.requestToggle(routeId);
        }
        catch (RuntimeException ignored) { return false; }
    }

    boolean setVolume(String routeId, int volume) {
        if (Build.VERSION.SDK_INT < 30 || backend == null) return false;
        try {
            if (volumeMemory.muted(routeId) || volumeMemory.pending(routeId, System.currentTimeMillis())) return false;
            Entry before = currentEntry(routeId);
            if (before == null || !before.selected || !before.volumeEditable) return false;
            boolean accepted = backend.setVolume(routeId, volume);
            Entry actual = currentEntry(routeId);
            if (accepted && actual != null) volumeMemory.remember(routeId, actual.volume, actual.volumeMax);
            return accepted;
        }
        catch (RuntimeException ignored) { return false; }
    }

    private Entry currentEntry(String routeId) {
        if (observedConnections == null) return null;
        observedConnections.refresh();
        for (Entry entry : backend.entries(observedConnections.connectedDevices().size() > 1))
            if (entry.id.equals(routeId)) return entry;
        return null;
    }

    String diagnostic(BluetoothAudioState connections) {
        MediaOutputSnapshot media = MediaOutputSnapshot.read(context);
        String routing = "Controlador de rutas no disponible";
        if (Build.VERSION.SDK_INT >= 30 && backend != null) {
            try { routing = backend.diagnostic(); }
            catch (RuntimeException ignored) { routing = "No se pudo consultar el controlador"; }
        }
        return "Juntos " + BuildConfig.VERSION_NAME + "\n" + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nEquipos Bluetooth de audio conectados: " + connections.connectedDevices().size()
                + "\n" + routing + "\nSalidas multimedia previstas: " + media.outputCount
                + "\nIdentidades Bluetooth expuestas: " + media.bluetoothAddresses.size() + "\n" + media.status
                + "\n\nLas salidas previstas no confirman que se esté reproduciendo audio."
                + "\nUna casilla con guion significa que no se puede confirmar su selección."
                + "\nNo se incluyen nombres de equipos ni direcciones Bluetooth en este informe.";
    }

    @TargetApi(30)
    private static final class Backend {
        final MediaRouter2 router;
        final AudioManager audio;
        final MediaRouter2.ControllerCallback controllerCallback;
        final MediaRouter2.RouteCallback routeCallback;
        final AudioDeviceCallback deviceCallback;
        final AudioManager.AudioPlaybackCallback playbackCallback;
        Backend(Context context, Runnable changed) {
            router = MediaRouter2.getInstance(context);
            audio = context.getSystemService(AudioManager.class);
            controllerCallback = new MediaRouter2.ControllerCallback() {
                @Override public void onControllerUpdated(MediaRouter2.RoutingController controller) { changed.run(); }
            };
            routeCallback = new MediaRouter2.RouteCallback() {
                @Override public void onRoutesUpdated(List<MediaRoute2Info> routes) { changed.run(); }
            };
            router.registerControllerCallback(context.getMainExecutor(), controllerCallback);
            try {
                router.registerRouteCallback(context.getMainExecutor(), routeCallback,
                        new RouteDiscoveryPreference.Builder(Collections.singletonList(MediaRoute2Info.FEATURE_LIVE_AUDIO), false).build());
            } catch (RuntimeException ex) {
                router.unregisterControllerCallback(controllerCallback); throw ex;
            }
            deviceCallback = new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { changed.run(); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { changed.run(); }
            };
            playbackCallback = new AudioManager.AudioPlaybackCallback() {
                @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) { changed.run(); }
            };
            try {
                audio.registerAudioDeviceCallback(deviceCallback, new android.os.Handler(android.os.Looper.getMainLooper()));
                audio.registerAudioPlaybackCallback(playbackCallback, new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (RuntimeException ex) {
                audio.unregisterAudioDeviceCallback(deviceCallback);
                router.unregisterRouteCallback(routeCallback);
                router.unregisterControllerCallback(controllerCallback);
                throw ex;
            }
        }
        List<Entry> entries(boolean multipleBluetoothConnections) {
            MediaRouter2.RoutingController controller = router.getSystemController();
            List<MediaRoute2Info> selected = controller.getSelectedRoutes();
            Map<String, MediaRoute2Info> routes = new LinkedHashMap<>();
            for (MediaRoute2Info route : selected) routes.put(route.getId(), route);
            for (MediaRoute2Info route : controller.getSelectableRoutes()) routes.put(route.getId(), route);
            if (Build.VERSION.SDK_INT >= 35) {
                for (MediaRoute2Info route : controller.getTransferableRoutes()) routes.put(route.getId(), route);
            } else {
                for (MediaRoute2Info route : router.getRoutes()) if (route.isSystemRoute()) routes.put(route.getId(), route);
            }
            List<Entry> result = new ArrayList<>();
            for (MediaRoute2Info route : routes.values()) {
                boolean isSelected = contains(selected, route.getId());
                int icon = R.drawable.ic_bluetooth;
                if (Build.VERSION.SDK_INT >= 34) {
                    switch (route.getType()) {
                        case MediaRoute2Info.TYPE_BUILTIN_SPEAKER: icon = R.drawable.ic_phone; break;
                        case MediaRoute2Info.TYPE_REMOTE_SPEAKER: icon = R.drawable.ic_speaker; break;
                        case MediaRoute2Info.TYPE_REMOTE_TV: icon = R.drawable.ic_tv; break;
                        case MediaRoute2Info.TYPE_BLE_HEADSET:
                        case MediaRoute2Info.TYPE_WIRED_HEADSET:
                        case MediaRoute2Info.TYPE_WIRED_HEADPHONES:
                        case MediaRoute2Info.TYPE_USB_HEADSET:
                        case MediaRoute2Info.TYPE_HEARING_AID: icon = R.drawable.ic_headphones; break;
                    }
                }
                boolean canToggle = contains(isSelected ? controller.getDeselectableRoutes() : controller.getSelectableRoutes(), route.getId());
                // A single public system route can conceal duplicated OEM outputs. Do not
                // represent it as an exclusive physical selection or individual volume.
                boolean uncertain = multipleBluetoothConnections && route.isSystemRoute() && !canToggle;
                boolean localVolume = !uncertain && route.isSystemRoute() && selected.size() == 1 && isSelected;
                result.add(new Entry(route.getId(), route.getName().toString(),
                        uncertain ? "Selección individual sin confirmar" : isSelected ? "Ruta indicada por Android" : canToggle ? "Disponible · marca la casilla para elegir" : "Selección directa no disponible en este celular",
                        !uncertain && isSelected, !uncertain, icon,
                        localVolume ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) : selected.size() == 1 && isSelected ? controller.getVolume() : route.getVolume(),
                        localVolume ? audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : selected.size() == 1 && isSelected ? controller.getVolumeMax() : route.getVolumeMax(),
                        !uncertain && (localVolume ? !audio.isVolumeFixed() : selected.size() == 1 && isSelected && controller.getVolumeHandling() == MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE),
                        canToggle, isSelected || contains(controller.getSelectableRoutes(), route.getId())
                                || route.getConnectionState() == MediaRoute2Info.CONNECTION_STATE_CONNECTED));
            }
            return result;
        }
        String diagnostic() {
            MediaRouter2.RoutingController controller = router.getSystemController();
            return "Rutas indicadas: " + controller.getSelectedRoutes().size()
                    + "\nSe pueden añadir: " + controller.getSelectableRoutes().size()
                    + "\nSe pueden quitar: " + controller.getDeselectableRoutes().size();
        }
        boolean requestToggle(String routeId) {
            MediaRouter2.RoutingController controller = router.getSystemController();
            boolean selected = contains(controller.getSelectedRoutes(), routeId);
            List<MediaRoute2Info> allowed = selected ? controller.getDeselectableRoutes() : controller.getSelectableRoutes();
            for (MediaRoute2Info route : allowed) {
                if (!route.getId().equals(routeId)) continue;
                if (selected) controller.deselectRoute(route); else controller.selectRoute(route);
                return true;
            }
            // Transfer can replace other outputs. Do not use it to pretend to add a second output.
            return false;
        }
        boolean setVolume(String routeId, int volume) {
            MediaRouter2.RoutingController controller = router.getSystemController();
            List<MediaRoute2Info> selected = controller.getSelectedRoutes();
            // Never present a group volume as an independent volume for one of its members.
            if (selected.size() != 1 || !selected.get(0).getId().equals(routeId)) return false;
            int mode = audio.getMode();
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) return false;
            if (selected.get(0).isSystemRoute()) {
                if (audio.isVolumeFixed()) return false;
                int target = Math.max(0, Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), volume));
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                return audio.getStreamVolume(AudioManager.STREAM_MUSIC) == target;
            }
            if (controller.getVolumeHandling() != MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE) return false;
            controller.setVolume(Math.max(0, Math.min(controller.getVolumeMax(), volume)));
            return true;
        }
        static boolean contains(List<MediaRoute2Info> routes, String id) {
            for (MediaRoute2Info route : routes) if (route.getId().equals(id)) return true;
            return false;
        }
        void stop() {
            audio.unregisterAudioDeviceCallback(deviceCallback);
            audio.unregisterAudioPlaybackCallback(playbackCallback);
            router.unregisterControllerCallback(controllerCallback);
            router.unregisterRouteCallback(routeCallback);
        }
    }
}
