package jp.jrdroid;

import java.math.BigDecimal;
import java.util.Locale;

/** JR presets plus exactly the 16 settings visible in the supplied Windows image.
 * The private-radio labels are not a verified assignment to individual companies. */
final class RadioChannels {
    private static final int[] PRIVATE_HZ={142493750,142481250,142506250,142518750,
        142500000,142487500,142512500,142525000,142550000,142537500,142562500,142575000,
        142818750,142806250,142831250,142843750};
    private static final String[] PRIVATE_LABELS={"K1","K1-1","K1-2","K1-3","K2","K2-1","K2-2","K2-3",
        "K3","K3-1","K3-2","K3-3","北総","北総-1","北総-2","北総-3"};
    static int count(boolean experimental) { return experimental?PRIVATE_HZ.length:DChannels.count(); }
    static int at(boolean experimental,int index) {
        if (index<0 || index>=count(experimental)) throw new IllegalArgumentException("チャンネル番号が不正です");
        return experimental?PRIVATE_HZ[index]:DChannels.at(index);
    }
    static boolean isExperimental(int hz) {
        for(int value:PRIVATE_HZ) if(value==hz) return true;
        return false;
    }
    static boolean contains(int hz) { return DChannels.contains(hz)||isExperimental(hz); }
    static int require(int hz) {
        if(!contains(hz)) throw new IllegalArgumentException("JRの22波または画像由来の142 MHz帯16波を選んでください。");
        return hz;
    }
    static int index(int hz) {
        require(hz);
        if(!isExperimental(hz)) return DChannels.index(hz);
        for(int i=0;i<PRIVATE_HZ.length;i++) if(PRIVATE_HZ[i]==hz) return i;
        throw new IllegalArgumentException();
    }
    static String label(int hz) {
        require(hz); return isExperimental(hz)?PRIVATE_LABELS[index(hz)]+"（試験）":DChannels.label(hz);
    }
    static String mhz(int hz) { return String.format(Locale.ROOT,"%.5f",require(hz)/1000000.0); }
    static String option(int hz) { return mhz(hz)+" MHz · "+label(hz); }
    static int neighbor(int hz,int direction) {
        boolean experimental=isExperimental(hz);
        int index=index(hz)+direction;
        return index<0 || index>=count(experimental)?hz:at(experimental,index);
    }
    static int parseMhz(String input) {
        String value=input==null?"":input.trim();
        if(!value.matches("[0-9]{3}(\\.[0-9]{1,6})?"))
            throw new IllegalArgumentException("MHzを半角数字で入力してください。例: 142.48125");
        try { return require(new BigDecimal(value).movePointRight(6).intValueExact()); }
        catch(ArithmeticException e) { throw new IllegalArgumentException("周波数の桁数が不正です。"); }
    }
    private RadioChannels() {}
}
