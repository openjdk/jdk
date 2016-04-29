package java.net.http;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * InputStream reads frames off stream q and supplies read demand from any
 * DataFrames it finds. Window updates are sent back on the connections send
 * q.
 */
class BodyInputStream extends InputStream {

    final Queue<Http2Frame> q;
    final int streamid;
    boolean closed;
    boolean eof;
    final Http2TestServerConnection conn;

    @SuppressWarnings({"rawtypes","unchecked"})
    BodyInputStream(Queue q, int streamid, Http2TestServerConnection conn) {
        this.q = q;
        this.streamid = streamid;
        this.conn = conn;
    }

    DataFrame df;
    ByteBuffer[] buffers;
    ByteBuffer buffer;
    int nextIndex = -1;

    private DataFrame getData() throws IOException {
        if (eof) {
            return null;
        }
        Http2Frame frame;
        do {
            frame = q.take();
            if (frame.type() == ResetFrame.TYPE) {
                conn.handleStreamReset((ResetFrame) frame); // throws IOException
            }
            // ignoring others for now Wupdates handled elsewhere
            if (frame.type() != DataFrame.TYPE) {
                System.out.println("Ignoring " + frame.toString() + " CHECK THIS");
            }
        } while (frame.type() != DataFrame.TYPE);
        df = (DataFrame) frame;
        int len = df.getDataLength();
        eof = frame.getFlag(DataFrame.END_STREAM);
        // acknowledge
        conn.sendWindowUpdates(len, streamid);
        return (DataFrame) frame;
    }

    // null return means EOF
    private ByteBuffer getBuffer() throws IOException {
        if (buffer == null || !buffer.hasRemaining()) {
            if (nextIndex == -1 || nextIndex == buffers.length) {
                DataFrame df = getData();
                if (df == null) {
                    return null;
                }
                int len = df.getDataLength();
                if ((len == 0) && eof) {
                    return null;
                }
                buffers = df.getData();
                nextIndex = 0;
            }
            buffer = buffers[nextIndex++];
        }
        return buffer;
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }
        ByteBuffer b = getBuffer();
        if (b == null) {
            return -1;
        }
        int remaining = b.remaining();
        if (remaining < length) {
            length = remaining;
        }
        b.get(buf, offset, length);
        return length;
    }

    byte[] one = new byte[1];

    @Override
    public int read() throws IOException {
        int c = read(one, 0, 1);
        if (c == -1) {
            return -1;
        }
        return one[0];
    }

    @Override
    public void close() {
        // TODO reset this stream
        closed = true;
    }
}
