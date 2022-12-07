/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8281236
 * @summary Check DTLS connection behaviors for named groups configuration
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build DTLSOverDatagram
 * @run main/othervm DTLSNamedGroups
 */

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.security.Security;

/**
 * Test DTLS client authentication.
 */
public class DTLSNamedGroups extends DTLSOverDatagram {
    // Make sure default DH(E) key exchange is not used for DTLS v1.2.
    private static String[] cipherSuites = new String[] {
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
    };

    private final String[] serverNamedGroups;
    private final String[] clientNamedGroups;

    public DTLSNamedGroups(String[] serverNamedGroups,
                           String[] clientNamedGroups) {
        this.serverNamedGroups = serverNamedGroups;
        this.clientNamedGroups = clientNamedGroups;
    }

    @Override
    SSLEngine createSSLEngine(boolean isClient) throws Exception {
        SSLEngine engine = super.createSSLEngine(isClient);

        SSLParameters sslParameters = engine.getSSLParameters();
        if (isClient) {
            sslParameters.setNamedGroups(clientNamedGroups);
            sslParameters.setCipherSuites(cipherSuites);
        } else {
            sslParameters.setNamedGroups(serverNamedGroups);
        }
        engine.setSSLParameters(sslParameters);

        return engine;
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        runTest(new String[] {
                        "x25519",
                        "secp256r1"
                },
                new String[] {
                        "x25519",
                        "secp256r1"
                },
                false);
        runTest(new String[] {
                        "secp256r1"
                },
                new String[] {
                        "secp256r1"
                },
                false);
        runTest(null,
                new String[] {
                        "secp256r1"
                },
                false);
        runTest(new String[] {
                        "secp256r1"
                },
                null,
                false);
        runTest(new String[0],
                new String[] {
                        "secp256r1"
                },
                true);
        runTest(new String[] {
                        "secp256r1"
                },
                new String[0],
                true);
        runTest(new String[] {
                        "secp256NA"
                },
                new String[] {
                        "secp256r1"
                },
                true);
    }

    private static void runTest(String[] serverNamedGroups,
                                String[] clientNamedGroups,
                                boolean exceptionExpected) throws Exception {
        DTLSNamedGroups testCase = new DTLSNamedGroups(
                serverNamedGroups, clientNamedGroups);
        try {
            testCase.runTest(testCase);
        } catch (Exception e) {
            if (!exceptionExpected) {
                throw e;
            } else { // Otherwise, swallow the expected exception and return.
                return;
            }
        }

        if (exceptionExpected) {
            throw new RuntimeException("Unexpected success!");
        }
    }
}

