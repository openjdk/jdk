package sun.nio.ch.sctp;

public final class UnsupportedUtil {

    private static final String MESSAGE = "SCTP not supported on this platform";

    private UnsupportedUtil() {
    }

    static UnsupportedOperationException sctpUnsupported() {
        return new UnsupportedOperationException(MESSAGE);
    }

}
