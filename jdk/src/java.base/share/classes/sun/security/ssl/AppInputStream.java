/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package sun.security.ssl;

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLProtocolException;

/**
 * InputStream for application data as returned by SSLSocket.getInputStream().
 *
 * @author David Brownell
 */
final class AppInputStream extends InputStream {
    // the buffer size for each read of network data
    private static final int READ_BUFFER_SIZE = 4096;

    // static dummy array we use to implement skip()
    private static final byte[] SKIP_ARRAY = new byte[256];

    // the related socket of the input stream
    private final SSLSocketImpl socket;

    // the temporary buffer used to read network
    private ByteBuffer buffer;

    // Is application data available in the stream?
    private boolean appDataIsAvailable;

    // One element array used to implement the single byte read() method
    private final byte[] oneByte = new byte[1];

    AppInputStream(SSLSocketImpl conn) {
        this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.socket = conn;
        this.appDataIsAvailable = false;
    }

    /**
     * Return the minimum number of bytes that can be read without blocking.
     *
     * Currently not synchronized.
     */
    @Override
    public int available() throws IOException {
        if ((!appDataIsAvailable) || socket.checkEOF()) {
            return 0;
        }

        return buffer.remaining();
    }

    /**
     * Read a single byte, returning -1 on non-fault EOF status.
     */
    @Override
    public synchronized int read() throws IOException {
        int n = read(oneByte, 0, 1);
        if (n <= 0) { // EOF
            return -1;
        }
        return oneByte[0] & 0xFF;
    }

    /**
     * Reads up to {@code len} bytes of data from the input stream into an
     * array of bytes. An attempt is made to read as many as {@code len} bytes,
     * but a smaller number may be read. The number of bytes actually read
     * is returned as an integer.
     *
     * If the layer above needs more data, it asks for more, so we
     * are responsible only for blocking to fill at most one buffer,
     * and returning "-1" on non-fault EOF status.
     */
    @Override
    public synchronized int read(byte[] b, int off, int len)
            throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        if (socket.checkEOF()) {
            return -1;
        }

        // Read the available bytes at first.
        int remains = available();
        if (remains > 0) {
            int howmany = Math.min(remains, len);
            buffer.get(b, off, howmany);

            return howmany;
        }

        appDataIsAvailable = false;
        int volume = 0;

        try {
            /*
             * Read data if needed ... notice that the connection guarantees
             * that handshake, alert, and change cipher spec data streams are
             * handled as they arrive, so we never see them here.
             */
            while(volume == 0) {
                // Clear the buffer for a new record reading.
                buffer.clear();

                //
                // grow the buffer if needed
                //

                // Read the header of a record into the buffer, and return
                // the packet size.
                int packetLen = socket.bytesInCompletePacket();
                if (packetLen < 0) {    // EOF
                    return -1;
                }

                // Is this packet bigger than SSL/TLS normally allows?
                if (packetLen > SSLRecord.maxLargeRecordSize) {
                    throw new SSLProtocolException(
                        "Illegal packet size: " + packetLen);
                }

                if (packetLen > buffer.remaining()) {
                    buffer = ByteBuffer.allocate(packetLen);
                }

                volume = socket.readRecord(buffer);
                if (volume < 0) {    // EOF
                    return -1;
                } else if (volume > 0) {
                    appDataIsAvailable = true;
                    break;
                }
            }

            int howmany = Math.min(len, volume);
            buffer.get(b, off, howmany);
            return howmany;
        } catch (Exception e) {
            // shutdown and rethrow (wrapped) exception as appropriate
            socket.handleException(e);

            // dummy for compiler
            return -1;
        }
    }


    /**
     * Skip n bytes. This implementation is somewhat less efficient
     * than possible, but not badly so (redundant copy). We reuse
     * the read() code to keep things simpler. Note that SKIP_ARRAY
     * is static and may garbled by concurrent use, but we are not interested
     * in the data anyway.
     */
    @Override
    public synchronized long skip(long n) throws IOException {
        long skipped = 0;
        while (n > 0) {
            int len = (int)Math.min(n, SKIP_ARRAY.length);
            int r = read(SKIP_ARRAY, 0, len);
            if (r <= 0) {
                break;
            }
            n -= r;
            skipped += r;
        }
        return skipped;
    }

    /*
     * Socket close is already synchronized, no need to block here.
     */
    @Override
    public void close() throws IOException {
        socket.close();
    }

    // inherit default mark/reset behavior (throw Exceptions) from InputStream
}
