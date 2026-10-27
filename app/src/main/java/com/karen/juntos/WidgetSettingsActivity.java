package com.karen.juntos;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class WidgetSettingsActivity extends Activity {
    private int widgetId, color, opacity;
    private FrameLayout preview;
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(Color.WHITE); text.setPadding(0, dp(8), 0, dp(8)); return text;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        color = state == null ? DevicesWidget.style(this, widgetId).getInt("color", 0xFF253D34) : state.getInt("color");
        opacity = state == null ? DevicesWidget.style(this, widgetId).getInt("opacity", 100) : state.getInt("opacity");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20)); root.setBackgroundColor(0xFF17251F);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(20) + insets.getSystemWindowInsetLeft(), dp(20) + insets.getSystemWindowInsetTop(),
                    dp(20) + insets.getSystemWindowInsetRight(), dp(20) + insets.getSystemWindowInsetBottom()); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        root.addView(text("Ajustes del widget", 24));
        root.addView(text("Vista previa", 16));
        preview = new FrameLayout(this); preview.setBackgroundColor(0xFF65766D);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(72)));
        root.addView(text("Opacidad del fondo", 18));
        RadioGroup opacityOptions = new RadioGroup(this); opacityOptions.setOrientation(RadioGroup.HORIZONTAL);
        for (int value : new int[]{40, 70, 100}) {
            RadioButton button = new RadioButton(this); button.setId(View.generateViewId());
            button.setText(value + "%"); button.setTextColor(Color.WHITE); button.setChecked(value == opacity);
            opacityOptions.addView(button, new RadioGroup.LayoutParams(0, dp(48), 1));
            button.setOnClickListener(v -> { opacity = value; preview(); });
        }
        root.addView(opacityOptions);
        root.addView(text("Color de fondo", 18));
        String[] labels = {"Verde", "Azul", "Grafito", "Violeta"};
        int[] colors = {0xFF253D34, 0xFF203F65, 0xFF303236, 0xFF49345D};
        RadioGroup colorOptions = new RadioGroup(this);
        for (int i = 0; i < colors.length; i++) {
            final int choice = colors[i]; RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId()); button.setText(labels[i]); button.setTextColor(Color.WHITE);
            button.setChecked(choice == color); colorOptions.addView(button);
            button.setOnClickListener(v -> { color = choice; preview(); });
        }
        root.addView(colorOptions);
        root.addView(text("Solo se muestran los dos equipos y sus arcos de batería. Sin lectura disponible, el arco queda gris. Toca un equipo para abrir Juntos o conectar uno nuevo. Toca el borde del fondo para volver a estos ajustes.\n\nPara quitar el widget, mantenlo pulsado en la pantalla de inicio y elige Eliminar. Los menús al mantener pulsado dependen del escritorio.", 14));
        Button save = new Button(this); save.setText("Guardar widget"); root.addView(save);
        save.setOnClickListener(v -> {
            DevicesWidget.style(this, widgetId).edit().putInt("color", color).putInt("opacity", opacity).apply();
            AppWidgetManager.getInstance(this).updateAppWidget(widgetId, DevicesWidget.views(this, widgetId, -1, -1));
            sendBroadcast(new Intent(this, DevicesWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId}));
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish();
        });
        Button cancel = new Button(this); cancel.setText("Cancelar"); root.addView(cancel);
        cancel.setOnClickListener(v -> finish()); preview();
    }
    private void preview() {
        preview.removeAllViews();
        preview.addView(DevicesWidget.views(this, widgetId, color, opacity).apply(this, preview), new FrameLayout.LayoutParams(-1, -1));
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("color", color); state.putInt("opacity", opacity); super.onSaveInstanceState(state);
    }
}
