package dev.inknfclab;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class LogExportProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("ink-nfc-[0-9-]+\\.zip") || uri.getPathSegments().size() != 1) throw new FileNotFoundException("Invalid export");
        File f = new File(new File(getContext().getFilesDir(), "exports"), name);
        if (!f.isFile()) throw new FileNotFoundException(name);
        return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "application/zip"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File f = file(uri); String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
            for (int i=0;i<columns.length;i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? f.getName() : OpenableColumns.SIZE.equals(columns[i]) ? f.length() : null;
            cursor.addRow(row); return cursor;
        } catch (IOException e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] args) { throw new UnsupportedOperationException(); }
}
