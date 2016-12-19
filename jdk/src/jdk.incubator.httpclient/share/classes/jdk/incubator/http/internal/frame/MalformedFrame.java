package jdk.incubator.http.internal.frame;

public class MalformedFrame extends Http2Frame {

    private int errorCode;
    // if errorStream == 0 means Connection Error; RFC 7540 5.4.1
    // if errorStream != 0 means Stream Error; RFC 7540 5.4.2
    private int errorStream;
    private String msg;

    /**
     * Creates Connection Error malformed frame
     * @param errorCode - error code, as specified by RFC 7540
     * @param msg - internal debug message
     */
    public MalformedFrame(int errorCode, String msg) {
        this(errorCode, 0 , msg);
    }

    /**
     * Creates Stream Error malformed frame
     * @param errorCode - error code, as specified by RFC 7540
     * @param errorStream - id of error stream (RST_FRAME will be send for this stream)
     * @param msg - internal debug message
     */
    public MalformedFrame(int errorCode, int errorStream, String msg) {
        super(0, 0);
        this.errorCode = errorCode;
        this.errorStream = errorStream;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return super.toString() + " MalformedFrame, Error: " + ErrorFrame.stringForCode(errorCode)
                + " streamid: " + streamid + " reason: " + msg;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return msg;
    }
}
