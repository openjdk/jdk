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

import java.io.InputStream;
import java.io.IOException;

import javax.net.ssl.SSLException;

/**
 * InputStream for handshake data, used internally only. Contains the
 * handshake message buffer and methods to parse them.
 *
 * Once a new handshake record arrives, it is buffered in this class until
 * processed by the Handshaker. The buffer may also contain incomplete
 * handshake messages in case the message is split across multiple records.
 * Handshaker.process_record deals with all that. It may also contain
 * handshake messages larger than the default buffer size (e.g. large
 * certificate messages). The buffer is grown dynamically to handle that
 * (see InputRecord.queueHandshake()).
 *
 * Note that the InputRecord used as a buffer here is separate from the
 * AppInStream.r, which is where data from the socket is initially read
 * into. This is because once the initial handshake has been completed,
 * handshake and application data messages may be interleaved arbitrarily
 * and must be processed independently.
 *
 * @author David Brownell
 */
public class HandshakeInStream extends InputStream {

    InputRecord r;

    /*
     * Construct the stream; we'll be accumulating hashes of the
     * input records using two sets of digests.
     */
    HandshakeInStream(HandshakeHash handshakeHash) {
        r = new InputRecord();
        r.setHandshakeHash(handshakeHash);
    }


    // overridden InputStream methods

    /*
     * Return the number of bytes available for read().
     *
     * Note that this returns the bytes remaining in the buffer, not
     * the bytes remaining in the current handshake message.
     */
    @Override
    public int available() {
        return r.available();
    }

    /*
     * Get a byte of handshake data.
     */
    @Override
    public int read() throws IOException {
        int n = r.read();
        if (n == -1) {
            throw new SSLException("Unexpected end of handshake data");
        }
        return n;
    }

    /*
     * Get a bunch of bytes of handshake data.
     */
    @Override
    public int read(byte b [], int off, int len) throws IOException {
        // we read from a ByteArrayInputStream, it always returns the
        // data in a single read if enough is available
        int n = r.read(b, off, len);
        if (n != len) {
            throw new SSLException("Unexpected end of handshake data");
        }
        return n;
    }

    /*
     * Skip some handshake data.
     */
    @Override
    public long skip(long n) throws IOException {
        return r.skip(n);
    }

    /*
     * Mark/ reset code, implemented using InputRecord mark/ reset.
     *
     * Note that it currently provides only a limited mark functionality
     * and should be used with care (once a new handshake record has been
     * read, data that has already been consumed is lost even if marked).
     */

    @Override
    public void mark(int readlimit) {
        r.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        r.reset();
    }

    @Override
    public boolean markSupported() {
        return true;
    }


    // handshake management functions

    /*
     * Here's an incoming record with handshake data.  Queue the contents;
     * it might be one or more entire messages, complete a message that's
     * partly queued, or both.
     */
    void incomingRecord(InputRecord in) throws IOException {
        r.queueHandshake(in);
    }

    /*
     * Hash any data we've consumed but not yet hashed.  Useful mostly
     * for processing client certificate messages (so we can check the
     * immediately following cert verify message) and finished messages
     * (so we can compute our own finished message).
     */
    void digestNow() {
        r.doHashes();
    }

    /*
     * Do more than skip that handshake data ... totally ignore it.
     * The difference is that the data does not get hashed.
     */
    void ignore(int n) {
        r.ignore(n);
    }


    // Message parsing methods

    /*
     * Read 8, 16, 24, and 32 bit SSL integer data types, encoded
     * in standard big-endian form.
     */

    int getInt8() throws IOException {
        return read();
    }

    int getInt16() throws IOException {
        return (getInt8() << 8) | getInt8();
    }

    int getInt24() throws IOException {
        return (getInt8() << 16) | (getInt8() << 8) | getInt8();
    }

    int getInt32() throws IOException {
        return (getInt8() << 24) | (getInt8() << 16)
             | (getInt8() << 8) | getInt8();
    }

    /*
     * Read byte vectors with 8, 16, and 24 bit length encodings.
     */

    byte[] getBytes8() throws IOException {
        int len = getInt8();
        verifyLength(len);
        byte b[] = new byte[len];

        read(b, 0, len);
        return b;
    }

    public byte[] getBytes16() throws IOException {
        int len = getInt16();
        verifyLength(len);
        byte b[] = new byte[len];

        read(b, 0, len);
        return b;
    }

    byte[] getBytes24() throws IOException {
        int len = getInt24();
        verifyLength(len);
        byte b[] = new byte[len];

        read(b, 0, len);
        return b;
    }

    // Is a length greater than available bytes in the record?
    private void verifyLength(int len) throws SSLException {
        if (len > available()) {
            throw new SSLException(
                        "Not enough data to fill declared vector size");
        }
    }

}
