package com.builtbydeep.sovra;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public final class WaveformView extends View {
    public interface SeekListener {
        void onSeek(float fraction);
    }

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private byte[] pcm = new byte[0];
    private float progress;
    private SeekListener seekListener;

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        wavePaint.setColor(0xff4a9eff);
        wavePaint.setStrokeWidth(3f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        playheadPaint.setColor(0xff9d4edd);
        playheadPaint.setStrokeWidth(4f);
    }

    public void setPcm(byte[] samples) {
        pcm = samples == null ? new byte[0] : samples;
        progress = 0f;
        invalidate();
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public void setSeekListener(SeekListener listener) {
        seekListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float center = getHeight() / 2f;
        float halfHeight = Math.max(1f, center - 12f);
        int samples = pcm.length / 2;
        int bars = Math.max(1, (int) (getWidth() / 7f));
        float step = getWidth() / (float) bars;
        for (int i = 0; i < bars; i++) {
            float amplitude = 0.08f;
            if (samples > 0) {
                int sampleIndex = Math.min(samples - 1, (int) ((long) i * samples / bars));
                int offset = sampleIndex * 2;
                short value = (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
                amplitude = Math.max(0.08f, Math.abs(value / 32768f));
            }
            float x = i * step + step / 2f;
            float extent = amplitude * halfHeight;
            canvas.drawLine(x, center - extent, x, center + extent, wavePaint);
        }
        canvas.drawLine(progress * getWidth(), 4f, progress * getWidth(),
                getHeight() - 4f, playheadPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && getWidth() > 0) {
            float fraction = Math.max(0f, Math.min(1f, event.getX() / getWidth()));
            setProgress(fraction);
            if (seekListener != null) {
                seekListener.onSeek(fraction);
            }
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
