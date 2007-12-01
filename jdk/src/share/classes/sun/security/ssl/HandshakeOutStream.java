/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package sun.security.ssl;

import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * Output stream for handshake data.  This is used only internally
 * to the SSL classes.
 *
 * MT note:  one thread at a time is presumed be writing handshake
 * messages, but (after initial connection setup) it's possible to
 * have other threads reading/writing application data.  It's the
 * SSLSocketImpl class that synchronizes record writes.
 *
 * @author  David Brownell
 */
class HandshakeOutStream extends OutputStream {

    private SSLSocketImpl socket;
    private SSLEngineImpl engine;

    OutputRecord r;

    HandshakeOutStream(ProtocolVersion protocolVersion,
            ProtocolVersion helloVersion, HandshakeHash handshakeHash,
            SSLSocketImpl socket) {
        this.socket = socket;
        r = new OutputRecord(Record.ct_handshake);
        init(protocolVersion, helloVersion, handshakeHash);
    }

    HandshakeOutStream(ProtocolVersion protocolVersion,
            ProtocolVersion helloVersion, HandshakeHash handshakeHash,
            SSLEngineImpl engine) {
        this.engine = engine;
        r = new EngineOutputRecord(Record.ct_handshake, engine);
        init(protocolVersion, helloVersion, handshakeHash);
    }

    private void init(ProtocolVersion protocolVersion,
            ProtocolVersion helloVersion, HandshakeHash handshakeHash) {
        r.setVersion(protocolVersion);
        r.setHelloVersion(helloVersion);
        r.setHandshakeHash(handshakeHash);
    }


    /*
     * Update the handshake data hashes ... mostly for use after a
     * client cert has been sent, so the cert verify message can be
     * constructed correctly yet without forcing extra I/O.  In all
     * other cases, automatic hash calculation suffices.
     */
    void doHashes() {
        r.doHashes();
    }

    /*
     * Write some data out onto the stream ... buffers as much as possible.
     * Hashes are updated automatically if something gets flushed to the
     * network (e.g. a big cert message etc).
     */
    public void write(byte buf[], int off, int len) throws IOException {
        while (len > 0) {
            int howmuch = Math.min(len, r.availableDataBytes());

            if (howmuch == 0) {
                flush();
            } else {
                r.write(buf, off, howmuch);
                off += howmuch;
                len -= howmuch;
            }
        }
    }

    /*
     * write-a-byte
     */
    public void write(int i) throws IOException {
        if (r.availableDataBytes() < 1) {
            flush();
        }
        r.write(i);
    }

    public void flush() throws IOException {
        if (socket != null) {
            try {
                socket.writeRecord(r);
            } catch (IOException e) {
                // Had problems writing; check if there was an
                // alert from peer. If alert received, waitForClose
                // will throw an exception for the alert
                socket.waitForClose(true);

                // No alert was received, just rethrow exception
                throw e;
            }
        } else {  // engine != null
            /*
             * Even if record might be empty, flush anyway in case
             * there is a finished handshake message that we need
             * to queue.
             */
            engine.writeRecord((EngineOutputRecord)r);
        }
    }

    /*
     * Tell the OutputRecord that a finished message was
     * contained either in this record or the one immeiately
     * preceeding it.  We need to reliably pass back notifications
     * that a finish message occured.
     */
    void setFinishedMsg() {
        assert(socket == null);

        ((EngineOutputRecord)r).setFinishedMsg();
    }

    /*
     * Put integers encoded in standard 8, 16, 24, and 32 bit
     * big endian formats. Note that OutputStream.write(int) only
     * writes the least significant 8 bits and ignores the rest.
     */

    void putInt8(int i) throws IOException {
        r.write(i);
    }

    void putInt16(int i) throws IOException {
        if (r.availableDataBytes() < 2) {
            flush();
        }
        r.write(i >> 8);
        r.write(i);
    }

    void putInt24(int i) throws IOException {
        if (r.availableDataBytes() < 3) {
            flush();
        }
        r.write(i >> 16);
        r.write(i >> 8);
        r.write(i);
    }

    void putInt32(int i) throws IOException {
        if (r.availableDataBytes() < 4) {
            flush();
        }
        r.write(i >> 24);
        r.write(i >> 16);
        r.write(i >> 8);
        r.write(i);
    }

    /*
     * Put byte arrays with length encoded as 8, 16, 24 bit
     * integers in big-endian format.
     */
    void putBytes8(byte b[]) throws IOException {
        if (b == null) {
            putInt8(0);
            return;
        }
        putInt8(b.length);
        write(b, 0, b.length);
    }

    void putBytes16(byte b[]) throws IOException {
        if (b == null) {
            putInt16(0);
            return;
        }
        putInt16(b.length);
        write(b, 0, b.length);
    }

    void putBytes24(byte b[]) throws IOException {
        if (b == null) {
            putInt24(0);
            return;
        }
        putInt24(b.length);
        write(b, 0, b.length);
    }
}
