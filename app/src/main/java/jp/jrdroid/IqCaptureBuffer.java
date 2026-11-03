package jp.jrdroid;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded five-second capture. No storage I/O runs on the DSP thread. */
final class IqCaptureBuffer {
    static final int SAMPLE_RATE=240000, LIMIT_BYTES=SAMPLE_RATE*2*5;
    private final int frequency;
    private final long startedAt=System.currentTimeMillis();
    private final byte[] bytes=new byte[LIMIT_BYTES];
    private int used;
    IqCaptureBuffer(int hz) { frequency=RadioChannels.require(hz); }
    void append(byte[] block,int count) {
        if(count<0 || count>block.length) throw new IllegalArgumentException("Invalid IQ length");
        int copied=Math.min(count,LIMIT_BYTES-used);
        System.arraycopy(block,0,bytes,used,copied); used+=copied;
    }
    boolean complete() { return used==LIMIT_BYTES; }
    int size() { return used; }
    void writeTo(OutputStream output) throws IOException {
        String header="{\"format\":\"JRDROID-IQ\",\"version\":1,\"frequency_hz\":"+frequency+
            ",\"sample_rate_hz\":"+SAMPLE_RATE+",\"encoding\":\"u8-iq-interleaved\",\"captured_bytes\":"+used+
            ",\"requested_bytes\":"+LIMIT_BYTES+",\"complete\":"+complete()+",\"frequency_is_requested\":true"+
            ",\"capture_started_ms\":"+startedAt+",\"requested_gain_tenths_db\":402,\"requested_ppm\":0}\n";
        output.write(header.getBytes(StandardCharsets.UTF_8)); output.write(bytes,0,used);
    }
}
