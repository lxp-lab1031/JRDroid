package jp.jrdroid;

import java.io.IOException;
import java.io.InputStream;

/** Coalesces fragmented TCP reads into bounded IQ blocks without dropping bytes. */
final class IqBlockReader {
    static final int BLOCK_BYTES = 32768; // About 68 ms at 240 k complex samples/s.
    static int read(InputStream input, byte[] block) throws IOException {
        if (block.length == 0) throw new IllegalArgumentException("Empty IQ buffer");
        int used = 0;
        while (used < block.length) {
            int n = input.read(block,used,block.length-used);
            if (n < 0) return used == 0 ? -1 : used;
            if (n == 0) {
                int one = input.read();
                if (one < 0) return used == 0 ? -1 : used;
                block[used++] = (byte)one;
            } else used += n;
        }
        return used;
    }
    private IqBlockReader() {}
}
