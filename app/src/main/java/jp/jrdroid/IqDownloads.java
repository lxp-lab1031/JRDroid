package jp.jrdroid;

import android.annotation.TargetApi;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android storage adapter. No Activity is retained by the export worker. */
final class IqDownloads {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static volatile String state="";
    static boolean isBusy() { return BUSY.get(); }
    static String status() { return state; }
    static String fileName() {
        return "JRDroid-IQ-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+
            "-"+UUID.randomUUID().toString().substring(0,8)+".zip";
    }
    static boolean save(Context context,Uri document) {
        if(!BUSY.compareAndSet(false,true)) return false;
        Context app=context.getApplicationContext();
        state="IQをZIPにまとめて保存しています…";
        new Thread(() -> {
            try {
                String name=fileName();
                IqCaptureExport.Target target;
                if(document!=null) target=new DocumentTarget(app.getContentResolver(),document);
                else {
                    if(Build.VERSION.SDK_INT<29) throw new IOException("保存先を選択してください");
                    target=new DownloadTarget(app.getContentResolver(),name);
                }
                IqCaptureExport.write(IqCaptureProvider.latest(app),target);
                state=document==null?"保存しました：ダウンロード/JRDroid/"+name:
                    "IQのZIPを選択した保存先に保存しました。";
            } catch(IOException | RuntimeException e) {
                state="IQを保存できませんでした。空き容量・保存先を確認し、もう一度試してください。";
                CrashDiagnostics.record(app,"iq-export",e);
            } finally { BUSY.set(false); }
        },"JRDroid-IQ-export").start();
        return true;
    }
    @TargetApi(29)
    private static final class DownloadTarget implements IqCaptureExport.Target {
        private final ContentResolver resolver;
        private final String name;
        private Uri uri;
        DownloadTarget(ContentResolver resolver,String name) { this.resolver=resolver;this.name=name; }
        public OutputStream open() throws IOException {
            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
            values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/JRDroid");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null) throw new IOException("ダウンロードへの保存を開始できません");
            return resolver.openOutputStream(uri,"w");
        }
        public void publish() throws IOException {
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(uri,values,null,null)!=1) throw new IOException("保存を完了できません");
        }
        public void abort() { if(uri!=null) resolver.delete(uri,null,null); }
    }
    private static final class DocumentTarget implements IqCaptureExport.Target {
        private final ContentResolver resolver;
        private final Uri uri;
        DocumentTarget(ContentResolver resolver,Uri uri) { this.resolver=resolver;this.uri=uri; }
        public OutputStream open() throws IOException { return resolver.openOutputStream(uri,"w"); }
        public void publish() {}
        public void abort() throws IOException {
            if(!DocumentsContract.deleteDocument(resolver,uri)) throw new IOException("未完成の保存ファイルを削除できません");
        }
    }
    private IqDownloads() {}
}
