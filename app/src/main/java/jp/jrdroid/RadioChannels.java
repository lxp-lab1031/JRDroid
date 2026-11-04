package jp.jrdroid;

import java.math.BigDecimal;
import java.util.Locale;

/** JR presets plus exactly the 16 base-station settings in the supplied image.
 * The private-radio labels are not a verified assignment to individual companies. */
final class RadioChannels {
    private static final int[] PRIVATE_HZ={146493750,146481250,146506250,146518750,
        146500000,146487500,146512500,146525000,146550000,146537500,146562500,146575000,
        146818750,146806250,146831250,146843750};
    private static final String[] PRIVATE_LABELS={"K1 データ","K1-1 音声","K1-2 音声","K1-3 音声",
        "K2 データ","K2-1 音声","K2-2 音声","K2-3 音声","K3 データ","K3-1 音声","K3-2 音声","K3-3 音声",
        "北総 データ","北総-1 音声","北総-2 音声","北総-3 音声"};
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
    /** Only saved v0.3.0 mobile-station settings migrate; tuning rejects 142 MHz. */
    static int restoreFrequency(int storedHz) {
        if(contains(storedHz)) return storedHz;
        for(int baseHz:PRIVATE_HZ) if((long)storedHz+4000000L==baseHz) return baseHz;
        return DChannels.DEFAULT_HZ;
    }
    static int require(int hz) {
        if(!contains(hz)) throw new IllegalArgumentException("JRの22波または146 MHz帯の基地局16波を選んでください。");
        return hz;
    }
    static int index(int hz) {
        require(hz);
        if(!isExperimental(hz)) return DChannels.index(hz);
        for(int i=0;i<PRIVATE_HZ.length;i++) if(PRIVATE_HZ[i]==hz) return i;
        throw new IllegalArgumentException();
    }
    static String label(int hz) {
        require(hz); return isExperimental(hz)?PRIVATE_LABELS[index(hz)]+" 基地局（試験）":DChannels.label(hz);
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
            throw new IllegalArgumentException("MHzを半角数字で入力してください。例: 146.48125");
        try { return require(new BigDecimal(value).movePointRight(6).intValueExact()); }
        catch(ArithmeticException e) { throw new IllegalArgumentException("周波数の桁数が不正です。"); }
    }
    private RadioChannels() {}
}
