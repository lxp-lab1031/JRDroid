package jp.jrdroid;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int DRIVER_REQUEST = 7;
    private static final String DRIVER = "marto.rtl_tcp_andro";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ReceiverService service;
    private boolean bound, launching;
    private String launchError = "";
    private TextView stateView, usbView, detailView, errorView, countersView;
    private Button start, stop;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ReceiverService.LocalBinder)binder).service(); render();
        }
        public void onServiceDisconnected(ComponentName name) { service = null; }
    };
    private final Runnable poll = new Runnable() {
        public void run() { render(); handler.postDelayed(this,500); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) launching = saved.getBoolean("launching");
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24),dp(22),dp(24),dp(30)); scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top; bottom = bars.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            body.setPadding(dp(24),top+dp(18),dp(24),bottom+dp(24));
            return insets;
        });
        text(body,"JRDroid",32,0xffedf5ff,true);
        text(body,"JR 在来線デジタル列車無線",14,0xff9eafc5,false);
        space(body,24);
        text(body,"旅客一斉 · 1 波受信",14,0xff62d7bb,true);
        text(body,"352.6250",46,0xffedf5ff,true);
        text(body,"MHz   /   RTL2832U・R820T",14,0xff9eafc5,false);
        space(body,24);
        LinearLayout statusCard = card(body);
        text(statusCard,"受信状態",12,0xff9eafc5,false);
        stateView = text(statusCard,"停止中",20,0xff62d7bb,true);
        usbView = text(statusCard,"",13,0xffc0ccdc,false);
        countersView = text(statusCard,"",14,0xffedf5ff,false);
        detailView = text(statusCard,"",12,0xff9eafc5,false);
        space(body,18);
        start = button(body,"受信開始",true); start.setOnClickListener(v -> launchDriver());
        stop = button(body,"停止",false); stop.setOnClickListener(v -> { if (service != null) service.stopReception(); });
        errorView = text(body,"",14,0xffffb5a9,false); errorView.setTextIsSelectable(true);
        space(body,18);
        text(body,"240 kS/s  ·  RF 40.2 dB  ·  補正 0 ppm",13,0xff9eafc5,false);
        text(body,"スマホ内で復調・音声合成・再生\nPC・外部サーバー・受信中のインターネットは不要",13,0xff9eafc5,false);
        space(body,16);
        text(body,"実機評価前の α 版です。音声品質・同期性能は未確認です。データ電文の表示は未実装です。",13,0xffffd596,false);
        TextView driverLink = text(body,"ドライバーの導入案内を開く ↗",13,0xff62d7bb,false);
        driverLink.setPadding(0,dp(20),0,dp(8));
        driverLink.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+DRIVER))));
        TextView licenseLink = text(body,"オープンソースライセンス",13,0xff9eafc5,false);
        licenseLink.setOnClickListener(v -> showLicenses());
        setContentView(scroll);
    }
    @Override protected void onStart() {
        super.onStart();
        bound = bindService(new Intent(this,ReceiverService.class),connection,BIND_AUTO_CREATE);
        handler.post(poll);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(poll);
        if (bound) { unbindService(connection); bound = false; service = null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("launching",launching); super.onSaveInstanceState(out); }
    private void launchDriver() {
        if (launching || (service != null && service.busy())) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse("iqsrc://-a 127.0.0.1 -p "+RtlTcp.PORT+
            " -s "+RtlTcp.SAMPLE_RATE+" -f "+RtlTcp.FREQUENCY+" -g 40 -P 0"));
        intent.setPackage(DRIVER);
        try { launching = true; launchError = ""; startActivityForResult(intent,DRIVER_REQUEST); }
        catch (ActivityNotFoundException e) {
            launching = false; launchError = "RTL-SDR ドライバーがありません。導入案内からインストールしてください。初回導入には通信が必要です。";
        }
        render();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != DRIVER_REQUEST) return;
        launching = false;
        if (result == RESULT_OK) {
            Intent startIntent = new Intent(this,ReceiverService.class).setAction(ReceiverService.START);
            if (data != null) startIntent.putExtra("commands",data.getIntArrayExtra("supportedTcpCommands"));
            try { startForegroundService(startIntent); launchError = ""; }
            catch (RuntimeException e) { launchError = "受信サービスを開始できません: "+e.getMessage(); }
        } else {
            String detail = data == null ? null : data.getStringExtra("detailed_exception_message");
            launchError = "ドライバーを開始できませんでした。USB の接続・給電・アクセス許可を確認してください。"+(detail == null ? "" : "\n"+detail);
        }
        render();
    }
    private void render() {
        if (stateView == null) return;
        ReceiverService s = service;
        boolean busy = s != null && s.busy();
        stateView.setText(launching ? "ドライバーで USB 許可を確認中" : s == null ? "停止中" : s.state);
        start.setEnabled(!busy && !launching); stop.setEnabled(busy);
        UsbManager usb = getSystemService(UsbManager.class);
        int count = usb == null ? 0 : usb.getDeviceList().size();
        usbView.setText((s != null && busy ? s.tuner : "チューナー未接続")+"\nUSB 機器: "+count+" 台（アクセス許可はドライバーで確認）");
        String error = !launchError.isEmpty() ? launchError : s == null ? "" : s.error;
        errorView.setText(error); errorView.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
        try {
            JSONObject j = new JSONObject(s == null ? "{}" : s.stats);
            countersView.setText(String.format(Locale.JAPAN,"同期フレーム  %,d\n有効音声  %,d  /  PCM  %,d",j.optLong("synchronized_frames"),j.optLong("voice_frames"),j.optLong("audio_frames")));
            detailView.setText(String.format(Locale.JAPAN,"IQ: %,d samples\n入力: %.1f dBFS  /  EVM: %.1f %%\n周波数ずれ: %+.1f Hz",j.optLong("iq_samples"),j.optDouble("input_dbfs",-120),j.optDouble("evm_percent",0),j.optDouble("frequency_error_hz",0)));
        } catch (org.json.JSONException e) { detailView.setText("受信統計を読み取れません"); }
    }
    private void showLicenses() {
        try (java.io.InputStream in = getAssets().open("THIRD_PARTY_NOTICES.txt")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] block = new byte[8192]; int n;
            while ((n = in.read(block)) != -1) bytes.write(block,0,n);
            String text = bytes.toString("UTF-8");
            TextView view = new TextView(this); view.setText(text); view.setTextSize(12); view.setPadding(dp(20),dp(12),dp(20),dp(12)); view.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this); scroll.addView(view);
            new AlertDialog.Builder(this).setTitle("ライセンス").setView(scroll).setPositiveButton("閉じる",null).show();
        } catch (java.io.IOException e) { launchError = e.getMessage(); render(); }
    }
    private TextView text(LinearLayout box,String value,int sp,int color,boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setPadding(0,dp(4),0,dp(4));
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(t,new LinearLayout.LayoutParams(-1,-2)); return t;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(14),dp(18),dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xff152333); bg.setCornerRadius(dp(18)); box.setBackground(bg);
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2)); return box;
    }
    private Button button(LinearLayout box,String label,boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(17); b.setAllCaps(false); b.setTextColor(primary ? 0xff0b1420 : 0xffedf5ff);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? 0xff62d7bb : 0xff24364b));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(58)); p.bottomMargin=dp(8); box.addView(b,p); return b;
    }
    private void space(LinearLayout box,int height) { box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
