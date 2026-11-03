package jp.jrdroid;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** One run. Cancellation performs no I/O or joins on its caller. */
final class ReceiverSession {
    private volatile boolean stopped;
    private Socket connection;
    private Thread audioThread, closer;
    private final Object controlLock=new Object();
    private final ArrayBlockingQueue<short[]> audio=new ArrayBlockingQueue<>(25);
    private int queuedSamples;
    volatile RuntimeException closeFailure;
    boolean isActive() { return !stopped; }
    synchronized boolean attach(Socket socket) throws IOException {
        if (stopped) { socket.close(); return false; }
        if (connection!=null) throw new IllegalStateException("Socket already attached");
        connection=socket; return true;
    }
    synchronized void startAudio(Runnable task) {
        if (stopped) return;
        if (audioThread!=null) throw new IllegalStateException("Audio worker already started");
        audioThread=new Thread(task,"JRDroid-audio"); audioThread.start();
    }
    void configure(Socket socket,int[] commands,int frequency) throws IOException {
        synchronized (controlLock) {
            if (stopped) throw new InterruptedIOException("停止しました");
            RtlTcp.configure(socket.getOutputStream(),commands,frequency);
        }
    }
    void offer(short[] pcm) throws IOException {
        if (pcm.length==0 || stopped) return;
        synchronized (audio) {
            if (stopped) return;
            if (queuedSamples+pcm.length>16000 || !audio.offer(pcm))
                throw new IOException("音声処理が追いつきません（2秒超）。受信を停止しました。");
            queuedSamples+=pcm.length;
        }
    }
    short[] nextAudio(boolean playing) throws InterruptedException {
        short[] pcm=playing?audio.poll(1000,TimeUnit.MILLISECONDS):audio.take();
        if (pcm!=null) synchronized (audio) { queuedSamples-=pcm.length; }
        return pcm;
    }
    synchronized void requestStop() {
        if (stopped) return;
        stopped=true;
        if (audioThread!=null) audioThread.interrupt();
        if (connection!=null) {
            Socket owned=connection; connection=null;
            closer=new Thread(() -> closeSocket(owned),"JRDroid-stop");
            // Publish and start together; awaitStopped must not join an unstarted thread.
            closer.start();
        }
    }
    private void closeSocket(Socket owned) {
        try {
            try { owned.shutdownInput(); } catch (IOException ignored) {}
            synchronized (controlLock) { RtlTcp.command(owned.getOutputStream(),0x7e,0); }
        } catch (IOException ignored) {
            // Already disconnected is an ordinary stop condition.
        } catch (RuntimeException e) { closeFailure=e; }
        finally {
            try { owned.close(); } catch (IOException ignored) {}
            catch (RuntimeException e) { closeFailure=e; }
        }
    }
    /** Receiver worker only; resources remain owned until both workers exit. */
    void awaitStopped() {
        requestStop(); Thread a,c;
        synchronized (this) { a=audioThread; c=closer; }
        boolean interrupted=false;
        for (Thread thread:new Thread[]{a,c}) {
            if (thread==null || thread==Thread.currentThread()) continue;
            while (thread.isAlive()) {
                try { thread.join(); } catch (InterruptedException e) { interrupted=true; }
            }
        }
        synchronized (audio) { audio.clear(); queuedSamples=0; }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
