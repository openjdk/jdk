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
 * @summary Check TLS connection behaviors for named groups configuration
 * @library /javax/net/ssl/templates
 * @run main/othervm NamedGroups
 */

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.security.Security;

public class NamedGroups extends SSLSocketTemplate {
    private final String[] serverNamedGroups;
    private final String[] clientNamedGroups;
    private final boolean exceptionExpected;

    public NamedGroups(String[] serverNamedGroups,
                            String[] clientNamedGroups,
                            boolean exceptionExpected) {
        this.serverNamedGroups = serverNamedGroups;
        this.clientNamedGroups = clientNamedGroups;
        this.exceptionExpected = exceptionExpected;
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setNamedGroups(serverNamedGroups);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslParameters = socket.getSSLParameters();
        sslParameters.setNamedGroups(clientNamedGroups);
        socket.setSSLParameters(sslParameters);
    }

    @Override
    protected void runServerApplication(SSLSocket socket) {
        try {
            super.runServerApplication(socket);
        } catch (Exception ex) {
            // Just ignore, let the client handle the failure information.
        }
    }

    @Override
    protected void runClientApplication(SSLSocket sslSocket) throws Exception {
        try {
            super.runClientApplication(sslSocket);
        } catch (Exception ex) {
            if (!exceptionExpected) {
                throw ex;
            } else {  // Otherwise, swallow the exception and return.
                return;
            }
        }

        if (exceptionExpected) {
            throw new RuntimeException("Unexpected success!");
        }
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
        new NamedGroups(serverNamedGroups,
                clientNamedGroups, exceptionExpected).run();
    }
}
