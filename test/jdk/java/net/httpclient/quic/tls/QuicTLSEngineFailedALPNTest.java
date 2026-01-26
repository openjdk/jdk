/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @library /test/lib
 * @modules java.base/sun.security.ssl
 *          java.base/jdk.internal.net.quic
 * @build jdk.test.lib.net.SimpleSSLContext
 * @summary Verify that a missing ALPN extension results in no_application_protocol alert
 * @run junit/othervm QuicTLSEngineFailedALPNTest
 */
public class QuicTLSEngineFailedALPNTest {

    @Test
    void testServerRequiresALPN() throws IOException {
        SSLContext ctx = SimpleSSLContext.findSSLContext("TLSv1.3");
        QuicTLSContext qctx = new QuicTLSContext(ctx);
        QuicTLSEngine clientEngine = createClientEngine(qctx);
        QuicTLSEngine serverEngine = createServerEngine(qctx);
        ByteBuffer cTOs = clientEngine.getHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL);
        try {
            serverEngine.consumeHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL, cTOs);
            fail("Expected exception not thrown");
        } catch (QuicTransportException e) {
            assertEquals(0x0178, e.getErrorCode(), "Unexpected error code");
        }
    }

    @Test
    void testClientRequiresALPN() throws IOException, QuicTransportException {
        SSLContext ctx = SimpleSSLContext.findSSLContext("TLSv1.3");
        QuicTLSContext qctx = new QuicTLSContext(ctx);
        QuicTLSEngine clientEngine = createClientEngine(qctx);
        QuicTLSEngine serverEngine = createServerEngine(qctx);
        SSLParameters params = clientEngine.getSSLParameters();
        params.setApplicationProtocols(new String[] { "test" });
        clientEngine.setSSLParameters(params);
        // client hello
        ByteBuffer cTOs = clientEngine.getHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL);
        serverEngine.consumeHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL, cTOs);
        assertFalse(cTOs.hasRemaining());
        // server hello
        ByteBuffer sTOc = serverEngine.getHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL);
        clientEngine.consumeHandshakeBytes(QuicTLSEngine.KeySpace.INITIAL, sTOc);
        assertFalse(sTOc.hasRemaining());
        // encrypted extensions
        sTOc = serverEngine.getHandshakeBytes(QuicTLSEngine.KeySpace.HANDSHAKE);
        try {
            clientEngine.consumeHandshakeBytes(QuicTLSEngine.KeySpace.HANDSHAKE, sTOc);
            fail("Expected exception not thrown");
        } catch (QuicTransportException e) {
            assertEquals(0x0178, e.getErrorCode(), "Unexpected error code");
        }

    }

    private static QuicTLSEngine createServerEngine(QuicTLSContext qctx) {
        QuicTLSEngine engine = qctx.createEngine();
        engine.setUseClientMode(false);
        engine.setLocalQuicTransportParameters(ByteBuffer.allocate(0));
        engine.setRemoteQuicTransportParametersConsumer(params-> { });
        engine.versionNegotiated(QuicVersion.QUIC_V1);
        return engine;
    }

    private static QuicTLSEngine createClientEngine(QuicTLSContext qctx) {
        QuicTLSEngine engine = qctx.createEngine("localhost", 1234);
        engine.setUseClientMode(true);
        engine.setLocalQuicTransportParameters(ByteBuffer.allocate(0));
        engine.setRemoteQuicTransportParametersConsumer(params-> { });
        engine.versionNegotiated(QuicVersion.QUIC_V1);
        return engine;
    }

}
