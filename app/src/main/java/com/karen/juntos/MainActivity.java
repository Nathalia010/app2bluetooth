package com.karen.juntos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothLeAudio;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PERMISSIONS = 10, ENABLE_BT = 11, NOTIFICATIONS = 12;
    private int INK, GREEN, background, surface, muted, soft;
    private boolean dark, requestingHomePermissions;
    private boolean adjustingVolume;
    private boolean volumeExpanded;
    private final Map<String, SeekBar> volumeBars = new LinkedHashMap<>();
    private String renderedPanelState = "";
    private final Runnable volumeRefresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            refreshDeviceVolumes(); handler.postDelayed(this, 700);
        }
    };
    private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
    private final Map<String, BluetoothClass> deviceClasses = new LinkedHashMap<>();
    private final String[] selected = new String[2];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothAudioState connections;
    private AudioOutputs outputs;
    private SharedPreferences preferences;
    private LinearLayout slots;
    private TextView status, discoveryStatus, soundStatus;
    private Button soundButton;
    private AlertDialog discoveryDialog;
    private DeviceAdapter discoveryAdapter;
    private boolean registered, scanning, active;
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private final Runnable scanTimeout = () -> {
        stopScan(); setStatus("Búsqueda terminada. Puedes volver a buscar.");
    };
    private final Runnable connectionRefresh = this::refreshConnectionUi;
    private final Runnable soundProgress = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            try {
                int seconds = Math.max(0, (player.getDuration() - player.getCurrentPosition() + 999) / 1000);
                soundStatus.setText("Escuchando el sonido · faltan " + seconds + " s");
                handler.postDelayed(this, 500);
            } catch (IllegalStateException ex) { stopSound(); }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DevicesWidget.BATTERY_ACTION.equals(intent.getAction())) { DevicesWidget.battery(context, intent); return; }
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (ready()) { connections.start(); loadPaired(); }
                else {
                    stopScan(); connections.stop(); devices.clear(); deviceClasses.clear();
                    renderSlots(); updateDiscovery();
                    setStatus("Activa Bluetooth para buscar dispositivos.");
                }
                return;
            }
            if (!hasPermissions()) return;
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (!scanning) return;
                scanning = false; handler.removeCallbacks(scanTimeout); updateSearchButton();
                setStatus("Búsqueda terminada · " + devices.size() + " equipos encontrados o guardados");
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device != null) {
                devices.put(device.getAddress(), device);
                BluetoothClass info = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
                if (info != null) deviceClasses.put(device.getAddress(), info);
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    if (bondState(device) == BluetoothDevice.BOND_BONDED)
                        setStatus("Emparejado. Abre los ajustes de Bluetooth para conectarlo al audio.");
                    else if (bondState(device) == BluetoothDevice.BOND_NONE)
                        setStatus("No se completó el emparejamiento. Puedes intentarlo desde los ajustes.");
                }
            }
            refreshConnectionUi();
            handler.removeCallbacks(connectionRefresh);
            handler.postDelayed(connectionRefresh, 400);
        }
    };

    @Override public void onCreate(Bundle state) {
        preferences = getSharedPreferences("devices", MODE_PRIVATE);
        preferences.edit().remove("slot2").remove("name2").remove("type2").apply();
        dark = preferences.getBoolean("dark_theme", (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        INK = Color.parseColor(dark ? "#EFF7F1" : "#182E23");
        GREEN = Color.parseColor(dark ? "#70DBA6" : "#12784C");
        background = Color.parseColor(dark ? "#111B18" : "#EDF6EF");
        surface = Color.parseColor(dark ? "#1E2D26" : "#FFFFFF");
        muted = Color.parseColor(dark ? "#B9CCC1" : "#53695C");
        soft = Color.parseColor(dark ? "#293F32" : "#D9EDDE");
        for (int i = 0; i < 2; i++) selected[i] = preferences.getString("slot" + i, null);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        audioManager = getSystemService(AudioManager.class);
        connections = new BluetoothAudioState(this, adapter, this::refreshConnectionUi);
        outputs = new AudioOutputs(this, this::renderSlots);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(dp(20) + left, dp(16) + top, dp(20) + right, dp(24) + bottom);
            return insets;
        });
        ScrollView full = new ScrollView(this); full.setFillViewport(true);
        full.addView(root); setContentView(full);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(label("JUNTOS BLUETOOTH", 12, GREEN, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button themeButton = button(dark ? "☀️" : "🌙", v -> {
            preferences.edit().putBoolean("dark_theme", !dark).apply();
            recreate();
        });
        themeButton.setTextSize(24);
        themeButton.setContentDescription(dark ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
        themeButton.setMinWidth(0);
        themeButton.setMinimumWidth(0);
        themeButton.setPadding(0, 0, 0, 0);
        header.addView(themeButton, new LinearLayout.LayoutParams(dp(56), dp(56)));
        root.addView(header);
        slots = column(); root.addView(slots);
        status = label("Tus equipos conectados aparecen aquí.", 12, muted, false);
        status.setMaxLines(2); root.addView(status);
        LinearLayout footer = column();
        LinearLayout advertising = column();
        advertising.setPadding(dp(12), dp(12), dp(12), dp(12));
        advertising.setBackground(card(surface));
        TextView adLabel = label("Publicidad", 11, muted, false);
        adLabel.setGravity(android.view.Gravity.CENTER);
        advertising.addView(adLabel);
        TextView adSpace = label("Espacio publicitario", 12, muted, false);
        adSpace.setGravity(android.view.Gravity.CENTER);
        advertising.addView(adSpace, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout.LayoutParams adMargins = new LinearLayout.LayoutParams(-1, -2);
        adMargins.topMargin = dp(12); adMargins.bottomMargin = dp(20);
        footer.addView(advertising, adMargins);
        soundStatus = label("Una voz dice «prueba» con música suave.", 12, muted, false);
        footer.addView(soundStatus);
        soundButton = button("Escuchar sonido (34 s)", v -> { if (player == null) playSound(); else stopSound(); });
        soundButton.setBackgroundTintList(ColorStateList.valueOf(GREEN));
        soundButton.setTextColor(dark ? Color.rgb(17, 38, 29) : Color.WHITE);
        footer.addView(soundButton);

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Configuración", v -> showConfiguration()), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("＋ Vincular dispositivo", v -> openDiscovery()), new LinearLayout.LayoutParams(0, -2, 1));
        footer.addView(actions);
        footer.addView(button("¿Necesitas ayuda?", v -> new AlertDialog.Builder(this).setTitle("¿En qué te ayudamos?")
                .setItems(new String[]{"No puedo conectar otro equipo", "Audio y micrófono de llamadas"},
                        (d, which) -> { if (which == 0) connectionHelp(); else callHelp(); }).setNegativeButton("Cerrar", null).show()));
        footer.addView(button("Widget para la pantalla de inicio", v -> {
            android.appwidget.AppWidgetManager manager = getSystemService(android.appwidget.AppWidgetManager.class);
            if (manager.isRequestPinAppWidgetSupported()) {
                manager.requestPinAppWidget(new android.content.ComponentName(this, DevicesWidget.class), null, null);
            } else new AlertDialog.Builder(this).setTitle("Añadir widget")
                    .setMessage("Mantén pulsado un espacio vacío de la pantalla de inicio, elige Widgets y busca Juntos Bluetooth.")
                    .setPositiveButton("Entendido", null).show();
        }));
        Button credits = button("Hecho con cariño · Créditos", v -> new AlertDialog.Builder(this)
                .setTitle("Juntos Bluetooth")
                .setMessage("Hecho por\nManuel Martinez y Karen Martinez\n\nDiseño y desarrollo de Juntos Bluetooth.\nVersión " + BuildConfig.VERSION_NAME
                        + "\n\nAportes voluntarios\nSi quieres apoyar nuestro trabajo, puedes enviar un aporte a nuestra llave de Nu:\n\n@KMB212\n\nTu aporte es opcional y no desbloquea funciones de la app. ¡Gracias por tu apoyo!"
                        + "\n\n© 2026 Manuel Martinez y Karen Martinez. Todos los derechos reservados.\n\nLa reproducción y el control de audio dependen de las funciones del teléfono y de los equipos conectados.")
                .setNeutralButton("Copiar llave de Nu", (dialog, which) -> {
                    android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Llave de Nu", "@KMB212"));
                        toast("Llave copiada: @KMB212");
                    } else toast("No se pudo copiar. La llave es @KMB212");
                })
                .setPositiveButton("Cerrar", null).show());
        credits.setBackgroundColor(Color.TRANSPARENT);
        footer.addView(credits);
        root.addView(footer);
        renderSlots();
    }

    @Override protected void onStart() {
        super.onStart(); active = true;
        handler.removeCallbacks(volumeRefresh); handler.post(volumeRefresh);
        outputs.start();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DevicesWidget.BATTERY_ACTION);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
        registered = true;
    }

    @Override protected void onResume() {
        super.onResume();
        if (ready()) { connections.start(); loadPaired(); refreshConnectionUi(); }
        else {
            devices.clear(); deviceClasses.clear(); renderSlots(); updateDiscovery();
            setStatus(adapter == null ? "Este equipo no tiene Bluetooth." : "Pulsa Conectar nuevo dispositivo para activar Bluetooth o conceder permisos.");
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ready() {
        try { return adapter != null && hasPermissions() && adapter.isEnabled(); }
        catch (SecurityException ex) { return false; }
    }

    private void setStatus(String message) {
        status.setText(message);
        if (discoveryStatus != null) discoveryStatus.setText(message);
    }

    private void openDiscovery() {
        openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    private void updateSearchButton() {
        if (discoveryDialog != null && discoveryDialog.isShowing())
            discoveryDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(scanning ? "Detener búsqueda" : "Volver a buscar");
    }

    private void updateDiscovery() {
        if (discoveryAdapter != null) discoveryAdapter.update();
        updateSearchButton();
    }

    private final class DeviceAdapter extends BaseAdapter {
        private final List<BluetoothDevice> items = new ArrayList<>();
        void update() { items.clear(); items.addAll(devices.values()); notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public BluetoothDevice getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            BluetoothDevice device = getItem(position);
            LinearLayout row = column(); row.setPadding(dp(12), dp(8), dp(12), dp(10));
            row.setBackground(card(surface));
            row.addView(label(name(device), 17, INK, true));
            row.addView(label(deviceType(device), 14, GREEN, true));
            row.addView(label(connectionLabel(device), 13, INK, false));
            int index = slotOf(device.getAddress());
            row.addView(label(index < 0 ? "Toca para elegir · " + device.getAddress()
                    : "Guardado como dispositivo " + (index + 1) + " · toca para ver opciones", 12, INK, false));
            return row;
        }
    }

    private void beginScan() {
        if (adapter == null) { setStatus("Este equipo no tiene Bluetooth."); return; }
        if (!hasPermissions()) {
            requestPermissions(Build.VERSION.SDK_INT >= 31
                    ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS);
            return;
        }
        if (!ready()) {
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BT); }
            catch (SecurityException ex) { setStatus("Revisa el permiso de dispositivos cercanos."); }
            return;
        }
        if (Build.VERSION.SDK_INT <= 30) {
            LocationManager location = getSystemService(LocationManager.class);
            boolean enabled = location != null && (location.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            if (!enabled) {
                new AlertDialog.Builder(this).setTitle("Activa la ubicación")
                        .setMessage("Android 8 a 11 exige la ubicación activa para buscar por Bluetooth. La app no guarda tu ubicación.")
                        .setPositiveButton("Abrir ajustes", (d, w) -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        .setNegativeButton("Cancelar", null).show();
                return;
            }
        }
        stopScan(); devices.clear(); deviceClasses.clear(); connections.start(); loadPaired();
        try { scanning = adapter.startDiscovery(); }
        catch (SecurityException ex) { setStatus("El permiso cambió. Revisa los permisos de la app."); return; }
        updateSearchButton();
        setStatus(scanning ? "Buscando cerca de ti…" : "No se pudo iniciar. Espera unos segundos e intenta de nuevo.");
        if (scanning) handler.postDelayed(scanTimeout, 20000);
    }

    private void stopScan() {
        boolean wasScanning = scanning;
        scanning = false; handler.removeCallbacks(scanTimeout);
        try {
            if (adapter != null && hasPermissions() && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException ignored) { /* Permission can be revoked while the screen is open. */ }
        updateSearchButton();
        if (wasScanning) setStatus("Búsqueda detenida · los equipos de la lista siguen disponibles para elegir.");
    }

    private void loadPaired() {
        if (!ready()) return;
        try {
            for (BluetoothDevice device : adapter.getBondedDevices()) devices.put(device.getAddress(), device);
        } catch (SecurityException ex) { setStatus("Revisa el permiso de dispositivos cercanos."); return; }
        renderSlots(); updateDiscovery();
    }

    private void refreshConnectionUi() {
        if (!active || !ready()) return;
        connections.refresh();
        for (BluetoothDevice device : connections.connectedDevices()) devices.put(device.getAddress(), device);
        renderSlots(); updateDiscovery();
    }

    private String name(BluetoothDevice device) {
        String value = null;
        try { if (hasPermissions()) value = device.getName(); }
        catch (SecurityException ignored) { /* Neutral label when permission is unavailable. */ }
        return value == null || value.trim().isEmpty() ? "Dispositivo sin nombre" : value;
    }

    private int bondState(BluetoothDevice device) {
        if (!hasPermissions()) return -1;
        try { return device.getBondState(); } catch (SecurityException ex) { return -1; }
    }

    private String connectionLabel(BluetoothDevice device) {
        if (ready()) {
            String connection = connections.summary(device.getAddress());
            if (connection != null) return connection;
        }
        switch (bondState(device)) {
            case BluetoothDevice.BOND_BONDED: return "Emparejado · guardado en el teléfono";
            case BluetoothDevice.BOND_BONDING: return "Emparejando… confirma en el teléfono";
            case BluetoothDevice.BOND_NONE: return "Sin emparejar";
            default: return "Estado sin verificar · revisa los permisos";
        }
    }

    private String deviceType(BluetoothDevice device) {
        BluetoothClass info = deviceClasses.get(device.getAddress());
        try { if (info == null && hasPermissions()) info = device.getBluetoothClass(); }
        catch (SecurityException ignored) { /* Fall back to discovery information. */ }
        if (info == null) return "Otro dispositivo · tipo sin identificar";
        switch (info.getDeviceClass()) {
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES: return "Audífonos / manos libres";
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER: return "Parlante";
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR: return "TV o pantalla";
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO: return "Audio de carro";
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO: return "Equipo de sonido";
        }
        switch (info.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.PHONE: return "Celular o teléfono";
            case BluetoothClass.Device.Major.COMPUTER: return "Computador";
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "Equipo de audio o video";
            case BluetoothClass.Device.Major.PERIPHERAL: return "Accesorio · teclado, mouse u otro";
            case BluetoothClass.Device.Major.IMAGING: return "Impresora o equipo de imagen";
            case BluetoothClass.Device.Major.WEARABLE: return "Reloj u otro accesorio personal";
            default: return "Otro dispositivo · tipo sin identificar";
        }
    }

    private void renderSlots() {
        if (slots == null || isDestroyed() || adjustingVolume) return;
        slots.removeAllViews(); volumeBars.clear();
        List<AudioOutputs.Entry> entries = outputs.panelEntries(connections);
        DevicesWidget.publish(this, entries);
        renderedPanelState = panelState(entries);
        LinearLayout headings = new LinearLayout(this);
        headings.addView(label("Dispositivos", 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        headings.addView(label("Conexión", 13, muted, true));
        slots.addView(headings);
        for (int i = 0; i < entries.size(); i++) {
            AudioOutputs.Entry entry = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(8), dp(8), 0, dp(8));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this); icon.setImageResource(entry.icon);
            icon.setImageTintList(ColorStateList.valueOf(entry.connected ? GREEN : muted));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconSpace = new LinearLayout.LayoutParams(dp(32), dp(32));
            iconSpace.rightMargin = dp(10); row.addView(icon, iconSpace);
            LinearLayout text = column(); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            text.addView(label((i + 1) + ". " + entry.name, 16, INK, true));
            text.addView(label(entry.connected ? entry.detail : "Abrir configuración de Bluetooth", 12, muted, false));
            TextView check = label(entry.connected ? "✓" : "+", 26, GREEN, true);
            check.setGravity(android.view.Gravity.CENTER);
            check.setContentDescription(entry.connected ? entry.name + ": conectado por Bluetooth" : "Conectar nuevo dispositivo");
            row.setOnClickListener(v -> {
                if (entry.connected) setStatus("Conectado por Bluetooth. Esto no confirma reproducción simultánea.");
                else openDiscovery();
            });
            if (!entry.connected) {
                row.setContentDescription("Conectar nuevo dispositivo. Abrir configuración de Bluetooth");
                check.setOnClickListener(v -> openDiscovery());
            }
            row.addView(check, new LinearLayout.LayoutParams(dp(56), dp(56)));
            slots.addView(row);
            View divider = new View(this); divider.setBackgroundColor(soft);
            slots.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        slots.addView(label("✓ indica conexión Bluetooth, no que ambos estén sonando.", 12, muted, false));
        Button volumeHeading = button("Volumen por dispositivo " + (volumeExpanded ? "▴" : "▾"), v -> {
            volumeExpanded = !volumeExpanded;
            renderSlots();
        });
        volumeHeading.setContentDescription("Volumen por dispositivo. " + (volumeExpanded ? "Contraer" : "Desplegar"));
        volumeHeading.setBackgroundColor(Color.TRANSPARENT);
        volumeHeading.setTextColor(INK);
        volumeHeading.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        volumeHeading.setPadding(0, 0, 0, 0);
        volumeHeading.setTextSize(16);
        slots.addView(volumeHeading);
        if (!volumeExpanded) return;
        LinearLayout bank = new LinearLayout(this);
        bank.setBackground(card(surface)); bank.setPadding(dp(8), dp(12), dp(8), dp(12));
        slots.addView(bank);
        boolean restricted = false;
        for (int i = 0; i < entries.size(); i++) {
            AudioOutputs.Entry entry = entries.get(i);
            boolean available = volumeAvailable(entry);
            restricted |= entry.connected && !entry.canToggle;
            LinearLayout level = column(); level.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            bank.addView(level, new LinearLayout.LayoutParams(0, -2, 1));
            TextView volumeLabel = label("", 20, INK, true); volumeLabel.setGravity(android.view.Gravity.CENTER);
            level.addView(volumeLabel);
            SeekBar volume = new VerticalVolumeBar(this); volume.setTag(volumeLabel);
            volume.setMax(Math.max(1, entry.volumeMax)); volume.setProgress(AudioOutputs.displayedVolume(entry));
            volume.setEnabled(available); volume.setContentDescription("Volumen de " + entry.name);
            level.addView(volume, new LinearLayout.LayoutParams(dp(56), dp(180)));
            volumeBars.put(entry.id, volume);
            updateVolumeAppearance(volume, volumeLabel, available);
            volumeLabel.setText(AudioOutputs.volumeText(entry));
            TextView caption = label("Audio " + (i + 1), 14, INK, true);
            caption.setGravity(android.view.Gravity.CENTER); level.addView(caption);
            TextView name = label(entry.name, 11, muted, false); name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(android.view.Gravity.CENTER); level.addView(name);
            String state = !entry.connected ? "Sin conectar" : entry.known && !entry.selected
                    ? "Desactivado" : available ? "Seleccionado" : "Sin control individual";
            TextView detail = label(state, 11, muted, false); detail.setGravity(android.view.Gravity.CENTER);
            level.addView(detail);
            if (entry.connected) {
                Button soundToggle = button(entry.known && entry.selected ? "Silenciar" : "Activar", v -> toggleOutput(entry.id));
                soundToggle.setEnabled(entry.canToggle);
                soundToggle.setContentDescription("Cambiar sonido de " + entry.name);
                level.addView(soundToggle);
            }
            volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    updateVolumeAppearance(bar, volumeLabel, bar.isEnabled());
                    if (fromUser && !outputs.setVolume(entry.id, progress))
                        setStatus("El celular no permitió cambiar este volumen.");
                }
                @Override public void onStartTrackingTouch(SeekBar bar) { adjustingVolume = true; }
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    adjustingVolume = false;
                    handler.postDelayed(() -> { if (active) renderSlots(); }, 300);
                }
            });
        }
        slots.addView(label(restricted
                ? "Gris: sin conexión o sin control individual disponible desde esta app."
                : "Gris: sin conectar o desactivado. Al activar, se recupera el nivel anterior cuando Android permite controlarlo.",
                12, muted, false));
    }
    private void toggleOutput(String routeId) {
        int mode = audioManager.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            setStatus("El audio de la llamada se elige desde la llamada."); return;
        }
        Boolean before = outputSelected(routeId);
        if (outputs.requestToggle(routeId)) {
            setStatus("Solicitando el cambio. La marca se actualizará cuando el celular lo confirme.");
            handler.postDelayed(() -> {
                if (!active) return;
                renderSlots();
                Boolean after = outputSelected(routeId);
                if (before == null || after == null || before.equals(after)) setStatus("El celular no confirmó el cambio de salida. La selección se mantiene como la informa Android.");
            }, 1600);
        } else setStatus("Este celular no permite activar o quitar este equipo directamente desde la app.");
    }

    private void refreshDeviceVolumes() {
        if (adjustingVolume || outputs == null) return;
        List<AudioOutputs.Entry> entries = outputs.panelEntries(connections);
        DevicesWidget.publish(this, entries);
        if (!renderedPanelState.equals(panelState(entries))) { renderSlots(); return; }
        for (AudioOutputs.Entry entry : entries) {
            SeekBar bar = volumeBars.get(entry.id);
            if (bar != null) {
                bar.setEnabled(volumeAvailable(entry));
                bar.setMax(Math.max(1, entry.volumeMax));
                bar.setProgress(AudioOutputs.displayedVolume(entry));
                updateVolumeAppearance(bar, (TextView) bar.getTag(), bar.isEnabled());
                ((TextView) bar.getTag()).setText(AudioOutputs.volumeText(entry));
            }
        }
    }

    private String panelState(List<AudioOutputs.Entry> entries) {
        StringBuilder state = new StringBuilder();
        for (AudioOutputs.Entry entry : entries)
            state.append(entry.id).append('|').append(entry.connected).append(entry.known)
                    .append(entry.selected).append(entry.canToggle).append(volumeAvailable(entry)).append(';');
        return state.toString();
    }

    private boolean volumeAvailable(AudioOutputs.Entry entry) {
        int mode = audioManager.getMode();
        return entry.connected && entry.selected && entry.volumeEditable && entry.volumeMax > 0
                && mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION;
    }

    private void updateVolumeAppearance(SeekBar bar, TextView label, boolean available) {
        int percent = Math.round(100f * bar.getProgress() / Math.max(1, bar.getMax()));
        int color = !available ? muted : Color.parseColor(percent <= 50 ? (dark ? "#79B8FF" : "#2167B5")
                : percent <= 75 ? (dark ? "#F5D76E" : "#947000") : (dark ? "#FF9393" : "#C33434"));
        bar.setProgressTintList(ColorStateList.valueOf(color));
        bar.setThumbTintList(ColorStateList.valueOf(color));
        label.setText(available ? percent + "%" : "—");
        label.setTextColor(color);
    }

    private Boolean outputSelected(String routeId) {
        for (AudioOutputs.Entry entry : outputs.entries(connections)) if (entry.known && entry.id.equals(routeId)) return entry.selected;
        return null;
    }

    private void showConfiguration() {
        String notificationLabel = DeviceNotificationService.running ? "Ocultar aviso de la barra" : "Mostrar aviso en la barra";
        new AlertDialog.Builder(this).setTitle("Configuración")
                .setItems(new String[]{"Abrir ajustes de Bluetooth", notificationLabel,
                        "Equipos guardados", "Diagnóstico de audio", "Ajustar volumen Bluetooth", "Privacidad y soporte"}, (dialog, which) -> {
                    if (which == 0) openSettings(Settings.ACTION_BLUETOOTH_SETTINGS);
                    else if (which == 1) {
                        if (DeviceNotificationService.running) {
                            preferences.edit().putBoolean("notification_enabled", false).apply();
                            stopService(new Intent(this, DeviceNotificationService.class));
                            setStatus("Aviso oculto. El audio del celular no cambia.");
                        } else {
                            enableNotification(true);
                        }
                    } else if (which == 2) showSavedDevices();
                    else if (which == 4) showBluetoothVolumeGuide();
                    else if (which == 5) showPrivacy();
                    else {
                        String report = outputs.diagnostic(connections);
                        new AlertDialog.Builder(this).setTitle("Diagnóstico de audio").setMessage(report)
                                .setPositiveButton("Copiar informe", (d, w) -> {
                                    android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico Juntos", report));
                                    setStatus("Informe copiado.");
                                }).setNegativeButton("Cerrar", null).show();
                    }
                }).setNegativeButton("Cerrar", null).show();
    }

    private void showPrivacy() {
        String policy;
        try (java.io.InputStream input = getResources().openRawResource(R.raw.privacy_policy)) {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            policy = text.toString();
        } catch (java.io.IOException ex) { policy = "Contacto de privacidad y soporte: knathaliabiscunda@gmail.com"; }
        new AlertDialog.Builder(this).setTitle("Privacidad y soporte").setMessage(policy)
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Contactar", (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:knathaliabiscunda@gmail.com"))); }
                    catch (android.content.ActivityNotFoundException ex) { toast("Escribe a knathaliabiscunda@gmail.com"); }
                }).show();
    }

    private void showBluetoothVolumeGuide() {
        new AlertDialog.Builder(this).setTitle("Ajustar volumen Bluetooth")
                .setItems(new String[]{"1. Habilitar opciones de desarrollador", "2. Inhabilitar volumen absoluto",
                        "3. Reconectar y comprobar", "Audio dual del fabricante"}, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(this).setTitle("¿Abrir información del teléfono?")
                                .setMessage("Para habilitar las opciones de desarrollador, toca 7 veces Número de compilación. En Xiaomi puede aparecer como Versión del SO o Versión de MIUI, dentro de la información detallada del teléfono.\n\nEl teléfono puede pedir tu PIN. Después vuelve a Juntos y elige el paso 2. Tú decides si activas esta opción en Ajustes.")
                                .setPositiveButton("Abrir ajustes", (d, w) -> openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS))
                                .setNegativeButton("Cancelar", null).show();
                    } else if (which == 1) {
                        new AlertDialog.Builder(this).setTitle("Dónde encontrar volumen absoluto")
                                .setMessage("En Xiaomi puede estar en los ajustes de cada equipo, en lugar de Opciones de desarrollador.\n\n1. Conecta el equipo y abre Ajustes → Bluetooth.\n2. Toca la flecha o ajustes junto a su nombre y revisa Audio.\n3. Si aparece Volumen absoluto o Mantener volumen consistente (Keep volume consistent), desactívalo para separar los volúmenes. Repite con el otro equipo. El nombre y la disponibilidad dependen del teléfono y del accesorio.\n\nSi usas Opciones de desarrollador, busca más abajo Inhabilitar volumen absoluto: en este caso el interruptor debe quedar ACTIVADO. Los registros Bluetooth HCI no son este ajuste.\n\nDespués desconecta y reconecta los equipos. Si ninguna opción aparece, Juntos no puede habilitarla ni verificar su estado. Esto no habilita por sí solo las barras independientes ni la reproducción simultánea. ¿Qué ajustes quieres abrir?")
                                .setPositiveButton("Bluetooth", (d, w) -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS))
                                .setNeutralButton("Desarrollador", (d, w) -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                .setNegativeButton("Cancelar", null).show();
                    } else if (which == 2) {
                        new AlertDialog.Builder(this).setTitle("¿Abrir Bluetooth para comprobar?")
                                .setMessage("Desconecta y vuelve a conectar los equipos después del cambio. Prueba los botones de volumen de cada uno. No hace falta borrar el emparejamiento para empezar.\n\nQue los equipos estén conectados no confirma que todos reproduzcan audio. Al volver, Juntos actualizará los controles que el teléfono permita usar.")
                                .setPositiveButton("Abrir Bluetooth", (d, w) -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS))
                                .setNegativeButton("Cancelar", null).show();
                    } else {
                        new AlertDialog.Builder(this).setTitle("Audio dual del fabricante")
                                .setMessage("Esta función depende del modelo y del sistema del teléfono. En Samsung compatibles se configura desde Salida multimedia en el panel rápido. No podemos confirmar esa función en tu Xiaomi.\n\nPuedes buscar Audio dual o Compartir audio en Ajustes. No existe un permiso que Juntos pueda pedir para añadir esa capacidad. ¿Quieres abrir Ajustes para revisar si tu teléfono la ofrece?")
                                .setPositiveButton("Abrir ajustes", (d, w) -> openSettings(Settings.ACTION_SETTINGS))
                                .setNegativeButton("Cancelar", null).show();
                    }
                }).setNegativeButton("Cerrar", null).show();
    }

    private void showSavedDevices() {
        String[] names = new String[2];
        for (int i = 0; i < 2; i++) names[i] = (i + 1) + ". " + preferences.getString("name" + i, "Sin equipo guardado");
        new AlertDialog.Builder(this).setTitle("Equipos guardados")
                .setItems(names, (dialog, which) -> {
                    if (selected[which] == null) { openDiscovery(); return; }
                    new AlertDialog.Builder(this).setTitle(names[which]).setMessage("Guardado no significa conectado ni elegido para el sonido.")
                            .setPositiveButton("Quitar de guardados", (d, w) -> {
                                selected[which] = null;
                                preferences.edit().remove("slot" + which).remove("name" + which).remove("type" + which).apply();
                                updateDiscovery();
                            }).setNegativeButton("Cerrar", null).show();
                }).setNegativeButton("Cerrar", null).show();
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        if (!hasPermissions() && adapter != null && !preferences.getBoolean("asked_home_bluetooth", false)) {
            preferences.edit().putBoolean("asked_home_bluetooth", true).apply();
            requestingHomePermissions = true;
            requestPermissions(Build.VERSION.SDK_INT >= 31
                    ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS);
        } else if (ready() && preferences.getBoolean("notification_enabled", false)) enableNotification(false);
        handler.postDelayed(this::processNotificationAction, 250);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        handler.postDelayed(this::processNotificationAction, 250);
    }

    private void processNotificationAction() {
        if (!active || getIntent() == null) return;
        String action = getIntent().getAction();
        if (DeviceNotificationService.TOGGLE_OUTPUT.equals(action)) {
            String routeId = getIntent().getStringExtra(DeviceNotificationService.ROUTE_ID);
            getIntent().setAction(null); toggleOutput(routeId);
        }
    }

    private void enableNotification(boolean userRequested) {
        if (!preferences.getBoolean("persistent_notice_explained", false)) {
            if (userRequested) new AlertDialog.Builder(this).setTitle("Controles en la notificación")
                    .setMessage("Juntos mantendrá un aviso visible para consultar tus conexiones Bluetooth y controlar las salidas compatibles aunque salgas de la app. Puedes detenerlo con Ocultar aviso, desde la notificación o Configuración. ¿Quieres activarlo?")
                    .setPositiveButton("Activar", (d, w) -> {
                        preferences.edit().putBoolean("persistent_notice_explained", true).putBoolean("notification_enabled", true).apply();
                        enableNotification(true);
                    }).setNegativeButton("Cancelar", null).show();
            return;
        }
        if (userRequested) preferences.edit().putBoolean("notification_enabled", true).apply();
        if (!hasPermissions()) {
            if (userRequested) {
                requestingHomePermissions = true;
                requestPermissions(Build.VERSION.SDK_INT >= 31
                        ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                        : new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (userRequested && preferences.getBoolean("asked_notifications", false)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                return;
            }
            if (userRequested || !preferences.getBoolean("asked_notifications", false)) {
                preferences.edit().putBoolean("asked_notifications", true).apply();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS);
            }
            return;
        }
        if (!DeviceNotificationService.notificationsAllowed(this)) {
            if (userRequested) startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            return;
        }
        if (!DeviceNotificationService.running) {
            try { startForegroundService(new Intent(this, DeviceNotificationService.class)); }
            catch (RuntimeException ex) { setStatus("No se pudo mostrar el aviso. Puedes intentarlo desde Configuración."); }
        }
    }

    private int slotOf(String address) {
        for (int i = 0; i < 2; i++) if (address.equals(selected[i])) return i;
        return -1;
    }

    private void select(BluetoothDevice device) {
        if (!ready()) { toast("Activa Bluetooth y concede los permisos para continuar."); return; }
        int existing = slotOf(device.getAddress());
        if (existing >= 0) { pairingOptions(device); return; }
        for (int i = 0; i < 2; i++) if (selected[i] == null) { saveSelection(i, device); return; }
        String[] choices = new String[2];
        for (int i = 0; i < 2; i++) choices[i] = "Dispositivo " + (i + 1) + ": " + preferences.getString("name" + i, "Equipo guardado");
        new AlertDialog.Builder(this).setTitle("¿Qué equipo guardado quieres reemplazar?")
                .setItems(choices, (dialog, which) -> saveSelection(which, device)).setNegativeButton("Cancelar", null).show();
    }

    private void saveSelection(int index, BluetoothDevice device) {
        if (!ready()) { toast("Revisa Bluetooth y los permisos."); return; }
        selected[index] = device.getAddress();
        preferences.edit().putString("slot" + index, device.getAddress()).putString("name" + index, name(device))
                .putString("type" + index, deviceType(device)).apply();
        renderSlots(); updateDiscovery(); pairingOptions(device);
    }

    private void pairingOptions(BluetoothDevice device) {
        if (bondState(device) == BluetoothDevice.BOND_NONE) {
            new AlertDialog.Builder(this).setTitle("Emparejar " + name(device))
                    .setMessage("El equipo quedó guardado en la selección. Android te pedirá confirmar el emparejamiento.")
                    .setPositiveButton("Emparejar", (dialog, which) -> {
                        if (!ready()) { toast("Revisa Bluetooth y los permisos."); return; }
                        stopScan();
                        try {
                            if (!device.createBond()) toast("No se pudo iniciar. Intenta desde los ajustes de Bluetooth.");
                            else setStatus("Emparejando… confirma la solicitud de Android.");
                        } catch (SecurityException ex) { toast("Revisa el permiso de dispositivos cercanos."); }
                    }).setNegativeButton("Después", null).show();
        } else {
            new AlertDialog.Builder(this).setTitle(name(device))
                    .setMessage(connectionLabel(device) + ".\n\nPara conectar o desconectar el audio, abre los ajustes. Quitar o reemplazar un equipo en la app solo cambia la selección guardada.")
                    .setPositiveButton("Abrir Bluetooth", (d, w) -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS))
                    .setNegativeButton("Cerrar", null).show();
        }
    }

    private void connectionHelp() {
        help("Conectar otro equipo", "1. Emparejado significa guardado: no confirma que el equipo esté conectado para audio. La app solo muestra «Conectado» cuando Android informa una conexión de audio.\n\n"
                + "2. Puedes guardar y emparejar más equipos en el teléfono. La selección de esta app tiene 2 espacios; al elegir otro, puedes reemplazar uno.\n\n"
                + "3. En el Galaxy S23 Ultra, Dual Audio permite dos salidas de música a la vez. Abre Salida multimedia y desmarca el TV u otra salida que no quieras usar; marca los dos audífonos o parlantes deseados.\n\n"
                + "4. Si el reloj toma las llamadas, abre los ajustes de Bluetooth, entra en las opciones del reloj y desactiva Llamadas / Audio de llamadas si aparece y no quieres usarlas allí. No hace falta quitarlo de la app para cambiar esa función.\n\n"
                + "5. Si un equipo nuevo no aparece, ponlo en modo de emparejamiento y revisa si está conectado a otro celular o al TV. Algunos TV envían audio a audífonos, pero no reciben música del celular por Bluetooth. Revisa el manual del TV.\n\n"
                + "La conexión de datos o notificaciones de un reloj no cuenta por sí sola como salida de música. Esta app no puede ampliar los límites de conexión del teléfono.");
    }

    private void callHelp() {
        help("Elegir audio y micrófono en llamadas", "Para llamadas del teléfono en Samsung:\n\n"
                + "1. Durante la llamada, toca Bluetooth o la flecha junto a ese botón.\n\n"
                + "2. Elige los audífonos, el manos libres, el reloj o el teléfono entre las opciones disponibles. Si quieres el celular, desactiva Bluetooth para esa llamada desde el botón de audio.\n\n"
                + "3. Android usa el micrófono asociado a la ruta elegida. Esta app no puede elegir, por ejemplo, el sonido de un audífono y el micrófono de otro para una llamada del teléfono o de WhatsApp.\n\n"
                + "Para WhatsApp u otra app de llamadas, usa el selector de audio dentro de esa llamada.\n\n"
                + "Si un reloj recibe las llamadas sin que quieras, revisa su opción Llamadas / Audio de llamadas en los ajustes de Bluetooth. Dual Audio para música no significa que la llamada se escuche en dos equipos.");
    }

    private void help(String title, String message) {
        ScrollView scroll = new ScrollView(this);
        TextView text = label(message, 15, INK, false); text.setPadding(dp(22), dp(8), dp(22), dp(12));
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton("Abrir Bluetooth", (d, w) -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS))
                .setNegativeButton("Cerrar", null).show();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == NOTIFICATIONS) {
            boolean allowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            preferences.edit().putBoolean("notification_enabled", allowed).apply();
            if (allowed) enableNotification(false);
            else setStatus("Aviso desactivado. Puedes activarlo desde Configuración.");
            return;
        }
        if (request != PERMISSIONS) return;
        if (requestingHomePermissions) {
            requestingHomePermissions = false;
            if (hasPermissions()) {
                if (ready()) { connections.start(); loadPaired(); refreshConnectionUi(); }
                if (preferences.getBoolean("notification_enabled", false)) enableNotification(false);
            } else setStatus("Permite Bluetooth desde Conectar nuevo dispositivo para ver tus equipos.");
            return;
        }
        if (hasPermissions()) {
            if (discoveryDialog == null) openDiscovery(); else beginScan();
        } else new AlertDialog.Builder(this).setTitle("Permiso necesario")
                .setMessage("Permite dispositivos cercanos (o ubicación en Android 8 a 11) para buscar equipos.")
                .setPositiveButton("Ver permisos", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Cerrar", null).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BT) {
            if (result == RESULT_OK) { if (discoveryDialog == null) openDiscovery(); else beginScan(); }
            else setStatus("Bluetooth sigue apagado. Pulsa buscar para volver a intentarlo.");
        }
    }

    private void playSound() {
        int mode = audioManager.getMode();
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            toast("Termina la llamada antes de comprobar el sonido."); return;
        }
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> { if (change < 0) stopSound(); }, handler).build();
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioManager.abandonAudioFocusRequest(focus); focus = null;
            toast("El audio está ocupado. Intenta de nuevo."); return;
        }
        try {
            player = MediaPlayer.create(this, R.raw.prueba_ascensor, attributes, 0);
            if (player == null) throw new IllegalStateException("Audio unavailable");
            player.setOnCompletionListener(media -> {
                stopSound(); soundStatus.setText("Sonido terminado. Comprueba en qué equipos lo escuchaste.");
            });
            player.setOnErrorListener((media, what, extra) -> { stopSound(); toast("No se pudo reproducir el sonido."); return true; });
            player.start(); soundButton.setText("Detener sonido"); handler.post(soundProgress);
        } catch (RuntimeException ex) { stopSound(); toast("No se pudo reproducir el sonido. Intenta de nuevo."); }
    }

    private void stopSound() {
        handler.removeCallbacks(soundProgress);
        if (player != null) { player.release(); player = null; }
        if (focus != null) { audioManager.abandonAudioFocusRequest(focus); focus = null; }
        if (soundButton != null) soundButton.setText("Escuchar sonido (34 s)");
        if (soundStatus != null) soundStatus.setText("Una voz dice «prueba» con música suave.");
    }

    @Override protected void onStop() {
        active = false;
        adjustingVolume = false;

        handler.removeCallbacks(volumeRefresh);
        if (discoveryDialog != null) discoveryDialog.dismiss();
        stopScan(); stopSound(); handler.removeCallbacks(connectionRefresh); connections.stop(); outputs.stop();
        if (registered) { unregisterReceiver(receiver); registered = false; }
        super.onStop();
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (android.content.ActivityNotFoundException | SecurityException ex) {
            new AlertDialog.Builder(this).setTitle("Abre el ajuste manualmente")
                    .setMessage("El teléfono no permite abrir esta pantalla directamente. Busca la opción en Ajustes del teléfono. Para Opciones de desarrollador, habilítalas primero desde la información del teléfono.")
                    .setPositiveButton("Entendido", null).show();
        }
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private LinearLayout panel() {
        LinearLayout layout = column(); layout.setPadding(dp(16), dp(10), dp(16), dp(14));
        layout.setBackground(card(soft));
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2); spacing.topMargin = dp(16);
        layout.setLayoutParams(spacing); return layout;
    }
    private GradientDrawable card(int color) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(16)); return shape;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(6), 0, dp(6));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private Button button(String text, View.OnClickListener listener) {
        Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextColor(GREEN);
        view.setBackgroundTintList(ColorStateList.valueOf(soft));
        view.setTextSize(15); view.setMinHeight(dp(48)); view.setOnClickListener(listener); return view;
    }
}
