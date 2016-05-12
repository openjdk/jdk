package java.net.http;

import java.net.http.WebSocket.CloseCode;

import static java.net.http.WebSocket.CloseCode.PROTOCOL_ERROR;
import static java.util.Objects.requireNonNull;

//
// Special kind of exception closed from the outside world.
//
// Used as a "marker exception" for protocol issues in the incoming data, so the
// implementation could close the connection and specify an appropriate status
// code.
//
// A separate 'section' argument makes it more uncomfortable to be lazy and to
// leave a relevant spec reference empty :-) As a bonus all messages have the
// same style.
//
final class WSProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final CloseCode closeCode;
    private final String section;

    WSProtocolException(String section, String detail) {
        this(section, detail, PROTOCOL_ERROR);
    }

    WSProtocolException(String section, String detail, Throwable cause) {
        this(section, detail, PROTOCOL_ERROR, cause);
    }

    private WSProtocolException(String section, String detail, CloseCode code) {
        super(formatMessage(section, detail));
        this.closeCode = requireNonNull(code);
        this.section = section;
    }

    WSProtocolException(String section, String detail, CloseCode code,
                        Throwable cause) {
        super(formatMessage(section, detail), cause);
        this.closeCode = requireNonNull(code);
        this.section = section;
    }

    private static String formatMessage(String section, String detail) {
        if (requireNonNull(section).isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (requireNonNull(detail).isEmpty()) {
            throw new IllegalArgumentException();
        }
        return WSUtils.webSocketSpecViolation(section, detail);
    }

    CloseCode getCloseCode() {
        return closeCode;
    }

    public String getSection() {
        return section;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + closeCode + "]";
    }
}
