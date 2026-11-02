package jp.jrdroid;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Owns reception independently of activity redraws and screen power state. */
public final class ReceiverService extends Service {
    static final String START = "jp.jrdroid.START", STOP = "jp.jrdroid.STOP";
    static final String FREQUENCY_EXTRA = "frequency_hz";
    private static final String CHANNEL = "receiver";
    private static final int NOTIFICATION = 1;
    public final class LocalBinder extends Binder { ReceiverService service() { return ReceiverService.this; } }
    private final LocalBinder binder = new LocalBinder();
    volatile String state = "停止中", error = "", stats = "{}", tuner = "未接続";
    volatile boolean running;
    volatile int frequencyHz = DChannels.DEFAULT_HZ;
    private volatile Socket socket;
    private volatile Thread worker, audioWorker;
    private volatile AudioTrack track;
    private final ArrayBlockingQueue<short[]> audio = new ArrayBlockingQueue<>(25);
    private volatile int queuedSamples;
    private PowerManager.WakeLock wake;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private volatile boolean uiVisible, refreshStats;
    void setUiVisible(boolean visible) {
        uiVisible = visible;
        if (visible) refreshStats = true;
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "列車無線の受信", NotificationManager.IMPORTANCE_LOW));
        audioManager = getSystemService(AudioManager.class);
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JRDroid:receiver");
        wake.setReferenceCounted(false);
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) { stopReception(); return START_NOT_STICKY; }
        if (START.equals(intent.getAction()) && worker == null) {
            int requested = intent.getIntExtra(FREQUENCY_EXTRA,DChannels.DEFAULT_HZ);
            if (!DChannels.contains(requested)) {
                error = "受信周波数が D タイプの対応範囲外です。"; state = "設定エラー";
                stopSelf(); return START_NOT_STICKY;
            }
            frequencyHz = requested;
            startForeground(NOTIFICATION, notification());
            running = true; state = "ドライバーに接続中"; error = ""; stats = "{}";
            int[] commands = intent.getIntArrayExtra("commands");
            // JNI model initialization and DSP have a larger stack than a UI callback.
            worker = new Thread(null, () -> receive(commands,requested), "JRDroid-DSP", 8 * 1024 * 1024);
            worker.start();
        }
        return START_NOT_STICKY;
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this, MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this,1,new Intent(this, ReceiverService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(jp.jrdroid.R.drawable.ic_radio)
            .setContentTitle("JRDroid · "+DChannels.mhz(frequencyHz)+" MHz")
            .setContentText(DChannels.label(frequencyHz)+" · タップして状態を表示")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
    }
    private void receive(int[] commands, int selectedFrequency) {
        NativeReceiver nativeRx = null;
        try {
            wake.acquire(10 * 60 * 1000L);
            long lastWake = SystemClock.elapsedRealtime();
            AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && running) { error = "音声フォーカスを失いました。再開するには受信開始を押してください。"; stopReception(); }
                }, new Handler(Looper.getMainLooper())).build();
            if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new IOException("音声出力を使用できません。他の音声アプリを停止してください。");
            int buffer = Math.max(3200, AudioTrack.getMinBufferSize(8000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));
            track = new AudioTrack.Builder().setAudioAttributes(attributes)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(buffer).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("AudioTrack の初期化に失敗しました");
            // Prefer built-in speaker; Bluetooth/wired routing may still be governed by Android.
            for (AudioDeviceInfo d : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { track.setPreferredDevice(d); break; }
            }
            // Open the output now; start playback only when actual PCM arrives.
            audio.clear(); queuedSamples = 0;
            audioWorker = new Thread(this::playAudio, "JRDroid-audio"); audioWorker.start();
            Socket connection = new Socket(); socket = connection;
            if (!running) throw new InterruptedIOException("停止しました");
            connection.connect(new InetSocketAddress("127.0.0.1",RtlTcp.PORT),5000);
            connection.setSoTimeout(5000); connection.setTcpNoDelay(true);
            connection.setReceiveBufferSize(131072);
            DataInputStream in = new DataInputStream(connection.getInputStream());
            RtlTcp.Header header = RtlTcp.readHeader(in);
            tuner = "RTL2832U / " + header.tunerName();
            RtlTcp.configure(connection.getOutputStream(),commands,selectedFrequency);
            nativeRx = new NativeReceiver(selectedFrequency);
            state = "IQ 受信中・フレーム同期待ち";
            byte[] block = new byte[IqBlockReader.BLOCK_BYTES];
            long lastStatus = 0;
            // Start arguments already set the rate/frequency; reject stale startup IQ for 0.25 s.
            int settle = RtlTcp.SAMPLE_RATE / 2;
            while (running) {
                int n = IqBlockReader.read(in,block);
                if (n < 0) throw new EOFException("SDR ドライバーとの接続が切れました");
                if (n == 0) continue;
                if (settle > 0) {
                    int discard = Math.min(settle,n); settle -= discard;
                    if (discard == n) continue;
                    System.arraycopy(block,discard,block,0,n-discard); n -= discard;
                }
                short[] pcm = nativeRx.decode(block,n);
                if (pcm.length > 0) {
                    synchronized (audio) {
                        if (queuedSamples + pcm.length > 16000 || !audio.offer(pcm))
                            throw new IOException("音声処理が追いつきません（2秒超）。受信を停止しました。");
                        queuedSamples += pcm.length;
                    }
                }
                long now = SystemClock.elapsedRealtime();
                if (now-lastWake >= 5*60*1000L) { wake.acquire(10*60*1000L); lastWake = now; }
                boolean diagnostics = uiVisible;
                if (refreshStats || now-lastStatus >= (diagnostics ? 1000 : 5000)) {
                    refreshStats = false;
                    stats = nativeRx.snapshot(diagnostics);
                    org.json.JSONObject s = new org.json.JSONObject(stats);
                    state = s.optBoolean("frame_locked") ? "フレーム同期中" : "IQ 受信中・フレーム同期待ち";
                    lastStatus = now;
                }
            }
        } catch (Exception | LinkageError e) {
            if (running) { error = explain(e); state = "受信エラー"; }
        } finally {
            running = false;
            Socket c = socket; socket = null;
            if (c != null) {
                try { RtlTcp.command(c.getOutputStream(),0x7e,0); } catch (IOException ignored) {}
                try { c.close(); } catch (IOException ignored) {}
            }
            if (nativeRx != null) nativeRx.close();
            pauseAudio(); // Unblock any blocking AudioTrack write before joining.
            Thread a = audioWorker;
            if (a != null) {
                a.interrupt();
                try { a.join(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            AudioTrack t = track; track = null;
            if (t != null) { try { t.pause(); t.flush(); t.release(); } catch (IllegalStateException ignored) {} }
            audio.clear(); queuedSamples = 0; audioWorker = null;
            if (focus != null) { audioManager.abandonAudioFocusRequest(focus); focus = null; }
            if (wake.isHeld()) wake.release();
            state = error.isEmpty() ? "停止中" : "受信エラー";
            new Handler(Looper.getMainLooper()).post(() -> {
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); worker = null;
            });
        }
    }
    private void playAudio() {
        final AudioTrack output = track;
        PcmPlayback playback = new PcmPlayback();
        PcmPlayback.Output adapter = new PcmPlayback.Output() {
            public void play() { output.play(); }
            public void pause() { output.pause(); }
            public void flush() { output.flush(); }
            public int write(short[] pcm,int offset,int count) {
                return output.write(pcm,offset,count,AudioTrack.WRITE_BLOCKING);
            }
            public long playedFrames() { return Integer.toUnsignedLong(output.getPlaybackHeadPosition()); }
        };
        try {
            while (running) {
                short[] pcm = playback.isPlaying() ? audio.poll(1000,TimeUnit.MILLISECONDS) : audio.take();
                if (pcm == null) { playback.pauseIfDrained(adapter); continue; }
                synchronized (audio) { queuedSamples -= pcm.length; }
                playback.write(adapter,pcm,() -> running);
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (Exception e) { if (running) { error = explain(e); state = "音声エラー"; stopReception(); } }
    }
    void stopReception() {
        running = false;
        pauseAudio();
        Thread a = audioWorker;
        if (a != null) a.interrupt();
        if (worker != null) state = "停止処理中";
        Socket c = socket;
        if (c != null) {
            try { RtlTcp.command(c.getOutputStream(),0x7e,0); } catch (IOException ignored) {}
            try { c.close(); } catch (IOException ignored) {}
        }
    }
    private void pauseAudio() {
        AudioTrack t = track;
        if (t != null) { try { t.pause(); } catch (IllegalStateException ignored) {} }
    }
    boolean busy() { return worker != null; }
    private String explain(Throwable e) {
        if (e instanceof SocketTimeoutException) return "SDR から5秒間応答がありません。USB 接続・給電・ドライバーを確認してください。";
        if (e instanceof ConnectException) return "端末内の SDR ドライバーに接続できません。ドライバーを再起動してください。";
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
    @Override public void onDestroy() { stopReception(); super.onDestroy(); }
}
