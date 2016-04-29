package java.net.http;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * OutputStream. Incoming window updates handled by the main connection
 * reader thread.
 */
@SuppressWarnings({"rawtypes","unchecked"})
class BodyOutputStream extends OutputStream {
    final static byte[] EMPTY_BARRAY = new byte[0];

    final int streamid;
    int window;
    boolean closed;
    boolean goodToGo = false; // not allowed to send until headers sent
    final Http2TestServerConnection conn;
    final Queue outputQ;

    BodyOutputStream(int streamid, int initialWindow, Http2TestServerConnection conn) {
        this.window = initialWindow;
        this.streamid = streamid;
        this.conn = conn;
        this.outputQ = conn.outputQ;
        conn.registerStreamWindowUpdater(streamid, this::updateWindow);
    }

    // called from connection reader thread as all incoming window
    // updates are handled there.
    synchronized void updateWindow(int update) {
        window += update;
        notifyAll();
    }

    void waitForWindow(int demand) throws InterruptedException {
        // first wait for the connection window
        conn.obtainConnectionWindow(demand);
        // now wait for the stream window
        synchronized (this) {
            while (demand > 0) {
                int n = Math.min(demand, window);
                demand -= n;
                window -= n;
                if (demand > 0) {
                    wait();
                }
            }
        }
    }

    void goodToGo() {
        goodToGo = true;
    }

    @Override
    public void write(byte[] buf, int offset, int len) throws IOException {
        if (closed) {
            throw new IOException("closed");
        }

        if (!goodToGo) {
            throw new IllegalStateException("sendResponseHeaders must be called first");
        }
        try {
            waitForWindow(len);
            send(buf, offset, len, 0);
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
    }

    private void send(byte[] buf, int offset, int len, int flags) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(buf, offset, len);
        buffer.flip();
        DataFrame df = new DataFrame();
        assert streamid != 0;
        df.streamid(streamid);
        df.setFlags(flags);
        df.setData(buffer);
        outputQ.put(df);
    }

    byte[] one = new byte[1];

    @Override
    public void write(int b) throws IOException {
        one[0] = (byte) b;
        write(one, 0, 1);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            send(EMPTY_BARRAY, 0, 0, DataFrame.END_STREAM);
        } catch (IOException ex) {
            System.err.println("TestServer: OutputStream.close exception: " + ex);
            ex.printStackTrace();
        }
    }
}
