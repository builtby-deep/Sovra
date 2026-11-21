package com.builtbydeep.sovra;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class WaveformHelper {
    public static Bitmap createWaveformBitmap(java.io.File pcmFile, int width, int height) {
        if (pcmFile == null || !pcmFile.isFile() || pcmFile.length() < 2
                || width <= 0 || height <= 0) return null;

        long fileLength = pcmFile.length();
        long frameCount = fileLength / 2L;
        int numBars = Math.max(1, width / 8);
        int[] amplitudes = new int[numBars];

        try (java.io.RandomAccessFile pcm = new java.io.RandomAccessFile(pcmFile, "r")) {
            byte[] sampleBytes = new byte[2];
            for (int i = 0; i < numBars; i++) {
                long startFrame = i * frameCount / numBars;
                long endFrame = (i + 1L) * frameCount / numBars;
                if (endFrame <= startFrame) continue;

                long barFrames = endFrame - startFrame;
                int samplesToRead = (int) Math.min(256L, barFrames);
                long frameStep = Math.max(1L, barFrames / samplesToRead);
                long sum = 0;
                int count = 0;
                for (int sampleIndex = 0; sampleIndex < samplesToRead; sampleIndex++) {
                    long frame = startFrame + sampleIndex * frameStep;
                    if (frame >= endFrame) break;
                    pcm.seek(frame * 2L);
                    if (pcm.read(sampleBytes) != 2) break;
                    int raw = (sampleBytes[0] & 0xff) | (sampleBytes[1] << 8);
                    sum += Math.abs((short) raw);
                    count++;
                }
                amplitudes[i] = count > 0 ? (int) (sum / count) : 0;
            }
        } catch (java.io.IOException error) {
            android.util.Log.w("WaveformHelper", "Unable to sample PCM waveform.", error);
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#00E5FF"));
        paint.setStrokeWidth(5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        float centerY = height / 2f;
        for (int i = 0; i < numBars; i++) {
            float fraction = (float) amplitudes[i] / 32768f;
            float barHeight = height * fraction * 1.5f;
            if (barHeight < 4f) barHeight = 4f;
            if (barHeight > height) barHeight = height - 4f;
            float x = (i * 8f) + 4f;
            if (x >= width) break;
            canvas.drawLine(x, centerY - barHeight / 2f,
                    x, centerY + barHeight / 2f, paint);
        }
        return bitmap;
    }

    public static Bitmap createWaveformBitmap(byte[] pcmData, int width, int height) {
        if (pcmData == null || pcmData.length == 0 || width == 0 || height == 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        Paint paint = new Paint();
        paint.setColor(Color.parseColor("#00E5FF")); // Blue Color
        paint.setStrokeWidth(5f); // Bar thickness
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        int numBars = width / 8; // Number of vertical lines
        int samplesPerBar = pcmData.length / numBars;
        
        float centerY = height / 2f;

        for (int i = 0; i < numBars; i++) {
            long sum = 0;
            int startIndex = i * samplesPerBar;
            int endIndex = Math.min(startIndex + samplesPerBar, pcmData.length);
            
            // Calculate average amplitude for this bar (16-bit PCM)
            for (int j = startIndex; j < endIndex - 1; j += 2) {
                int sample = (pcmData[j] & 0xFF) | (pcmData[j + 1] << 8);
                sum += Math.abs((short) sample);
            }
            
            int count = (endIndex - startIndex) / 2;
            int average = count > 0 ? (int) (sum / count) : 0;
            
            // Scale amplitude to view height (Max 16-bit value is 32768)
            float fraction = (float) average / 32768f;
            float barHeight = (height * fraction * 1.5f); // 1.5 multiplier for better visual
            if (barHeight < 4f) barHeight = 4f; // Minimum bar height
            if (barHeight > height) barHeight = height - 4f;
            
            float x = (i * 8f) + 4f;
            canvas.drawLine(x, centerY - (barHeight / 2f), x, centerY + (barHeight / 2f), paint);
        }
        
        return bitmap;
    }
}
