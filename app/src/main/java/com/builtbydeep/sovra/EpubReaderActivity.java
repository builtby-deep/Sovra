package com.builtbydeep.sovra;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Locale;

public final class EpubReaderActivity extends AppCompatActivity {
    private TextView nameView;
    private TextView previewView;
    private final ActivityResultLauncher<String[]> documentPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocument);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_epub_reader);
        nameView = findViewById(R.id.document_name);
        previewView = findViewById(R.id.document_preview);
        findViewById(R.id.open_document).setOnClickListener(view ->
                documentPicker.launch(new String[]{"application/epub+zip", "application/pdf",
                        "text/plain", "application/octet-stream"}));
        Uri incoming = getIntent().getData();
        if (incoming != null) onDocument(incoming);
    }

    private void onDocument(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some document providers do not offer persistable grants.
        }
        String name = displayName(uri);
        nameView.setText(name);
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf") || lowerName.endsWith(".txt")
                || "text/plain".equalsIgnoreCase(getContentResolver().getType(uri))) {
            try {
                previewView.setText(TextImportHelper.read(this, uri));
            } catch (IOException exception) {
                Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            previewView.setText("EPUB selected. Reader rendering is not available in this preview.");
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        }
        return uri.getLastPathSegment() == null ? "Selected document" : uri.getLastPathSegment();
    }
}
