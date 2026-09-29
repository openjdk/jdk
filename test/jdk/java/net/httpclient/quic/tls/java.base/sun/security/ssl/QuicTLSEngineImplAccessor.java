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
package sun.security.ssl;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;

import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicVersion;

public final class QuicTLSEngineImplAccessor {
    // visible for testing
    public static void testDeriveOneRTTKeys(QuicVersion version,
                                            QuicTLSEngineImpl engine,
                                            SecretKey client_application_traffic_secret_0,
                                            SecretKey server_application_traffic_secret_0,
                                            String negotiatedCipherSuite,
                                            boolean clientMode)
            throws IOException, GeneralSecurityException
    {
        engine.deriveOneRTTKeys(version, client_application_traffic_secret_0,
                                server_application_traffic_secret_0,
                                CipherSuite.valueOf(negotiatedCipherSuite),
                                clientMode);
    }

    // visible for testing
    public static void completeHandshake(final QuicTLSEngineImpl engine) {
        try {
            final Field f = QuicTLSEngineImpl.class.getDeclaredField("handshakeState");
            f.setAccessible(true);
            f.set(engine, QuicTLSEngine.HandshakeState.HANDSHAKE_CONFIRMED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
