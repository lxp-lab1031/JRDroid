package jp.jrdroid;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/** Standard unsigned 8-bit RTL-TCP protocol. Never connects off the device. */
final class RtlTcp {
    static final int PORT = 12345;
    static final int SAMPLE_RATE = 240000;
    static final int GAIN_TENTHS = 402;
    static final class Header {
        final int tuner, gainCount;
        Header(int tuner, int gains) { this.tuner = tuner; gainCount = gains; }
        String tunerName() {
            switch (tuner) { case 5: return "R820T"; case 6: return "R828D"; default: return "tuner " + tuner; }
        }
    }
    static Header readHeader(DataInputStream in) throws IOException {
        byte[] magic = new byte[4]; in.readFully(magic);
        if (!Arrays.equals(magic, new byte[]{'R','T','L','0'})) throw new IOException("RTL-TCP ヘッダーが不正です");
        Header h = new Header(in.readInt(), in.readInt());
        if (h.tuner != 5 && h.tuner != 6) throw new IOException("R820T/R828D 以外のチューナーです: " + h.tuner);
        return h;
    }
    static void command(OutputStream out, int id, int value) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeByte(id); data.writeInt(value); data.flush();
    }
    static void configure(OutputStream out, int[] supported, int frequencyHz) throws IOException {
        DChannels.require(frequencyHz);
        int[] required = {1,2,3,4,5,8};
        if (supported != null) for (int id : required) {
            boolean found = false;
            for (int command : supported) if (command == id) found = true;
            if (!found) throw new IOException("ドライバーが必要な RTL-TCP コマンドに非対応です: " + id);
        }
        command(out, 2, SAMPLE_RATE);
        command(out, 1, frequencyHz);
        command(out, 5, 0); // frequency correction: 0 ppm
        command(out, 8, 0); // RTL2832 digital AGC off
        command(out, 3, 1); // manual tuner gain
        command(out, 4, GAIN_TENTHS);
    }
    private RtlTcp() {}
}
