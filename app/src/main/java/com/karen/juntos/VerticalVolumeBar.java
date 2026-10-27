package com.karen.juntos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.SeekBar;

/** Native range control drawn bottom-to-top, preserving SeekBar accessibility. */
final class VerticalVolumeBar extends SeekBar {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnSeekBarChangeListener listener;
    private int touchPointer = -1;
    VerticalVolumeBar(Context context) {
        super(context);
        // Prevent locale mirroring: upward always increases volume.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setBackground(null);
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float inset = 14 * density;
        float x = getWidth() / 2f;
        float bottom = getHeight() - inset;
        float top = inset;
        float y = bottom - (bottom - top) * getProgress() / Math.max(1f, getMax());
        int color = getProgressTintList() == null ? 0xff2167b5 : getProgressTintList().getDefaultColor();
        paint.setColor(color);
        paint.setAlpha(isEnabled() ? 45 : 35);
        paint.setStrokeWidth(20 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(x, top, x, bottom, paint);
        if (isEnabled()) {
            paint.setAlpha(255);
            canvas.drawLine(x, bottom, x, y, paint);
            canvas.drawCircle(x, y, (isPressed() || isFocused() ? 13 : 11) * density, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchPointer = event.getPointerId(0);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                setPressed(true); requestFocus();
                if (listener != null) listener.onStartTrackingTouch(this);
                updateFromTouch(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(touchPointer);
                if (index >= 0) updateFromTouch(event.getY(index));
                return true;
            case MotionEvent.ACTION_UP:
                if (touchPointer < 0) return false;
                updateFromTouch(event.getY());
                finishTouch(); performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                finishTouch();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == touchPointer) finishTouch();
                return true;
            default:
                return true;
        }
    }

    @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener) {
        this.listener = listener;
        super.setOnSeekBarChangeListener(listener); // Keep native keyboard and accessibility events.
    }

    private void updateFromTouch(float y) {
        float inset = 14 * getResources().getDisplayMetrics().density;
        float fraction = Math.max(0, Math.min(1,
                (getHeight() - inset - y) / Math.max(1, getHeight() - 2 * inset)));
        int value = Math.round(fraction * getMax());
        if (value == getProgress()) return;
        setProgress(value);
        if (listener != null) listener.onProgressChanged(this, value, true);
    }

    private void finishTouch() {
        if (touchPointer < 0) return;
        touchPointer = -1; setPressed(false);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (listener != null) listener.onStopTrackingTouch(this);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
        return super.onKeyDown(keyCode, event);
    }
}
