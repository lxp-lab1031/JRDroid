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
import android.text.InputType;
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
    private boolean visible, sharing;
    private int selectedFrequencyHz = DChannels.DEFAULT_HZ;
    private int launchingFrequencyHz = DChannels.DEFAULT_HZ;
    private String launchError = "";
    private TextView stateView, usbView, detailView, errorView, countersView, frequencyView, channelView, profileNotice, captureInfo;
    private Button start, stop, channelButton, lowerFrequency, higherFrequency, captureButton, shareCapture;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ReceiverService.LocalBinder)binder).service(); service.setUiVisible(true); render();
        }
        public void onServiceDisconnected(ComponentName name) { service = null; }
    };
    private final Runnable poll = new Runnable() {
        public void run() { render(); handler.postDelayed(this,1000); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        CrashDiagnostics.install(this);
        int stored = getSharedPreferences("receiver_settings",MODE_PRIVATE).getInt("frequency_hz",DChannels.DEFAULT_HZ);
        selectedFrequencyHz = RadioChannels.restoreFrequency(stored);
        if(selectedFrequencyHz!=stored)
            getSharedPreferences("receiver_settings",MODE_PRIVATE).edit().putInt("frequency_hz",selectedFrequencyHz).apply();
        if (saved != null) {
            launching = saved.getBoolean("launching");
            int pending = saved.getInt("launching_frequency_hz",selectedFrequencyHz);
            launchingFrequencyHz = RadioChannels.contains(pending) ? pending : selectedFrequencyHz;
        }
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
        channelView = text(body,"",14,0xff62d7bb,true);
        profileNotice = text(body,"146 MHz帯の基地局を試験受信します。京急等の音声復号は未確認です。",13,0xffffd596,false);
        frequencyView = text(body,RadioChannels.mhz(selectedFrequencyHz),42,0xffedf5ff,true);
        text(body,"MHz   /   RTL2832U・R820T",14,0xff9eafc5,false);
        channelButton = button(body,"受信対象・周波数を選択",false);
        channelButton.setOnClickListener(v -> chooseChannel());
        LinearLayout steps = new LinearLayout(this); body.addView(steps);
        lowerFrequency = stepButton(steps,"−6.25 kHz");
        higherFrequency = stepButton(steps,"＋6.25 kHz");
        lowerFrequency.setOnClickListener(v -> selectFrequency(RadioChannels.neighbor(selectedFrequencyHz,-1)));
        higherFrequency.setOnClickListener(v -> selectFrequency(RadioChannels.neighbor(selectedFrequencyHz,1)));
        text(body,"停止中に選局 → 受信開始。146 MHz帯は京急・京成・北総の基地局設定です。",12,0xff9eafc5,false);
        space(body,12);
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
        captureButton = button(body,"調査用 IQ を5秒取得",false);
        captureButton.setOnClickListener(v -> {
            if(service!=null && service.requestIqCapture()) render();
            else Toast.makeText(this,"受信中に開始してください。保存中は次の取得を開始できません。",Toast.LENGTH_LONG).show();
        });
        shareCapture = button(body,"保存した IQ を共有",false);
        shareCapture.setOnClickListener(v -> shareIqCapture());
        captureInfo = text(body,"",12,0xff9eafc5,false);
        space(body,18);
        text(body,"240 kS/s  ·  RF 40.2 dB  ·  補正 0 ppm",13,0xff9eafc5,false);
        text(body,"スマホ内で復調・音声合成・再生\nPC・外部サーバー・受信中のインターネットは不要",13,0xff9eafc5,false);
        text(body,"他アプリの音声とミックス再生します。音量・出力先はスマホのメディア設定に従います。",13,0xff9eafc5,false);
        space(body,16);
        text(body,"JRのD4第1波・旅客一斉、TikTokミックス・Bluetooth出力は実機報告があります。146 MHz帯の音声復号は未確認です。",13,0xffffd596,false);
        TextView driverLink = text(body,"ドライバーの導入案内を開く ↗",13,0xff62d7bb,false);
        driverLink.setPadding(0,dp(20),0,dp(8));
        driverLink.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+DRIVER))));
        TextView licenseLink = text(body,"オープンソースライセンス",13,0xff9eafc5,false);
        licenseLink.setOnClickListener(v -> showLicenses());
        TextView diagnosticsLink = text(body,"停止・クラッシュの診断情報",13,0xff62d7bb,false);
        diagnosticsLink.setPadding(0,dp(16),0,dp(12));
        diagnosticsLink.setOnClickListener(v -> showDiagnostics());
        setContentView(scroll);
    }
    @Override protected void onStart() {
        super.onStart();
        visible=true;
        bound = bindService(new Intent(this,ReceiverService.class),connection,BIND_AUTO_CREATE);
        handler.post(poll);
    }
    @Override protected void onStop() {
        visible=false;
        handler.removeCallbacks(poll);
        if (service != null) service.setUiVisible(false);
        if (bound) { unbindService(connection); bound = false; service = null; }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("launching",launching);
        out.putInt("launching_frequency_hz",launchingFrequencyHz);
        super.onSaveInstanceState(out);
    }
    private boolean tuningLocked() { return launching || service == null || service.busy(); }
    private void selectFrequency(int hz) {
        if (tuningLocked() || !RadioChannels.contains(hz)) return;
        selectedFrequencyHz = hz;
        getSharedPreferences("receiver_settings",MODE_PRIVATE).edit().putInt("frequency_hz",hz).apply();
        launchError = "";
        render();
    }
    private void chooseChannel() {
        if (tuningLocked()) return;
        new AlertDialog.Builder(this).setTitle("受信対象")
            .setItems(new String[]{"JR Dタイプ · 352 MHz帯","京急・京成・北総 基地局 · 146 MHz帯（試験）"},(dialog,which) -> chooseChannelGroup(which==1))
            .setNegativeButton("閉じる",null).show();
    }
    private void chooseChannelGroup(boolean experimental) {
        if(tuningLocked()) return;
        String[] options = new String[RadioChannels.count(experimental)];
        for (int i=0;i<options.length;i++) options[i] = RadioChannels.option(RadioChannels.at(experimental,i));
        int checked=RadioChannels.isExperimental(selectedFrequencyHz)==experimental?RadioChannels.index(selectedFrequencyHz):-1;
        new AlertDialog.Builder(this).setTitle(experimental?"146 MHz帯 · 基地局（復号未確認）":"JR Dタイプ · 352 MHz帯")
            .setSingleChoiceItems(options,checked,(dialog,which) -> {
                selectFrequency(RadioChannels.at(experimental,which)); dialog.dismiss();
            })
            .setNeutralButton("MHz を直接入力",(dialog,which) -> enterFrequency())
            .setNegativeButton("閉じる",null).show();
    }
    private void enterFrequency() {
        if (tuningLocked()) return;
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setText(RadioChannels.mhz(selectedFrequencyHz)); input.selectAll();
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setPadding(dp(24),dp(12),dp(24),dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("受信周波数 (MHz)")
            .setMessage("JRの22波、または146 MHz帯の基地局16波を指定してください。例: 146.48125 / 352.56250")
            .setView(input).setPositiveButton("設定",null).setNegativeButton("キャンセル",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                int hz = RadioChannels.parseMhz(input.getText().toString());
                if (tuningLocked()) { input.setError("受信を停止してから変更してください。"); return; }
                selectFrequency(hz); dialog.dismiss();
            } catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        }));
        dialog.show();
    }
    private void launchDriver() {
        if (tuningLocked()) return;
        launchingFrequencyHz = selectedFrequencyHz;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse("iqsrc://-a 127.0.0.1 -p "+RtlTcp.PORT+
            " -s "+RtlTcp.SAMPLE_RATE+" -f "+launchingFrequencyHz+" -g 40 -P 0"));
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
            startIntent.putExtra(ReceiverService.FREQUENCY_EXTRA,launchingFrequencyHz);
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
        int shownFrequency = busy ? s.frequencyHz : launching ? launchingFrequencyHz : selectedFrequencyHz;
        boolean experimental=RadioChannels.isExperimental(shownFrequency);
        frequencyView.setText(RadioChannels.mhz(shownFrequency));
        channelView.setText(RadioChannels.label(shownFrequency)+" · 1 波受信");
        profileNotice.setVisibility(experimental?View.VISIBLE:View.GONE);
        boolean selectable = !tuningLocked();
        channelButton.setEnabled(selectable);
        lowerFrequency.setText(experimental?"前の設定":"−6.25 kHz");
        higherFrequency.setText(experimental?"次の設定":"＋6.25 kHz");
        lowerFrequency.setEnabled(selectable && RadioChannels.neighbor(selectedFrequencyHz,-1)!=selectedFrequencyHz);
        higherFrequency.setEnabled(selectable && RadioChannels.neighbor(selectedFrequencyHz,1)!=selectedFrequencyHz);
        boolean capturing=s!=null && s.captureBusy();
        captureButton.setEnabled(busy && !capturing);
        shareCapture.setEnabled(IqCaptureProvider.latest(this).isFile() && !capturing && !sharing);
        captureInfo.setText(s==null?"":s.captureState);
        stateView.setText(launching ? "ドライバーで USB 許可を確認中" : s == null ? "停止中" : s.state);
        start.setEnabled(selectable); stop.setEnabled(busy);
        UsbManager usb = getSystemService(UsbManager.class);
        int count = usb == null ? 0 : usb.getDeviceList().size();
        usbView.setText((s != null && busy ? s.tuner : "チューナー未接続")+"\nUSB 機器: "+count+" 台（アクセス許可はドライバーで確認）");
        String error = !launchError.isEmpty() ? launchError : s == null ? "" : s.error;
        errorView.setText(error); errorView.setVisibility(error.isEmpty() ? View.GONE : View.VISIBLE);
        try {
            JSONObject j = new JSONObject(s == null || (!busy && s.frequencyHz != selectedFrequencyHz) ? "{}" : s.stats);
            countersView.setText(String.format(Locale.JAPAN,"同期フレーム  %,d\n有効音声  %,d  /  PCM  %,d",j.optLong("synchronized_frames"),j.optLong("voice_frames"),j.optLong("audio_frames")));
            String evm = j.isNull("evm_percent") ? "未計測" : String.format(Locale.JAPAN,"%.1f %%",j.optDouble("evm_percent",0));
            detailView.setText(String.format(Locale.JAPAN,"IQ: %,d samples\n入力: %.1f dBFS  /  EVM: %s\n周波数ずれ: %+.1f Hz",j.optLong("iq_samples"),j.optDouble("input_dbfs",-120),evm,j.optDouble("frequency_error_hz",0)));
        } catch (org.json.JSONException e) { detailView.setText("受信統計を読み取れません"); }
    }
    private void shareIqCapture() {
        if(sharing || !IqCaptureProvider.latest(this).isFile()) return;
        sharing=true; render();
        Context app=getApplicationContext();
        new Thread(() -> {
            try {
                Uri uri=IqCaptureProvider.prepareShare(app);
                runOnUiThread(() -> {
                    sharing=false;
                    if(!visible || isFinishing() || isDestroyed()) return;
                    render();
                    Intent send=new Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newUri(getContentResolver(),"JRDroid IQ",uri));
                    try { startActivity(Intent.createChooser(send,"調査用IQを共有")); }
                    catch(ActivityNotFoundException e) { Toast.makeText(this,"共有できるアプリが見つかりません",Toast.LENGTH_LONG).show(); }
                });
            } catch(java.io.IOException | RuntimeException e) {
                runOnUiThread(() -> {
                    sharing=false;
                    if(visible && !isFinishing()) { render();Toast.makeText(this,"IQの共有準備に失敗しました",Toast.LENGTH_LONG).show(); }
                });
            }
        },"JRDroid-IQ-share").start();
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
    private void showDiagnostics() {
        TextView view=new TextView(this); view.setText("診断情報を読み込み中…"); view.setTextSize(12);
        view.setPadding(dp(20),dp(12),dp(20),dp(12)); view.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this); scroll.addView(view);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("停止・クラッシュの診断")
            .setView(scroll).setNegativeButton("閉じる",null).setPositiveButton("コピー",(d,which) -> {
                getSystemService(android.content.ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText("JRDroid 診断",view.getText()));
                Toast.makeText(this,"診断情報をコピーしました",Toast.LENGTH_SHORT).show();
            }).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        Context app=getApplicationContext();
        new Thread(() -> {
            String report=CrashDiagnostics.describe(app);
            runOnUiThread(() -> {
                if (!isFinishing() && dialog.isShowing()) {
                    view.setText(report); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            });
        },"JRDroid-diagnostics-read").start();
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
    private Button stepButton(LinearLayout box,String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(0xffedf5ff); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff24364b));
        box.addView(b,new LinearLayout.LayoutParams(0,dp(48),1)); return b;
    }
    private void space(LinearLayout box,int height) { box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
