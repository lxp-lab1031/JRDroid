package jp.jrdroid;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** On-device diagnostics only. No log upload and no additional permissions. */
final class CrashDiagnostics {
    private static boolean installed;
    static synchronized void install(Context context) {
        if (installed) return;
        installed=true;
        Context app=context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
            try { save(app,"last-crash.txt","thread="+thread.getName(),error); }
            catch (Throwable ignored) { /* Never prevent the system crash handler. */ }
            finally {
                if (previous!=null) previous.uncaughtException(thread,error);
                else android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }
    static void record(Context context,String stage,Throwable error) {
        Context app=context.getApplicationContext();
        Runnable write=() -> save(app,"last-event.txt",stage,error);
        if (Looper.myLooper()==Looper.getMainLooper()) new Thread(write,"JRDroid-diagnostics").start();
        else write.run();
    }
    private static synchronized void save(Context app,String filename,String stage,Throwable error) {
        try {
            StringWriter trace=new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            String text=date(System.currentTimeMillis())+" / "+stage+"\n"+trace;
            if (text.length()>32768) text=text.substring(0,32768);
            try (Writer out=new OutputStreamWriter(new FileOutputStream(new File(app.getFilesDir(),filename)),StandardCharsets.UTF_8)) { out.write(text); }
        } catch (IOException | RuntimeException ignored) { /* Diagnostics must not break reception. */ }
    }
    static String describe(Context app) {
        StringBuilder text=new StringBuilder("JRDroid 診断情報\n");
        try {
            text.append("package: ").append(app.getPackageName()).append('\n');
            text.append("version: ").append(app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName).append('\n');
            text.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            text.append("model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            for (String name:new String[]{"last-crash.txt","last-event.txt"}) {
                File file=new File(app.getFilesDir(),name);
                if (file.isFile()) try (InputStream input=new FileInputStream(file)) {
                    text.append("\n--- ").append(name).append(" ---\n").append(read(input));
                }
            }
            if (Build.VERSION.SDK_INT>=30) {
                text.append("\n--- Android 終了履歴（このアプリのみ）---\n");
                List<ApplicationExitInfo> exits=app.getSystemService(ActivityManager.class)
                    .getHistoricalProcessExitReasons(app.getPackageName(),0,8);
                boolean traced=false;
                for (ApplicationExitInfo exit:exits) {
                    int reason=exit.getReason();
                    text.append(date(exit.getTimestamp())).append(" reason=").append(reasonName(reason))
                        .append(" status=").append(exit.getStatus()).append('\n').append(exit.getDescription()).append('\n');
                    if (!traced && (reason==ApplicationExitInfo.REASON_ANR || reason==ApplicationExitInfo.REASON_CRASH || reason==ApplicationExitInfo.REASON_CRASH_NATIVE)) {
                        try (InputStream input=exit.getTraceInputStream()) {
                            if (input!=null) { text.append("--- trace ---\n").append(read(input)).append('\n'); traced=true; }
                        } catch (IOException | RuntimeException e) { text.append("trace: ").append(e.getClass().getSimpleName()).append('\n'); }
                    }
                }
                if (exits.isEmpty()) text.append("終了履歴なし\n");
            } else text.append("Android 終了履歴は Android 11 以降で利用できます。\n");
        } catch (Exception e) { text.append("診断読取: ").append(e).append('\n'); }
        return text.toString();
    }
    @android.annotation.TargetApi(30)
    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR:return "ANR";
            case ApplicationExitInfo.REASON_CRASH:return "JAVA_CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:return "NATIVE_CRASH";
            case ApplicationExitInfo.REASON_LOW_MEMORY:return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_USER_REQUESTED:return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_SIGNALED:return "SIGNALED";
            default:return Integer.toString(reason);
        }
    }
    private static String date(long time) { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.JAPAN).format(new Date(time)); }
    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] block=new byte[4096];
        while (bytes.size()<65536) {
            int n=input.read(block,0,Math.min(block.length,65536-bytes.size()));
            if (n<0) break;
            if (n>0) bytes.write(block,0,n);
        }
        return bytes.toString("UTF-8");
    }
    private CrashDiagnostics() {}
}
