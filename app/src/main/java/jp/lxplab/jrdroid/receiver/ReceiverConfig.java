package jp.lxplab.jrdroid.receiver;

/** Hardware-independent configuration. Frequency is always in Hz. */
public final class ReceiverConfig {
    public static final long DEFAULT_FREQUENCY_HZ = 352_625_000L;
    private final long frequencyHz;

    public ReceiverConfig(long frequencyHz) {
        if (frequencyHz <= 0) {
            throw new IllegalArgumentException("frequencyHz must be positive");
        }
        this.frequencyHz = frequencyHz;
    }

    public long getFrequencyHz() {
        return frequencyHz;
    }
}
