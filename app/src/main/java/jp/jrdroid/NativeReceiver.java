package jp.jrdroid;

/** Confined to the owning DSP worker thread. No handle crosses into the UI. */
final class NativeReceiver implements AutoCloseable {
    static { System.loadLibrary("jrdroid"); }
    private long handle = create();
    short[] decode(byte[] iq, int count) { return process(handle, iq, count); }
    String snapshot() { return status(handle); }
    @Override public void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long create();
    private static native short[] process(long handle, byte[] iq, int count);
    private static native String status(long handle);
    private static native void destroy(long handle);
}
