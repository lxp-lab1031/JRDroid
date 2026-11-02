package jp.jrdroid;

/** Confined to the owning DSP worker thread. No handle crosses into the UI. */
final class NativeReceiver implements AutoCloseable {
    static { System.loadLibrary("jrdroid"); }
    private long handle;
    private static final short[] EMPTY_PCM = new short[0];
    NativeReceiver() { this(DChannels.DEFAULT_HZ); }
    NativeReceiver(int frequencyHz) { handle = create(DChannels.require(frequencyHz)); }
    short[] decode(byte[] iq, int count) {
        short[] pcm = process(handle, iq, count);
        return pcm == null ? EMPTY_PCM : pcm;
    }
    String snapshot() { return snapshot(true); }
    String snapshot(boolean diagnostics) { return status(handle,diagnostics); }
    @Override public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long create(int frequencyHz);
    private static native short[] process(long handle, byte[] iq, int count);
    private static native String status(long handle,boolean diagnostics);
    private static native void destroy(long handle);
}
