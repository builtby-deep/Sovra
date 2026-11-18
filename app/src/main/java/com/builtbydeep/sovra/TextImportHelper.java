package com.builtbydeep.sovra;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextImportHelper {
    public static final int MAX_CHARACTERS = 25000;
    private static final Pattern MISORDERED_DEVANAGARI_MATRA = Pattern.compile(
            "([\\u093F\\u093E-\\u094C\\u094E\\u094F\\u0955-\\u0957])"
                    + "([\\u0915-\\u0939\\u0958-\\u095F])([\\u093C]?)");

    private TextImportHelper() {
    }

    public static String read(Context context, Uri uri) throws IOException {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Context and document URI are required");
        }
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(uri);
        String displayName = getDisplayName(resolver, uri);
        boolean isPdf = "application/pdf".equalsIgnoreCase(mimeType)
                || displayName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        boolean isText = "text/plain".equalsIgnoreCase(mimeType)
                || displayName.toLowerCase(java.util.Locale.ROOT).endsWith(".txt");
        if (!isPdf && !isText) {
            throw new IOException("Only PDF and plain-text documents can be imported");
        }

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to open selected document");
            }
            String text = isPdf ? readPdf(context, input) : readText(input);
            return normalizeDevanagari(limit(text));
        }
    }

    public static String normalizeDevanagari(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        Matcher matcher = MISORDERED_DEVANAGARI_MATRA.matcher(normalized);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(
                            matcher.group(2) + matcher.group(3) + matcher.group(1)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String readPdf(Context context, InputStream input) throws IOException {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        try (PDDocument document = PDDocument.load(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            LimitedWriter output = new LimitedWriter(MAX_CHARACTERS);
            stripper.writeText(document, output);
            return output.toString();
        }
    }

    private static String readText(InputStream input) throws IOException {
        StringBuilder text = new StringBuilder(Math.min(MAX_CHARACTERS, 4096));
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while (text.length() < MAX_CHARACTERS
                    && (count = reader.read(buffer, 0,
                    Math.min(buffer.length, MAX_CHARACTERS - text.length()))) != -1) {
                text.append(buffer, 0, count);
            }
        }
        return text.toString();
    }

    private static String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    return name == null ? "" : name;
                }
            }
        }
        return uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment();
    }

    private static String limit(String text) {
        if (text.length() <= MAX_CHARACTERS) {
            return text;
        }
        int end = MAX_CHARACTERS;
        if (Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static final class LimitedWriter extends Writer {
        private final StringBuilder content = new StringBuilder(MAX_CHARACTERS);
        private final int limit;

        LimitedWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] chars, int offset, int length) {
            int remaining = limit - content.length();
            if (remaining > 0) {
                content.append(chars, offset, Math.min(remaining, length));
            }
        }

        @Override
        public void write(String value, int offset, int length) {
            int remaining = limit - content.length();
            if (remaining > 0) {
                content.append(value, offset, Math.min(remaining, length));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return content.toString();
        }
    }
}
