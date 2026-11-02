package jp.jrdroid;

public final class DChannelsTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        if (args.length>0 && args[0].equals("--dump")) {
            for (int i=0;i<DChannels.count();i++) {
                int hz = DChannels.at(i); System.out.println(hz+"\t"+DChannels.label(hz));
            }
            return;
        }
        check(DChannels.count()==22);
        check(DChannels.at(0)==352537500 && DChannels.at(21)==352668750);
        for (int i=0;i<DChannels.count();i++) {
            int hz=DChannels.at(i);
            check(DChannels.parseMhz(DChannels.mhz(hz))==hz);
            check(DChannels.index(hz)==i);
            check(!DChannels.contains(hz+1));
            check(!DChannels.contains(hz-1));
            if (i>0) check(hz-DChannels.at(i-1)==6250);
        }
        check(DChannels.label(352556250).equals("D1 音声第1波"));
        check(DChannels.label(352618750).equals("D1 音声第2波"));
        check(DChannels.parseMhz(" 352.625000 ")==DChannels.DEFAULT_HZ);
        for (String bad:new String[]{"", "NaN", "Infinity", "3.52625e2", "-352.625", "352,625", "352.625001", "352.53749", "352.675", "352.5562501"}) {
            try { DChannels.parseMhz(bad); throw new AssertionError(bad); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: D-channel boundaries, exact MHz round trips, steps and invalid input");
    }
}
