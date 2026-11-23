package com.builtbydeep.sovra;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AudioHelper {

    public static String saveMp3File(byte[] mp3Data, String fileName, Context context) {
        if (mp3Data == null || mp3Data.length == 0) return "";
        try {
            File musicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC), "VoxEngine");
            if (!musicDir.exists() && !musicDir.mkdirs()) return "";
            File mp3File = new File(musicDir, fileName);
            try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(mp3File))) {
                output.write(mp3Data);
            }
            return mp3File.getAbsolutePath();
        } catch (java.io.IOException error) {
            android.util.Log.e("AudioHelper", "Unable to save MP3 audio.", error);
            return "";
        }
    }

    // ✅ FIX: sampleRate parameter add kiya — Kokoro=24000, Piper/Vits=22050
    public static String saveWavFile(byte[] pcmData, String fileName, int sampleRate, Context context) {
        if (pcmData == null) return "";
        return saveWavFile(new java.io.ByteArrayInputStream(pcmData), pcmData.length,
                fileName, sampleRate, context);
    }

    public static String saveWavFile(File pcmFile, String fileName, int sampleRate, Context context) {
        if (pcmFile == null || !pcmFile.isFile()) return "";
        try (BufferedInputStream input =
                     new BufferedInputStream(new FileInputStream(pcmFile))) {
            return saveWavFile(input, pcmFile.length(), fileName, sampleRate, context);
        } catch (Exception e) {
            return "";
        }
    }

    private static String saveWavFile(java.io.InputStream pcmInput, long audioLength,
                                      String fileName, int sampleRate, Context context) {
        try {
            if (audioLength > 0xffffffffL - 36) return "";
            File musicDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC), "VoxEngine");
            if (!musicDir.exists()) musicDir.mkdirs();

            File wavFile = new File(musicDir, fileName);

            try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(wavFile))) {
                long totalAudioLen = audioLength;
                long totalDataLen  = totalAudioLen + 36;
                long longSampleRate = sampleRate; // ✅ Ab hardcoded nahi, jo pass hoga wahi jayega
                int  channels      = 1;
                long byteRate      = 16L * longSampleRate * channels / 8;

                byte[] header = new byte[44];
                header[0]  = 'R'; header[1]  = 'I'; header[2]  = 'F'; header[3]  = 'F';
                header[4]  = (byte) (totalDataLen & 0xff);
                header[5]  = (byte) ((totalDataLen >> 8)  & 0xff);
                header[6]  = (byte) ((totalDataLen >> 16) & 0xff);
                header[7]  = (byte) ((totalDataLen >> 24) & 0xff);
                header[8]  = 'W'; header[9]  = 'A'; header[10] = 'V'; header[11] = 'E';
                header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
                header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
                header[20] = 1;  header[21] = 0;
                header[22] = (byte) channels; header[23] = 0;
                header[24] = (byte) (longSampleRate & 0xff);
                header[25] = (byte) ((longSampleRate >> 8)  & 0xff);
                header[26] = (byte) ((longSampleRate >> 16) & 0xff);
                header[27] = (byte) ((longSampleRate >> 24) & 0xff);
                header[28] = (byte) (byteRate & 0xff);
                header[29] = (byte) ((byteRate >> 8)  & 0xff);
                header[30] = (byte) ((byteRate >> 16) & 0xff);
                header[31] = (byte) ((byteRate >> 24) & 0xff);
                header[32] = (byte) (2 * 16 / 8); header[33] = 0;
                header[34] = 16; header[35] = 0;
                header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
                header[40] = (byte) (totalAudioLen & 0xff);
                header[41] = (byte) ((totalAudioLen >> 8)  & 0xff);
                header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
                header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

                fos.write(header, 0, 44);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long bytesCopied = 0;
                while ((bytesRead = pcmInput.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    bytesCopied += bytesRead;
                }
                if (bytesCopied != audioLength) {
                    wavFile.delete();
                    return "";
                }
            }

            return wavFile.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }
}
