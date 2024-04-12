/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
 * @test
 * @bug 8293176
 * @summary SSLEngine should close after bad key share
 * @run main SSLEngineDecodeBadPoint
 */
public class SSLEngineDecodeBadPoint {
    static final byte[] clientHello = HexFormat.of().parseHex(
            "160303013a0100013603031570" +
            "151d6066940aa5dfcecc99f470bfdc175eec2c6f3273b079b2f80b49" +
            "75c820efe3d307201492a49fcee79fac5b2f05dca26c572b65b0d90d" +
            "81f51fd26b49b700021302010000eb000500050100000000000a0016" +
            "0014001d001700180019001e01000101010201030104000d00220020" +
            "040305030603080708080804080508060809080a080b040105010601" +
            "02030201002b0003020304002d000201010032002200200403050306" +
            "03080708080804080508060809080a080b0401050106010203020100" +
            "33006b0069001d00209a4d13131f83cc4c5be46520f0b4d7a6f1d3f6" +
            "ca7118e6dd115125090da6e044" +
            // ECDHE key share, 5th byte changed from 04 to (invalid) 05
            "0017004105d8c3734b9c729f6a9851" +
            "5049543ec5a9bb6c19b8c02ca0bdc3b20a77c44acdab226b6329b7c5" +
            "db9204421932c6fa1abe614c6892f5289edf9ff43cac534cad9e");

    public static void main(String[] args) throws NoSuchAlgorithmException, SSLException {
        SSLContext ctx = SSLContext.getDefault();
        SSLEngine eng = ctx.createSSLEngine();
        eng.setUseClientMode(false);
        eng.beginHandshake();
        ByteBuffer hello = ByteBuffer.wrap(clientHello);
        ByteBuffer emptyBuf = ByteBuffer.allocate(0);
        SSLEngineResult res = eng.unwrap(hello, emptyBuf);
        System.out.println("status after unwrap: " + res);
        eng.getDelegatedTask().run();

        SSLEngineResult.HandshakeStatus status = eng.getHandshakeStatus();
        System.out.println("status after task: " + status);
        if (status != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            throw new RuntimeException("Unexpected status after task: " + status);
        }
        ByteBuffer alert = ByteBuffer.allocate(eng.getSession().getPacketBufferSize());

        try {
            eng.wrap(emptyBuf, alert);
            throw new RuntimeException("Expected wrap to throw");
        } catch (SSLException e) {
            System.err.println("Received expected SSLException:");
            e.printStackTrace();
        }
        if (alert.position() != 0) {
            throw new RuntimeException("Expected no bytes written in first wrap call");
        }
        res = eng.wrap(emptyBuf, alert);
        System.out.println("status after wrap: " + res);
        if (res.getStatus() != SSLEngineResult.Status.CLOSED ||
                res.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            throw new RuntimeException("Unexpected status after wrap: " + res);
        }
        if (!eng.isOutboundDone()) {
            throw new RuntimeException("Expected outbound done");
        }
    }
}
