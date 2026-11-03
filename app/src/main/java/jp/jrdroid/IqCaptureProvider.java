package jp.jrdroid;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Exposes immutable per-share captures, read-only, via user-granted URIs. */
public final class IqCaptureProvider extends ContentProvider {
    static File latest(Context context) { return IqCaptureFiles.latest(context.getCacheDir()); }
    static Uri prepareShare(Context context) throws IOException {
        File copy=IqCaptureFiles.prepareShare(context.getCacheDir());
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".iq-capture")
            .appendPath("shared").appendPath(copy.getName()).build();
    }
    private File file(Uri uri) {
        if(uri.getPathSegments().size()!=2 || !"shared".equals(uri.getPathSegments().get(0)) || uri.getQuery()!=null)
            throw new IllegalArgumentException("Unknown capture URI");
        try { return IqCaptureFiles.resolveShare(getContext().getCacheDir(),uri.getLastPathSegment()); }
        catch(FileNotFoundException e) { throw new IllegalArgumentException("Unknown capture URI"); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { file(uri); return "application/octet-stream"; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        File file=file(uri);
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);
        if(file.isFile()) {
            Object[] row=new Object[columns.length];
            for(int i=0;i<columns.length;i++) {
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i]="JRDroid-"+file.getName();
                else if(OpenableColumns.SIZE.equals(columns[i])) row[i]=file.length();
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException("Read only"); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException("Read only"); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException("Read only"); }
}
