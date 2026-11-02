package jp.jrdroid;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
public class PowerBenchmark {
    public static void main(String[] args) throws Exception {
        byte[] voice=Files.readAllBytes(Path.of(args[0]));
        String mode=args[1]; int size=mode.equals("baseline")?16384:32768;
        int intervalSamples=mode.equals("baseline")?96000:mode.equals("hidden")?1200000:240000;
        boolean full=!mode.equals("hidden");
        Method selective=null;
        try { selective=NativeReceiver.class.getDeclaredMethod("snapshot",boolean.class); }
        catch (NoSuchMethodException ignored) { }
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long id=Thread.currentThread().getId();
        for (boolean speech:new boolean[]{false,true}) {
            byte[] input=new byte[4320000];
            if (speech) for(int i=0;i<input.length;i++) input[i]=voice[i%voice.length];
            else Arrays.fill(input,(byte)127);
            long[] times=new long[3],allocations=new long[3]; long expectedPcm=-1;
            for(int trial=-1;trial<3;trial++) {
                byte[] block=new byte[size];
                try(NativeReceiver receiver=new NativeReceiver()) {
                    long before=bean.getCurrentThreadCpuTime(), memory=bean.getThreadAllocatedBytes(id);
                    long samples=0;int sinceStatus=0;
                    for(int at=0;at<input.length;at+=size) {
                        int n=Math.min(size,input.length-at);System.arraycopy(input,at,block,0,n);
                        samples+=receiver.decode(block,n).length;sinceStatus+=n/2;
                        if(sinceStatus>=intervalSamples) {
                            if(selective==null) receiver.snapshot();else selective.invoke(receiver,full);
                            sinceStatus=0;
                        }
                    }
                    long cpu=bean.getCurrentThreadCpuTime()-before;
                    long allocated=bean.getThreadAllocatedBytes(id)-memory;
                    if(trial>=0){times[trial]=cpu;allocations[trial]=allocated;}
                    if(expectedPcm!=-1 && samples!=expectedPcm) throw new AssertionError("Nonrepeatable PCM");
                    expectedPcm=samples;
                }
            }
            Arrays.sort(times);Arrays.sort(allocations);
            System.out.printf(Locale.ROOT,"%s %s: CPU_ms_per_9s_IQ=%.3f Java_allocated_bytes=%d PCM_samples=%d%n",mode,speech?"voice":"idle",times[1]/1e6,allocations[1],expectedPcm);
        }
    }
}
