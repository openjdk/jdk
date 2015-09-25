/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/*
 * OutputStream for application data as returned by SSLSocket.getOutputStream().
 *
 * @author  David Brownell
 */
class AppOutputStream extends OutputStream {

    private SSLSocketImpl socket;

    // One element array used to implement the write(byte) method
    private final byte[] oneByte = new byte[1];

    AppOutputStream(SSLSocketImpl conn) {
        this.socket = conn;
    }

    /**
     * Write the data out, NOW.
     */
    @Override
    public synchronized void write(byte[] b, int off, int len)
            throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        // check if the Socket is invalid (error or closed)
        socket.checkWrite();

        // Delegate the writing to the underlying socket.
        try {
            socket.writeRecord(b, off, len);
            socket.checkWrite();
        } catch (Exception e) {
            // shutdown and rethrow (wrapped) exception as appropriate
            socket.handleException(e);
        }
    }

    /**
     * Write one byte now.
     */
    @Override
    public synchronized void write(int i) throws IOException {
        oneByte[0] = (byte)i;
        write(oneByte, 0, 1);
    }

    /*
     * Socket close is already synchronized, no need to block here.
     */
    @Override
    public void close() throws IOException {
        socket.close();
    }

    // inherit no-op flush()
}
