/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8396112
 * @summary Reject inconsistent metadata in DTLS handshake fragments
 * @library /test/lib
 * @run main InconsistentFragmentLength
 */

import java.nio.ByteBuffer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLProtocolException;
import jdk.test.lib.Asserts;

public class InconsistentFragmentLength {

    private static final String ERROR =
            "Inconsistent DTLS handshake fragments";

    private static void uint24(ByteBuffer b, int n) {
        b.put((byte) (n >> 16));
        b.put((byte) (n >> 8));
        b.put((byte) n);
    }

    private static ByteBuffer record(int sequence, int type,
                                     int length, int offset,
                                     byte[] fragment) {
        var b = ByteBuffer.allocate(25 + fragment.length);

        // DTLS 1.2 record: handshake content type, protocol version,
        // epoch 0, 48-bit record sequence number, and record length.
        b.put((byte) 22).put((byte) 0xfe).put((byte) 0xfd);
        b.putShort((short) 0);
        b.putInt(0).putShort((short) sequence);
        b.putShort((short) (12 + fragment.length));

        // DTLS handshake: type, message length, message_seq 0,
        // fragment offset, fragment length, and fragment data.
        b.put((byte) type);
        uint24(b, length);
        b.putShort((short) 0);
        uint24(b, offset);
        uint24(b, fragment.length);
        b.put(fragment);
        return b.flip();
    }

    private static SSLEngine engine() throws Exception {
        var context = SSLContext.getInstance("DTLS");
        context.init(null, null, null);
        var engine = context.createSSLEngine("localhost", 4433);
        engine.setUseClientMode(true);
        engine.beginHandshake();
        engine.wrap(ByteBuffer.allocate(0), ByteBuffer.allocate(65536));
        return engine;
    }

    private static void testInconsistentLengths() throws Exception {
        var engine = engine();
        var app = ByteBuffer.allocate(65536);
        try {
            engine.unwrap(record(0, 0, 100, 5, new byte[50]), app);
            engine.unwrap(record(1, 0, 10, 0, new byte[5]), app);
            engine.unwrap(record(2, 0, 100, 55, new byte[45]), app);
            throw new AssertionError("Inconsistent lengths accepted");
        } catch (SSLProtocolException expected) {
            Asserts.assertEquals(expected.getMessage(), ERROR);
        }
    }

    private static void testMixedHandshakeTypes() throws Exception {
        var engine = engine();
        var app = ByteBuffer.allocate(65536);
        try {
            engine.unwrap(record(0, 2, 100, 5, new byte[50]), app);
            engine.unwrap(record(1, 0, 10, 0, new byte[5]), app);
            engine.unwrap(record(2, 0, 10, 5, new byte[5]), app);
            throw new AssertionError("Inconsistent types accepted");
        } catch (SSLProtocolException expected) {
            Asserts.assertEquals(expected.getMessage(), ERROR);
        }
    }

    private static void testCompleteReassembly() throws Exception {
        var engine = engine();
        var app = ByteBuffer.allocate(65536);

        // A valid HelloVerifyRequest: DTLS 1.2, empty cookie.
        // Deliver the three body bytes out of order.
        engine.unwrap(record(0, 3, 3, 1,
                new byte[] {(byte) 0xfd}), app);
        engine.unwrap(record(1, 3, 3, 0,
                new byte[] {(byte) 0xfe}), app);
        SSLEngineResult result = engine.unwrap(record(2, 3, 3, 2,
                new byte[] {0}), app);

        Asserts.assertEquals(result.getHandshakeStatus(),
                SSLEngineResult.HandshakeStatus.NEED_TASK);
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
        Asserts.assertEquals(engine.getHandshakeStatus(),
                SSLEngineResult.HandshakeStatus.NEED_WRAP);
    }

    public static void main(String[] args) throws Exception {
        testInconsistentLengths();
        testMixedHandshakeTypes();
        testCompleteReassembly();
    }
}
