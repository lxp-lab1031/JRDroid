package jp.jrdroid;

import java.io.*;
import java.util.*;

/** Verifies audio tails and fragmented IQ survive the power-saving changes. */
public final class PowerPathTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static final class Output implements PcmPlayback.Output {
        final List<Short> samples = new ArrayList<>();
        int plays, pauses, flushes, maxWrite=37;
        long head;
        public void play() { plays++; }
        public void pause() { pauses++; }
        public void flush() { flushes++; head=0; }
        public long playedFrames() { return head; }
        public int write(short[] pcm,int offset,int count) {
            if (maxWrite<=0) return maxWrite;
            int n=Math.min(maxWrite,count);
            for (int i=0;i<n;i++) samples.add(pcm[offset+i]);
            return n;
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] data = new byte[10003];
        for (int i=0;i<data.length;i++) data[i]=(byte)(i*73);
        InputStream fragmented = new ByteArrayInputStream(data) {
            public synchronized int read(byte[] b,int off,int n) { return super.read(b,off,Math.min(n,137)); }
        };
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        byte[] block = new byte[1024];
        int n, reads=0;
        while ((n=IqBlockReader.read(fragmented,block))!=-1) { collected.write(block,0,n); reads++; }
        check(Arrays.equals(data,collected.toByteArray())); check(reads==10);
        try { IqBlockReader.read(new ByteArrayInputStream(data),new byte[0]); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: IQ batching preserves fragmented/odd input and final partial blocks");
        Output out = new Output(); PcmPlayback player = new PcmPlayback();
        short[] pcm = new short[320]; for (int i=0;i<pcm.length;i++) pcm[i]=(short)(i-160);
        player.write(out,new short[0],() -> true); check(out.plays==0);
        player.write(out,pcm,() -> true);
        check(out.plays==1 && out.samples.size()==320);
        for (int i=0;i<320;i++) check(out.samples.get(i)==pcm[i]);
        out.head=319; check(!player.pauseIfDrained(out)); check(out.flushes==0);
        out.head=320; check(player.pauseIfDrained(out)); check(!player.isPlaying());
        player.write(out,pcm,() -> true); check(out.plays==2 && out.samples.size()==640);
        out.head=320; check(player.pauseIfDrained(out)); check(out.flushes==2);
        System.out.println("PASS: partial PCM writes, idle pause after drain and restart preserve audio tails");
        for (int result : new int[]{0,-6}) {
            Output broken = new Output(); broken.maxWrite=result;
            try { new PcmPlayback().write(broken,pcm,() -> true); throw new AssertionError(); }
            catch (IOException expected) { }
        }
        Output stopped=new Output(); new PcmPlayback().write(stopped,pcm,() -> false);check(stopped.plays==0);
        try (NativeReceiver r=new NativeReceiver()) {
            byte[] silence=new byte[32768]; Arrays.fill(silence,(byte)127);
            short[] a=r.decode(silence,silence.length), b=r.decode(silence,silence.length);
            check(a.length==0 && a==b);
            check(r.snapshot(false).contains("\"evm_percent\":null"));
            check(r.snapshot(false).contains("\"diagnostics_current\":false"));
            check(r.snapshot().contains("\"diagnostics_current\":true"));
        }
        System.out.println("PASS: stopped/error audio avoids polling; empty PCM is reused; hidden diagnostics are skipped");
    }
}
