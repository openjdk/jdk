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
 * @summary Reject inconsistent lengths in DTLS handshake fragments
 * @run main InconsistentFragmentLength
 */

import javax.net.ssl.*;
import java.nio.ByteBuffer;

public class InconsistentFragmentLength {

    static void uint24(ByteBuffer b, int n) {
        b.put((byte)(n >> 16)).put((byte)(n >> 8)).put((byte)n);
    }

    static ByteBuffer record(int sequence, int length, int offset, int size) {
        var b = ByteBuffer.allocate(25 + size);

        // DTLS 1.2 handshake record in epoch 0, with a 48-bit record
        // sequence number and a payload containing one handshake fragment.
        b.put((byte)22).put((byte)0xfe).put((byte)0xfd).putShort((short)0);
        b.putInt(0).putShort((short)sequence).putShort((short)(12 + size));

        // HelloRequest fragment: total message length, message sequence 0,
        // fragment offset, fragment length, and zero-filled payload.
        b.put((byte)0);
        uint24(b, length);
        b.putShort((short)0);
        uint24(b, offset);
        uint24(b, size);
        b.put(new byte[size]);

        return b.flip();
    }

    static SSLEngine engine() throws Exception {
        var context = SSLContext.getInstance("DTLS");
        context.init(null, null, null);
        var engine = context.createSSLEngine("localhost", 4433);
        engine.setUseClientMode(true);
        engine.beginHandshake();
        engine.wrap(ByteBuffer.allocate(0), ByteBuffer.allocate(65536));
        return engine;
    }

    public static void main(String[] args) throws Exception {
        var engine = engine();
        var app = ByteBuffer.allocate(65536);
        engine.unwrap(record(0, 100, 5, 50), app);
        try {
            engine.unwrap(record(1, 10, 0, 5), app);
            engine.unwrap(record(2, 100, 55, 45), app);
            throw new AssertionError("Inconsistent length accepted");
        } catch (SSLException expected) {
            if (!expected.getMessage().contains("Inconsistent DTLS handshake fragments")) throw expected;
            for (Throwable t = expected; t != null; t = t.getCause()) {
                if (t instanceof IndexOutOfBoundsException) throw new AssertionError(t);
            }
        }
        // Consistent fragmented input must pass the same reassembly path.
        engine = engine();
        engine.unwrap(record(0, 100, 5, 50), app);
        engine.unwrap(record(1, 100, 0, 5), app);
    }
}
