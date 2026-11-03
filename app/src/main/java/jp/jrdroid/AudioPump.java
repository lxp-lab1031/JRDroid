package jp.jrdroid;

import java.util.function.BooleanSupplier;

/** Opens, uses and closes the output on the same audio worker. */
final class AudioPump {
    interface Factory { PcmPlayback.Output open() throws Exception; }
    static void run(ReceiverSession session,Factory factory) throws Exception {
        if (!session.isActive()) return;
        BooleanSupplier active=session::isActive;
        try (PcmPlayback.Output output=factory.open()) {
            PcmPlayback playback=new PcmPlayback();
            while (session.isActive()) {
                short[] pcm=session.nextAudio(playback.isPlaying());
                if (pcm==null) { playback.pauseIfDrained(output); continue; }
                playback.write(output,pcm,active);
            }
        }
    }
    private AudioPump() {}
}
