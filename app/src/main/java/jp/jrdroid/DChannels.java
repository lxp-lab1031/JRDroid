package jp.jrdroid;

import java.math.BigDecimal;
import java.util.Locale;

/** JR East standard D-type downlink assignments from the vendored presets.
 * Route-specific exceptions exist; the displayed MHz is the selected frequency. */
final class DChannels {
    static final int MIN_HZ = 352537500, MAX_HZ = 352668750, STEP_HZ = 6250;
    static final int DEFAULT_HZ = 352625000;
    private static final String[] LABELS = {
        "D5 音声第1波", "D7 音声第1波", "D3 音声第1波", "D1 音声第1波",
        "D4 音声第1波", "D6 音声第1波", "D2 音声第1波", "D6 音声第2波",
        "D5 音声第2波", "D2 音声第2波", "D4 音声第2波", "D7 音声第2波",
        "D3 音声第2波", "D1 音声第2波", "旅客一斉", "D1 データ波",
        "D2 データ波", "D3 データ波", "D4 データ波", "D5 データ波",
        "D6 データ波", "D7 データ波"
    };
    static int count() { return LABELS.length; }
    static boolean contains(int hz) {
        return hz >= MIN_HZ && hz <= MAX_HZ && (hz-MIN_HZ)%STEP_HZ == 0;
    }
    static int require(int hz) {
        if (!contains(hz)) throw new IllegalArgumentException("352.53750〜352.66875 MHz を 6.25 kHz 刻みで指定してください。");
        return hz;
    }
    static int at(int index) {
        if (index < 0 || index >= count()) throw new IllegalArgumentException("チャンネル番号が不正です");
        return MIN_HZ+index*STEP_HZ;
    }
    static int index(int hz) { return (require(hz)-MIN_HZ)/STEP_HZ; }
    static String label(int hz) { return LABELS[index(hz)]; }
    static String mhz(int hz) { return String.format(Locale.ROOT,"%.5f",require(hz)/1000000.0); }
    static String option(int hz) { return mhz(hz)+" MHz  ·  "+label(hz); }
    static int parseMhz(String text) {
        String s = text == null ? "" : text.trim();
        if (!s.matches("[0-9]{3}(\\.[0-9]{1,6})?"))
            throw new IllegalArgumentException("MHz を半角数字で入力してください。例: 352.55625");
        try { return require(new BigDecimal(s).movePointRight(6).intValueExact()); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("周波数の桁数が不正です。"); }
    }
    private DChannels() {}
}
