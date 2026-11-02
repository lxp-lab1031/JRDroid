package jp.jrdroid;
import java.nio.file.*;

/** Exercises the same JNI symbols and Java wrapper used by the Android service. */
public final class JniSmokeTest {
    public static void main(String[] args) throws Exception {
        byte[] input = Files.readAllBytes(Path.of(args[0]));
        long samples = 0; boolean nonzero = false;
        try (NativeReceiver r = new NativeReceiver()) {
            for (int at=0; at<input.length; at+=8191) {
                byte[] chunk = java.util.Arrays.copyOfRange(input,at,Math.min(input.length,at+8191));
                short[] pcm = r.decode(chunk,chunk.length); samples += pcm.length;
                for (short s : pcm) if (s != 0) nonzero = true;
            }
            String status = r.snapshot();
            if (samples < 3200 || !nonzero) throw new AssertionError("No decoded PCM: "+status);
            System.out.println("PASS: host JNI IQ → JR sync/voice → PCM, "+samples+" samples; "+status);
        }
        try (NativeReceiver r = new NativeReceiver()) {
            try { r.decode(new byte[4],5); throw new AssertionError("Invalid length accepted"); }
            catch (IllegalStateException expected) { }
        }
        System.out.println("PASS: JNI invalid input raises Java exception, handle closes");
        for (int i=0;i<DChannels.count();i++) {
            int hz = DChannels.at(i);
            try (NativeReceiver r = new NativeReceiver(hz)) {
                if (!r.snapshot().contains("\"frequency_hz\":"+hz)) throw new AssertionError("JNI frequency mismatch: "+hz);
                long count = 0;
                for (int at=0;at<input.length;at+=8191) {
                    byte[] chunk = java.util.Arrays.copyOfRange(input,at,Math.min(input.length,at+8191));
                    count+=r.decode(chunk,chunk.length).length;
                }
                if (count<3200) throw new AssertionError("Missing PCM at "+hz);
            }
        }
        System.out.println("PASS: all 22 selected frequencies reach JNI and synthesize PCM in independent sessions");
    }
}
