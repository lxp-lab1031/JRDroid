package jp.jrdroid;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** These are JR synthetic waveforms; they do not validate a Keikyu air interface. */
public final class PrivateBaseStationTest {
    private static void check(boolean value) { if(!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        int[] expected={146493750,146481250,146506250,146518750,146500000,146487500,146512500,146525000,
            146550000,146537500,146562500,146575000,146818750,146806250,146831250,146843750};
        check(RadioChannels.count(true)==16);
        for(int i=0;i<DChannels.count();i++) {
            int hz=DChannels.at(i);check(!RadioChannels.isExperimental(hz));check(RadioChannels.label(hz).equals(DChannels.label(hz)));check(RadioChannels.restoreFrequency(hz)==hz);
        }
        byte[] iq=Files.readAllBytes(Path.of(args[0]));
        for(int i=0;i<expected.length;i++) {
            int hz=RadioChannels.at(true,i);check(hz==expected[i]);
            check(RadioChannels.isExperimental(hz));check(RadioChannels.parseMhz(RadioChannels.mhz(hz))==hz);
            check(RadioChannels.label(hz).contains("基地局"));
            check(RadioChannels.label(hz).contains(i%4==0?"データ":"音声"));
            check(RadioChannels.restoreFrequency(hz)==hz);
            int mobile=hz-4000000;
            check(!RadioChannels.contains(mobile));check(!RadioChannels.isExperimental(mobile));
            check(RadioChannels.restoreFrequency(mobile)==hz);
            try { RadioChannels.parseMhz(String.format(Locale.ROOT,"%.5f",mobile/1000000.0));throw new AssertionError(); }
            catch(IllegalArgumentException expectedError) { }
            ByteArrayOutputStream rejected=new ByteArrayOutputStream();
            try { RtlTcp.configure(rejected,null,mobile);throw new AssertionError(); }
            catch(IllegalArgumentException expectedError) {check(rejected.size()==0);}
            try(NativeReceiver unused=new NativeReceiver(mobile)) {throw new AssertionError();}
            catch(IllegalArgumentException expectedError) { }
            ByteArrayOutputStream command=new ByteArrayOutputStream();RtlTcp.configure(command,null,hz);
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(command.toByteArray()));
            check(in.readUnsignedByte()==2);check(in.readInt()==240000);check(in.readUnsignedByte()==1);check(in.readInt()==hz);
            try(NativeReceiver r=new NativeReceiver(hz)) {
                check(r.snapshot(false).contains("\"frequency_hz\":"+hz));
                check(r.snapshot(false).contains("\"experimental_private\":true"));
                int samples=0;
                for(int at=0;at<iq.length;at+=32768) {
                    byte[] part=Arrays.copyOfRange(iq,at,Math.min(iq.length,at+32768));samples+=r.decode(part,part.length).length;
                }
                check(samples>=3200);
            }
        }
        for(int invalid:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,142493751,146493751})
            check(RadioChannels.restoreFrequency(invalid)==DChannels.DEFAULT_HZ);
        check(RadioChannels.neighbor(expected[0],-1)==expected[0]);
        check(RadioChannels.neighbor(expected[15],1)==expected[15]);
        for(int hz:new int[]{146000000,146493751,146600000,352500000}) check(!RadioChannels.contains(hz));
        System.out.println("PASS: 16 base-station presets reach tuner/JNI; 142 MHz rejected and saved settings migrated; synthetic JR PCM only");
        IqCaptureBuffer capture=new IqCaptureBuffer(expected[1]);byte[] block=new byte[32768];
        int position=0;
        while(!capture.complete()) {
            for(int i=0;i<block.length;i++) block[i]=(byte)(position+i);
            capture.append(block,block.length);position+=block.length;
        }
        capture.append(new byte[]{9},1);check(capture.size()==2400000);
        ByteArrayOutputStream data=new ByteArrayOutputStream();capture.writeTo(data);byte[] saved=data.toByteArray();
        int headerEnd=0;while(saved[headerEnd]!='\n') headerEnd++;
        String header=new String(saved,0,headerEnd,StandardCharsets.UTF_8);
        check(header.contains("\"frequency_hz\":146481250"));check(header.contains("\"complete\":true"));
        check(saved.length-headerEnd-1==2400000);
        for(int i=0;i<2400000;i++) check(saved[headerEnd+1+i]==(byte)i);
        IqCaptureBuffer partial=new IqCaptureBuffer(DChannels.DEFAULT_HZ);partial.append(new byte[137],137);
        ByteArrayOutputStream small=new ByteArrayOutputStream();partial.writeTo(small);
        String partialHeader=new String(small.toByteArray(),StandardCharsets.UTF_8).split("\n",2)[0];
        check(partialHeader.contains("\"complete\":false"));check(partialHeader.contains("\"captured_bytes\":137"));
        try { partial.append(new byte[2],3);throw new AssertionError(); } catch(IllegalArgumentException expectedError) { }
        System.out.println("PASS: five-second IQ bound, byte identity, metadata and incomplete capture labeling");
        Path cache=Files.createTempDirectory("jrd-iq-share-test");
        try {
            File latest=IqCaptureFiles.latest(cache.toFile());latest.getParentFile().mkdirs();Files.write(latest.toPath(),new byte[]{1,2,3});
            File first=IqCaptureFiles.prepareShare(cache.toFile());Files.write(latest.toPath(),new byte[]{9,8});
            File second=IqCaptureFiles.prepareShare(cache.toFile());
            check(!first.equals(second));check(Arrays.equals(Files.readAllBytes(first.toPath()),new byte[]{1,2,3}));
            check(Arrays.equals(Files.readAllBytes(second.toPath()),new byte[]{9,8}));
            try { IqCaptureFiles.resolveShare(cache.toFile(),"../../secret");throw new AssertionError(); }
            catch(FileNotFoundException expectedFailure) { }
            for(int i=0;i<6;i++) IqCaptureFiles.prepareShare(cache.toFile());
            try(java.util.stream.Stream<Path> copies=Files.list(cache.resolve("iq-shares"))) {check(copies.count()<=4);}
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(cache)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
        }
        System.out.println("PASS: immutable capture shares, path validation and bounded share cache");
        if(args.length>1) {
            IqCaptureBuffer synthetic=new IqCaptureBuffer(expected[1]);synthetic.append(iq,iq.length);
            try(OutputStream stream=Files.newOutputStream(Path.of(args[1]))) { synthetic.writeTo(stream); }
        }
    }
}
