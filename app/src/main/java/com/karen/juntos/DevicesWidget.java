package com.karen.juntos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import java.util.List;

public class DevicesWidget extends AppWidgetProvider {
    // Optional system broadcast: not all manufacturers expose accessory batteries.
    static final String BATTERY_ACTION = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    static final String BATTERY_EXTRA = "android.bluetooth.device.extra.BATTERY_LEVEL";
    static SharedPreferences data(Context c) { return c.getSharedPreferences("widget_data", Context.MODE_PRIVATE); }
    static SharedPreferences style(Context c, int id) { return c.getSharedPreferences("widget_style_" + id, Context.MODE_PRIVATE); }

    static void battery(Context c, Intent intent) {
        if (!BATTERY_ACTION.equals(intent.getAction())) return;
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int level = intent.getIntExtra(BATTERY_EXTRA, -1);
        if (device == null) return;
        String key = "battery:bt:" + device.getAddress();
        data(c).edit().putInt(key, level >= 0 && level <= 100 ? level : -1)
                .putLong(key + ":time", System.currentTimeMillis()).apply();
        updateAll(c);
    }

    static void publish(Context c, List<AudioOutputs.Entry> entries) {
        SharedPreferences p = data(c);
        String signature = "";
        for (AudioOutputs.Entry e : entries) signature += e.id + ":" + e.connected + ":" + e.name + ":" + e.icon + ";";
        long now = System.currentTimeMillis();
        if (signature.equals(p.getString("signature", "")) && now - p.getLong("updated", 0) < 60000) return;
        SharedPreferences.Editor edit = p.edit().putString("signature", signature).putLong("updated", now);
        for (int i = 0; i < 2; i++) {
            AudioOutputs.Entry e = entries.get(i);
            String oldId = p.getString("id" + i, "");
            if (!e.connected || !oldId.equals(e.id)) edit.remove("battery:" + oldId).remove("battery:" + oldId + ":time");
            edit.putString("id" + i, e.id).putString("name" + i, e.connected ? e.name : "Conectar dispositivo")
                    .putBoolean("connected" + i, e.connected).putInt("icon" + i, e.icon);
        }
        edit.apply(); updateAll(c);
    }

    static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : manager.getAppWidgetIds(new ComponentName(c, DevicesWidget.class)))
            manager.updateAppWidget(id, views(c, id, -1, -1));
    }

    static Bitmap background(int color, int opacity) {
        Bitmap bitmap = Bitmap.createBitmap(600, 176, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color); paint.setAlpha(opacity * 255 / 100);
        canvas.drawRoundRect(new RectF(2, 2, 598, 174), 86, 86, paint);
        paint.setShader(new LinearGradient(0, 0, 600, 176, new int[]{0x99FFFFFF, 0x18FFFFFF, 0x70000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        paint.setAlpha(opacity * 255 / 100);
        canvas.drawRoundRect(new RectF(2, 2, 598, 174), 86, 86, paint);
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
        paint.setColor(0xFF8FA79B); paint.setAlpha(opacity * 255 / 100);
        canvas.drawRoundRect(new RectF(2, 2, 598, 174), 86, 86, paint);
        return bitmap;
    }

    static Bitmap batteryArc(int level) {
        Bitmap bitmap = Bitmap.createBitmap(114, 102, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10); paint.setStrokeCap(Paint.Cap.ROUND);
        RectF arc = new RectF(12, 9, 102, 99);
        paint.setColor(0x667F8984); canvas.drawArc(arc, 150, 240, false, paint);
        if (level > 0) {
            int start = level <= 20 ? 0xFFFFAD73 : 0xFF8EF4CE;
            int end = level <= 20 ? 0xFFE65C67 : 0xFF00CD8D;
            paint.setShader(new LinearGradient(12, 90, 102, 9, start, end, Shader.TileMode.CLAMP));
            canvas.drawArc(arc, 150, 240 * Math.min(100, level) / 100f, false, paint);
        }
        return bitmap;
    }

    static RemoteViews views(Context c, int id, int previewColor, int previewOpacity) {
        SharedPreferences p = data(c), s = style(c, id);
        int color = previewColor == -1 ? s.getInt("color", 0xFF253D34) : previewColor;
        int opacity = previewOpacity == -1 ? s.getInt("opacity", 100) : previewOpacity;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.devices_widget);
        v.setImageViewBitmap(R.id.widget_background, background(color, opacity));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent settings = new Intent(c, WidgetSettingsActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(android.net.Uri.parse("juntos://widget/" + id));
        v.setOnClickPendingIntent(R.id.widget_background, PendingIntent.getActivity(c, id, settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        int[] rows = {R.id.widget_device_1, R.id.widget_device_2};
        int[] icons = {R.id.widget_icon_1, R.id.widget_icon_2};
        int[] bars = {R.id.widget_progress_1, R.id.widget_progress_2};
        long now = System.currentTimeMillis(), updated = p.getLong("updated", 0);
        boolean fresh = now - updated < 120000;
        for (int i = 0; i < 2; i++) {
            boolean connected = p.getBoolean("connected" + i, false);
            String name = p.getString("name" + i, "Conectar dispositivo");
            v.setImageViewResource(icons[i], p.getInt("icon" + i, R.drawable.ic_headphones));
            v.setInt(icons[i], "setColorFilter", 0xFF98E5BE);
            String key = "battery:" + p.getString("id" + i, "");
            int level = fresh && connected && now - p.getLong(key + ":time", 0) < 900000 ? p.getInt(key, -1) : -1;
            v.setImageViewBitmap(bars[i], batteryArc(level));
            v.setContentDescription(rows[i], name + (fresh && connected ? ". Conectado" : ". Conexión sin confirmar") + (level < 0 ? ". Batería no disponible" : ". Batería " + level + " por ciento"));
            PendingIntent connect = PendingIntent.getActivity(c, 100, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(rows[i], connected ? open : connect);
        }
        v.setContentDescription(R.id.widget_background, "Ajustes del widget. " + (updated == 0 ? "Sin lectura disponible" : "Última lectura " + android.text.format.DateFormat.format("HH:mm", updated)));
        return v;
    }

    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (BATTERY_ACTION.equals(intent.getAction())) battery(c, intent);
    }
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        updateAll(c);
        final PendingResult pending = goAsync();
        BluetoothManager bluetooth = c.getSystemService(BluetoothManager.class);
        BluetoothAudioState state = new BluetoothAudioState(c, bluetooth == null ? null : bluetooth.getAdapter(), () -> {});
        AudioOutputs outputs = new AudioOutputs(c, () -> {});
        state.start(); outputs.start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { state.refresh(); publish(c, outputs.panelEntries(state)); }
            finally { state.stop(); outputs.stop(); pending.finish(); }
        }, 1800);
    }
    @Override public void onDeleted(Context c, int[] ids) {
        for (int id : ids) style(c, id).edit().clear().apply();
    }
}
