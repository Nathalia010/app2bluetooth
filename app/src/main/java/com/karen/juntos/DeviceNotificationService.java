package com.karen.juntos;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.media.AudioManager;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** User-visible monitoring only. No media capture, background scans, or forced audio routing. */
public class DeviceNotificationService extends Service {
    static final String CHANNEL = "juntos_connected_devices";
    static final String TOGGLE_OUTPUT = "com.karen.juntos.TOGGLE_OUTPUT";
    static final String ROUTE_ID = "route_id";
    private static final String VOLUME = "com.karen.juntos.ADJUST_VOLUME";
    private static final String STOP = "com.karen.juntos.STOP_MONITOR";
    private static final int NOTIFICATION = 100;
    static volatile boolean running;
    private BluetoothAdapter adapter;
    private BluetoothAudioState connections;
    private AudioOutputs outputs;
    private boolean registered;
    private String feedback;
    private SharedPreferences volumePreferences;
    private boolean observingVolume;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::update;
    private final SharedPreferences.OnSharedPreferenceChangeListener volumeChanges = (preferences, key) -> {
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 150);
    };
    private final ContentObserver systemVolumeChanges = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            handler.removeCallbacks(refresh); handler.postDelayed(refresh, 150);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DevicesWidget.BATTERY_ACTION.equals(intent.getAction())) { DevicesWidget.battery(context, intent); return; }
            update();
            handler.removeCallbacks(refresh); handler.postDelayed(refresh, 500);
        }
    };

    static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    private boolean bluetoothAllowed() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onCreate() {
        super.onCreate();
        if (!bluetoothAllowed() || !notificationsAllowed(this)) { stopSelf(); return; }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { stopSelf(); return; }
        connections = new BluetoothAudioState(this, adapter, this::update);
        outputs = new AudioOutputs(this, this::update);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Dispositivos conectados", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Muestra tus conexiones de audio y el acceso para elegir dónde escuchar.");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(NOTIFICATION, notification());
        } catch (RuntimeException ex) { stopSelf(); return; }
        running = true;
        volumePreferences = getSharedPreferences("output_volumes", MODE_PRIVATE);
        volumePreferences.registerOnSharedPreferenceChangeListener(volumeChanges);
        try {
            getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, systemVolumeChanges);
            observingVolume = true;
        } catch (SecurityException ignored) { /* Routing callbacks and in-app changes still refresh the notice. */ }
        IntentFilter filter = new IntentFilter();
        filter.addAction(DevicesWidget.BATTERY_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        registered = true;
        connections.start(); outputs.start(); update();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            getSharedPreferences("devices", MODE_PRIVATE).edit().putBoolean("notification_enabled", false).apply();
            stopSelf();
        } else if (running && intent != null && TOGGLE_OUTPUT.equals(intent.getAction())) {
            toggleOutput(intent.getStringExtra(ROUTE_ID));
        } else if (running && intent != null && VOLUME.equals(intent.getAction())) {
            adjustVolume(intent.getStringExtra(ROUTE_ID), intent.getIntExtra("direction", 0));
        } else if (running) update();
        return START_NOT_STICKY;
    }

    private void toggleOutput(String routeId) {
        feedback = null;
        int mode = getSystemService(AudioManager.class).getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            feedback = "El audio de la llamada se elige desde la llamada.";
            update(); return;
        }
        Boolean before = selectedState(routeId);
        if (!outputs.requestToggle(routeId)) {
            feedback = "Android no permite cambiar esta salida directamente desde la app.";
        } else {
            feedback = "Solicitando cambio de salida…";
            handler.postDelayed(() -> {
                Boolean after = selectedState(routeId);
                feedback = after == null || after.equals(before)
                        ? "El celular no confirmó el cambio. Se mantiene la selección real."
                        : "Salida actualizada por Android.";
                update();
            }, 1600);
        }
        update();
    }

    private Boolean selectedState(String routeId) {
        for (AudioOutputs.Entry entry : outputs.entries(connections)) {
            if (entry.id.equals(routeId) && entry.known) return entry.selected;
        }
        return null;
    }

    private PendingIntent toggleIntent(int request, AudioOutputs.Entry entry) {
        Intent intent = new Intent(this, DeviceNotificationService.class).setAction(TOGGLE_OUTPUT).putExtra(ROUTE_ID, entry.id)
                .setData(android.net.Uri.parse("juntos://toggle/" + android.net.Uri.encode(entry.id)));
        return PendingIntent.getService(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean volumeAvailable(AudioOutputs.Entry entry) {
        int mode = getSystemService(AudioManager.class).getMode();
        return entry.connected && entry.selected && entry.volumeEditable && entry.volumeMax > 0
                && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }

    private PendingIntent volumeIntent(AudioOutputs.Entry entry, int direction) {
        Intent intent = new Intent(this, DeviceNotificationService.class).setAction(VOLUME)
                .setData(android.net.Uri.parse("juntos://volume/" + android.net.Uri.encode(entry.id) + "/" + direction))
                .putExtra(ROUTE_ID, entry.id).putExtra("direction", direction);
        return PendingIntent.getService(this, 40, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void adjustVolume(String routeId, int direction) {
        if (direction != -1 && direction != 1) return;
        feedback = "Control de volumen no disponible para esta salida.";
        // Re-read the route: actions from an older notification cannot grant control.
        for (AudioOutputs.Entry entry : outputs.entries(connections)) {
            if (!entry.id.equals(routeId) || !volumeAvailable(entry)) continue;
            int step = Math.max(1, Math.round(entry.volumeMax * 0.05f));
            int target = Math.max(0, Math.min(entry.volumeMax, entry.volume + direction * step));
            feedback = outputs.setVolume(entry.id, target) ? null : "El celular limitó o rechazó el cambio de volumen.";
            break;
        }
        update();
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 400);
    }

    private void update() {
        if (!running || connections == null) return;
        if (!bluetoothAllowed() || !notificationsAllowed(this)) { stopSelf(); return; }
        try {
            if (!adapter.isEnabled()) connections.stop(); else connections.start();
            connections.refresh();
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
        } catch (SecurityException ex) { stopSelf(); }
    }

    private PendingIntent activityIntent(int request, String action, String route) {
        Intent intent = new Intent(this, MainActivity.class).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (route != null) intent.putExtra(ROUTE_ID, route);
        return PendingIntent.getActivity(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification() {
        List<AudioOutputs.Entry> rows = outputs.panelEntries(connections);
        DevicesWidget.publish(this, rows);
        int connectedCount = (int) rows.stream().filter(entry -> entry.connected).count();
        RemoteViews expanded = new RemoteViews(getPackageName(), R.layout.notification_devices);
        int[] rowIds = {R.id.notification_row_1, R.id.notification_row_2};
        int[] nameIds = {R.id.notification_name_1, R.id.notification_name_2};
        int[] checkIds = {R.id.notification_check_1, R.id.notification_check_2};
        int[] stateIds = {R.id.notification_state_1, R.id.notification_state_2};
        int[] iconIds = {R.id.notification_icon_1, R.id.notification_icon_2};
        int[] volumeIds = {R.id.notification_volume_1, R.id.notification_volume_2};
        int[] minusIds = {R.id.notification_minus_1, R.id.notification_minus_2};
        int[] plusIds = {R.id.notification_plus_1, R.id.notification_plus_2};
        for (int i = 0; i < 2; i++) {
            expanded.setViewVisibility(rowIds[i], i < rows.size() ? View.VISIBLE : View.GONE);
            if (i >= rows.size()) continue;
            AudioOutputs.Entry entry = rows.get(i);
            boolean editable = volumeAvailable(entry);
            String state = !entry.connected ? "Sin conectar" : entry.known ? (entry.selected ? "Seleccionado" : "Desactivado") : "Sin confirmar";
            String level = AudioOutputs.volumeText(entry);
            expanded.setTextViewText(nameIds[i], (i + 1) + ". " + entry.name + " · " + level);
            expanded.setContentDescription(nameIds[i], entry.name + ". " + entry.detail + ". Volumen: " + level);
            expanded.setTextViewText(stateIds[i], state);
            expanded.setImageViewResource(iconIds[i], entry.icon);
            expanded.setProgressBar(volumeIds[i], Math.max(1, entry.volumeMax), editable ? AudioOutputs.displayedVolume(entry) : 0, false);
            expanded.setFloat(volumeIds[i], "setAlpha", editable ? 1f : 0.35f);
            expanded.setContentDescription(minusIds[i], "Bajar volumen de " + entry.name);
            expanded.setContentDescription(plusIds[i], "Subir volumen de " + entry.name);
            expanded.setViewVisibility(minusIds[i], editable ? View.VISIBLE : View.INVISIBLE);
            expanded.setViewVisibility(plusIds[i], editable ? View.VISIBLE : View.INVISIBLE);
            expanded.setBoolean(minusIds[i], "setEnabled", editable);
            expanded.setBoolean(plusIds[i], "setEnabled", editable);
            expanded.setOnClickPendingIntent(minusIds[i], volumeIntent(entry, -1));
            expanded.setOnClickPendingIntent(plusIds[i], volumeIntent(entry, 1));
            expanded.setImageViewResource(checkIds[i], entry.known ? (entry.selected ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_unchecked) : R.drawable.ic_checkbox_unknown);
            expanded.setContentDescription(checkIds[i], entry.name + ". " + state + ". "
                    + (entry.canToggle ? (entry.selected ? "Desactivar sonido." : "Activar sonido y recuperar volumen.") : "Control individual no disponible desde esta app."));
            expanded.setBoolean(checkIds[i], "setEnabled", entry.connected && entry.canToggle);
            expanded.setFloat(checkIds[i], "setAlpha", entry.canToggle ? 1f : 0.5f);
            PendingIntent toggle = toggleIntent(i + 1, entry);
            expanded.setOnClickPendingIntent(nameIds[i], toggle);
            expanded.setOnClickPendingIntent(iconIds[i], toggle);
            expanded.setOnClickPendingIntent(stateIds[i], toggle);
            expanded.setOnClickPendingIntent(checkIds[i], toggle);
        }
        String hint = rows.stream().noneMatch(entry -> entry.connected)
                ? "Sin equipos Bluetooth conectados. Abre Juntos para vincular uno."
                : "Casilla: activar/desactivar sonido. Gris: sin control individual disponible.";
        if (feedback != null) hint = feedback;
        expanded.setTextViewText(R.id.notification_hint, hint);
        PendingIntent stop = PendingIntent.getService(this, 30, new Intent(this, DeviceNotificationService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        RemoteViews collapsed = new RemoteViews(getPackageName(), R.layout.notification_devices_compact);
        PendingIntent connect = PendingIntent.getActivity(this, 10,
                new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        collapsed.setTextViewText(R.id.notification_summary, connectedCount == 0
                ? "Despliega para ver las salidas de audio"
                : connectedCount + " equipos Bluetooth · despliega para controlar");
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle("Juntos activo")
                .setContentText(connectedCount == 0 ? "Juntos activo · toca para ver las salidas" : connectedCount + " equipos Bluetooth · toca para ver las salidas")
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(collapsed).setCustomBigContentView(expanded)
                .setContentIntent(activityIntent(0, null, null)).setOngoing(true).setOnlyAlertOnce(true)
                .setShowWhen(false).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Conectar dispositivo", connect).build())
                .addAction(new Notification.Action.Builder(null, "Ocultar aviso", stop).build());
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }

    @Override public void onDestroy() {
        running = false; handler.removeCallbacksAndMessages(null);
        if (volumePreferences != null) volumePreferences.unregisterOnSharedPreferenceChangeListener(volumeChanges);
        if (observingVolume) getContentResolver().unregisterContentObserver(systemVolumeChanges);
        if (registered) unregisterReceiver(receiver);
        if (connections != null) connections.stop();
        if (outputs != null) outputs.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
