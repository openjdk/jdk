package sun.nio.ch.sctp;

public final class Util {

    private static final String MESSAGE = "SCTP not supported on this platform";

    private Util() {
    }

    static UnsupportedOperationException sctpNotSupported() {
        return new UnsupportedOperationException(MESSAGE);
    }

}
