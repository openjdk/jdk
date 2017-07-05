/*
 * Copyright (c) 1996, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

/**
 * InputStream for application data as returned by SSLSocket.getInputStream().
 * It uses an InputRecord as internal buffer that is refilled on demand
 * whenever it runs out of data.
 *
 * @author David Brownell
 */
class AppInputStream extends InputStream {

    // static dummy array we use to implement skip()
    private final static byte[] SKIP_ARRAY = new byte[1024];

    private SSLSocketImpl c;
    InputRecord r;

    // One element array used to implement the single byte read() method
    private final byte[] oneByte = new byte[1];

    AppInputStream(SSLSocketImpl conn) {
        r = new InputRecord();
        c = conn;
    }

    /**
     * Return the minimum number of bytes that can be read without blocking.
     * Currently not synchronized.
     */
    public int available() throws IOException {
        if (c.checkEOF() || (r.isAppDataValid() == false)) {
            return 0;
        }
        return r.available();
    }

    /**
     * Read a single byte, returning -1 on non-fault EOF status.
     */
    public synchronized int read() throws IOException {
        int n = read(oneByte, 0, 1);
        if (n <= 0) { // EOF
            return -1;
        }
        return oneByte[0] & 0xff;
    }

    /**
     * Read up to "len" bytes into this buffer, starting at "off".
     * If the layer above needs more data, it asks for more, so we
     * are responsible only for blocking to fill at most one buffer,
     * and returning "-1" on non-fault EOF status.
     */
    public synchronized int read(byte b[], int off, int len)
            throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        if (c.checkEOF()) {
            return -1;
        }
        try {
            /*
             * Read data if needed ... notice that the connection guarantees
             * that handshake, alert, and change cipher spec data streams are
             * handled as they arrive, so we never see them here.
             */
            while (r.available() == 0) {
                c.readDataRecord(r);
                if (c.checkEOF()) {
                    return -1;
                }
            }

            int howmany = Math.min(len, r.available());
            howmany = r.read(b, off, howmany);
            return howmany;
        } catch (Exception e) {
            // shutdown and rethrow (wrapped) exception as appropriate
            c.handleException(e);
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
    public void close() throws IOException {
        c.close();
    }

    // inherit default mark/reset behavior (throw Exceptions) from InputStream

}
