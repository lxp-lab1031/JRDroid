package jp.jrdroid;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.net.*;

/** Each run retains its resources until their owning workers have exited. */
public final class ReceiverService extends Service {
    static final String START="jp.jrdroid.START", STOP="jp.jrdroid.STOP";
    static final String FREQUENCY_EXTRA="frequency_hz";
    private static final String CHANNEL="receiver";
    private static final int NOTIFICATION=1;
    public final class LocalBinder extends Binder { ReceiverService service() { return ReceiverService.this; } }
    private final LocalBinder binder=new LocalBinder();
    private final Handler main=new Handler(Looper.getMainLooper());
    volatile String state="停止中", error="", stats="{}", tuner="未接続";
    volatile int frequencyHz=DChannels.DEFAULT_HZ;
    private volatile Run current;
    private volatile boolean uiVisible, refreshStats;
    private AudioManager audioManager;
    private PowerManager power;

    private static final class Run {
        final ReceiverSession session=new ReceiverSession();
        final int frequency;
        Run(int frequency) { this.frequency=frequency; }
    }
    void setUiVisible(boolean visible) { uiVisible=visible; if (visible) refreshStats=true; }
    @Override public void onCreate() {
        super.onCreate();
        CrashDiagnostics.install(this);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL,"列車無線の受信",NotificationManager.IMPORTANCE_LOW));
        audioManager=getSystemService(AudioManager.class);
        power=getSystemService(PowerManager.class);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if (intent==null) { stopReception(); if (current==null) stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) { stopReception(); return START_NOT_STICKY; }
        if (START.equals(intent.getAction()) && current==null) {
            int hz=intent.getIntExtra(FREQUENCY_EXTRA,DChannels.DEFAULT_HZ);
            if (!DChannels.contains(hz)) {
                error="受信周波数が D タイプの対応範囲外です。"; state="設定エラー";
                stopSelf(); return START_NOT_STICKY;
            }
            Run run=new Run(hz); current=run; frequencyHz=hz;
            error=""; stats="{}"; tuner="未接続"; state="ドライバーに接続中";
            try {
                startForeground(NOTIFICATION,notification());
                int[] commands=intent.getIntArrayExtra("commands");
                new Thread(null,() -> receive(run,commands),"JRDroid-DSP",8*1024*1024).start();
            } catch (RuntimeException e) {
                run.session.requestStop(); current=null;
                error=explain(e); state="受信エラー";
                CrashDiagnostics.record(this,"start",e);
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            }
        }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ReceiverService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("JRDroid · "+DChannels.mhz(frequencyHz)+" MHz")
            .setContentText(DChannels.label(frequencyHz)+" · タップして状態を表示")
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
    }
    private void receive(Run run,int[] commands) {
        NativeReceiver receiver=null;
        PowerManager.WakeLock wake=null;
        AudioFocusRequest focus=null;
        try {
            wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"JRDroid:receiver");
            wake.setReferenceCounted(false);
            wake.acquire(10*60*1000L); long lastWake=SystemClock.elapsedRealtime();
            AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change<0 && current==run && run.session.isActive()) {
                        error="音声フォーカスを失いました。受信開始で再開してください。";
                        stop(run);
                    }
                },main).build();
            if (audioManager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new IOException("音声出力を使用できません。他の音声アプリを停止してください。");
            run.session.startAudio(() -> playAudio(run,attributes));
            Socket socket=new Socket();
            if (!run.session.attach(socket)) throw new InterruptedIOException("停止しました");
            socket.connect(new InetSocketAddress("127.0.0.1",RtlTcp.PORT),5000);
            socket.setSoTimeout(5000); socket.setTcpNoDelay(true); socket.setReceiveBufferSize(131072);
            DataInputStream input=new DataInputStream(socket.getInputStream());
            RtlTcp.Header header=RtlTcp.readHeader(input);
            tuner="RTL2832U / "+header.tunerName();
            run.session.configure(socket,commands,run.frequency);
            receiver=new NativeReceiver(run.frequency);
            state="IQ 受信中・フレーム同期待ち";
            byte[] block=new byte[IqBlockReader.BLOCK_BYTES];
            int settle=RtlTcp.SAMPLE_RATE/2; long lastStatus=0;
            while (run.session.isActive()) {
                int n=IqBlockReader.read(input,block);
                if (n<0) throw new EOFException("SDR ドライバーとの接続が切れました");
                if (settle>0) {
                    int discard=Math.min(settle,n); settle-=discard;
                    if (discard==n) continue;
                    System.arraycopy(block,discard,block,0,n-discard); n-=discard;
                }
                run.session.offer(receiver.decode(block,n));
                long now=SystemClock.elapsedRealtime();
                if (now-lastWake>=5*60*1000L) { wake.acquire(10*60*1000L); lastWake=now; }
                boolean diagnostics=uiVisible;
                if (refreshStats || now-lastStatus>=(diagnostics?1000:5000)) {
                    refreshStats=false; stats=receiver.snapshot(diagnostics);
                    state=new org.json.JSONObject(stats).optBoolean("frame_locked") ? "フレーム同期中" : "IQ 受信中・フレーム同期待ち";
                    lastStatus=now;
                }
            }
        } catch (Exception | LinkageError e) {
            if (run.session.isActive()) fail(run,"receiver",e);
        } finally {
            // No timed join followed by cross-thread AudioTrack.release().
            run.session.awaitStopped();
            if (run.session.closeFailure!=null) CrashDiagnostics.record(this,"socket-close",run.session.closeFailure);
            if (receiver!=null) { NativeReceiver owned=receiver; cleanup(run,"native-close",owned::close); }
            if (focus!=null) { AudioFocusRequest owned=focus; cleanup(run,"focus-release",() -> audioManager.abandonAudioFocusRequest(owned)); }
            if (wake!=null) { PowerManager.WakeLock owned=wake; cleanup(run,"wake-release",() -> { if (owned.isHeld()) owned.release(); }); }
            main.post(() -> {
                if (current!=run) return;
                cleanup(run,"foreground-stop",() -> stopForeground(STOP_FOREGROUND_REMOVE));
                state=error.isEmpty()?"停止中":"受信エラー";
                current=null; stopSelf();
            });
        }
    }
    private void playAudio(Run run,AudioAttributes attributes) {
        try { AudioPump.run(run.session,() -> openOutput(run,attributes)); }
        catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
        catch (Exception | LinkageError e) { if (run.session.isActive()) fail(run,"audio",e); }
    }
    private PcmPlayback.Output openOutput(Run run,AudioAttributes attributes) throws IOException {
        int buffer=Math.max(3200,AudioTrack.getMinBufferSize(8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));
        final AudioTrack output=new AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(buffer).build();
        try {
            if (output.getState()!=AudioTrack.STATE_INITIALIZED) throw new IOException("AudioTrack の初期化に失敗しました");
            for (AudioDeviceInfo device:audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                if (device.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { output.setPreferredDevice(device); break; }
        } catch (IOException | RuntimeException e) { cleanup(run,"audio-init-release",output::release); throw e; }
        return new PcmPlayback.Output() {
            public void play() { output.play(); }
            public void pause() { output.pause(); }
            public void flush() { output.flush(); }
            public int write(short[] pcm,int offset,int count) { return output.write(pcm,offset,count,AudioTrack.WRITE_NON_BLOCKING); }
            public long playedFrames() { return Integer.toUnsignedLong(output.getPlaybackHeadPosition()); }
            public void close() {
                cleanup(run,"audio-pause",output::pause);
                cleanup(run,"audio-flush",output::flush);
                cleanup(run,"audio-release",output::release);
            }
        };
    }
    private void fail(Run run,String stage,Throwable failure) {
        if (current!=run) return;
        error=explain(failure); state="受信エラー";
        CrashDiagnostics.record(this,stage,failure);
        stop(run);
    }
    private void cleanup(Run run,String stage,Runnable task) {
        try { task.run(); }
        catch (RuntimeException | LinkageError e) {
            CrashDiagnostics.record(this,stage,e);
            if (current==run && error.isEmpty()) error="終了処理エラー: "+stage+" / "+explain(e);
        }
    }
    private void stop(Run run) {
        if (current!=run) return;
        state="停止処理中";
        run.session.requestStop();
    }
    void stopReception() { Run run=current; if (run!=null) stop(run); }
    boolean busy() { return current!=null; }
    private String explain(Throwable e) {
        if (e instanceof SocketTimeoutException) return "SDR から5秒間応答がありません。USB 接続・給電・ドライバーを確認してください。";
        if (e instanceof ConnectException) return "端末内の SDR ドライバーに接続できません。ドライバーを再起動してください。";
        return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    }
    @Override public void onDestroy() { stopReception(); super.onDestroy(); }
}
