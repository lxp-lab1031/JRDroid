package jp.jrdroid;
import java.io.*;
import java.util.Arrays;

/** Runs on the host JVM, without Android stubs. */
public final class RtlTcpProtocolTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeBytes("RTL0"); h.writeInt(5); h.writeInt(29);
        RtlTcp.Header parsed = RtlTcp.readHeader(new DataInputStream(new ByteArrayInputStream(header.toByteArray())));
        check(parsed.tunerName().equals("R820T")); check(parsed.gainCount == 29);
        System.out.println("PASS: RTL0 header and R820T identification");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RtlTcp.configure(output,new int[]{1,2,3,4,5,8},DChannels.DEFAULT_HZ);
        DataInputStream commands = new DataInputStream(new ByteArrayInputStream(output.toByteArray()));
        int[][] expected = {{2,240000},{1,352625000},{5,0},{8,0},{3,1},{4,402}};
        for (int[] command : expected) { check(commands.readUnsignedByte()==command[0]); check(commands.readInt()==command[1]); }
        check(commands.available()==0);
        System.out.println("PASS: frequency, sample rate, manual gain, ppm and AGC command bytes");
        try { RtlTcp.configure(new ByteArrayOutputStream(),new int[]{1,2},DChannels.DEFAULT_HZ); throw new AssertionError(); }
        catch (IOException expectedFailure) { check(expectedFailure.getMessage().contains("非対応")); }
        System.out.println("PASS: missing driver capabilities are rejected");
        for (byte[] bad : new byte[][] {new byte[12],Arrays.copyOf(header.toByteArray(),7)}) {
            try { RtlTcp.readHeader(new DataInputStream(new ByteArrayInputStream(bad))); throw new AssertionError(); }
            catch (IOException expectedFailure) { }
        }
        System.out.println("PASS: invalid and truncated headers are rejected");
        for (int i=0;i<DChannels.count();i++) {
            int hz = DChannels.at(i);
            ByteArrayOutputStream selected = new ByteArrayOutputStream();
            RtlTcp.configure(selected,null,hz);
            DataInputStream bytes = new DataInputStream(new ByteArrayInputStream(selected.toByteArray()));
            check(bytes.readUnsignedByte()==2); check(bytes.readInt()==240000);
            check(bytes.readUnsignedByte()==1); check(bytes.readInt()==hz);
        }
        ByteArrayOutputStream invalid = new ByteArrayOutputStream();
        try { RtlTcp.configure(invalid,null,DChannels.MIN_HZ+1); throw new AssertionError(); }
        catch (IllegalArgumentException expectedFailure) { check(invalid.size()==0); }
        System.out.println("PASS: all 22 frequency commands and pre-write validation");
    }
}
