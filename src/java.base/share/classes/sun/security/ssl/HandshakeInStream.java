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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLException;

/**
 * InputStream for handshake data, used internally only. Contains the
 * handshake message buffer and methods to parse them.
 *
 * Once a new handshake record arrives, it is buffered in this class until
 * processed by the Handshaker. The buffer may also contain incomplete
 * handshake messages in case the message is split across multiple records.
 * Handshaker.processRecord deals with all that. It may also contain
 * handshake messages larger than the default buffer size (e.g. large
 * certificate messages). The buffer is grown dynamically to handle that.
 *
 * Note that this class only handles Handshake messages in TLS format.
 * DTLS Handshake messages should be converted into TLS format before
 * calling into this method.
 *
 * @author David Brownell
 */

// This class is used to handle plain text handshake messages.
//
public final class HandshakeInStream extends ByteArrayInputStream {

    /*
     * Construct the stream; we'll be accumulating hashes of the
     * input records using two sets of digests.
     */
    HandshakeInStream() {
        super(new byte[0]);     // lazy to alloacte the internal buffer
    }

    //
    // overridden ByteArrayInputStream methods
    //

    @Override
    public int read(byte[] b) throws IOException {
        if (super.read(b) != b.length) {
            throw new SSLException("Unexpected end of handshake data");
        }

        return b.length;
    }

    //
    // handshake input stream management functions
    //

    /*
     * Here's an incoming record with handshake data.  Queue the contents;
     * it might be one or more entire messages, complete a message that's
     * partly queued, or both.
     */
    void incomingRecord(ByteBuffer in) throws IOException {
        int len;

        // Move any unread data to the front of the buffer.
        if (pos != 0) {
            len = count - pos;
            if (len != 0) {
                System.arraycopy(buf, pos, buf, 0, len);
            }
            pos = 0;
            count = len;
        }

        // Grow buffer if needed.
        len = in.remaining() + count;
        if (buf.length < len) {
            byte[] newbuf = new byte[len];
            if (count != 0) {
                System.arraycopy(buf, 0, newbuf, 0, count);
            }
            buf = newbuf;
        }

        // Append the incoming record to the buffer
        in.get(buf, count, in.remaining());
        count = len;
    }

    //
    // Message parsing methods
    //

    /*
     * Read 8, 16, 24, and 32 bit SSL integer data types, encoded
     * in standard big-endian form.
     */
    int getInt8() throws IOException {
        verifyLength(1);
        return read();
    }

    int getInt16() throws IOException {
        verifyLength(2);
        return (getInt8() << 8) | getInt8();
    }

    int getInt24() throws IOException {
        verifyLength(3);
        return (getInt8() << 16) | (getInt8() << 8) | getInt8();
    }

    int getInt32() throws IOException {
        verifyLength(4);
        return (getInt8() << 24) | (getInt8() << 16)
             | (getInt8() << 8) | getInt8();
    }

    /*
     * Read byte vectors with 8, 16, and 24 bit length encodings.
     */
    byte[] getBytes8() throws IOException {
        int len = getInt8();
        verifyLength(len);
        byte[] b = new byte[len];

        read(b);
        return b;
    }

    public byte[] getBytes16() throws IOException {
        int len = getInt16();
        verifyLength(len);
        byte[] b = new byte[len];

        read(b);
        return b;
    }

    byte[] getBytes24() throws IOException {
        int len = getInt24();
        verifyLength(len);
        byte[] b = new byte[len];

        read(b);
        return b;
    }

    // Is a length greater than available bytes in the record?
    private void verifyLength(int len) throws SSLException {
        if (len > available()) {
            throw new SSLException("Unexpected end of handshake data");
        }
    }
}
