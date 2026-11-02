package jp.jrdroid;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Small playback state machine; Android adapter performs blocking writes. */
final class PcmPlayback {
    interface Output {
        void play();
        void pause();
        void flush();
        int write(short[] pcm,int offset,int count);
        long playedFrames();
    }
    private boolean playing;
    private long submittedFrames;
    boolean isPlaying() { return playing; }
    void write(Output output,short[] pcm,BooleanSupplier running) throws IOException {
        if (pcm.length == 0 || !running.getAsBoolean()) return;
        if (!playing) { output.play(); playing = true; }
        int offset = 0;
        while (running.getAsBoolean() && offset < pcm.length) {
            int n = output.write(pcm,offset,pcm.length-offset);
            if (!running.getAsBoolean()) return;
            if (n <= 0 || n > pcm.length-offset) throw new IOException("音声出力エラー: "+n);
            offset += n;
            submittedFrames += n;
        }
    }
    boolean pauseIfDrained(Output output) {
        // AudioTrack's playback head is unsigned 32 bit and resets on flush.
        // An idle timeout alone is insufficient: never discard an unplayed tail.
        if (!playing || (submittedFrames & 0xffffffffL) != (output.playedFrames() & 0xffffffffL)) return false;
        output.pause(); output.flush(); submittedFrames = 0; playing = false;
        return true;
    }
}
