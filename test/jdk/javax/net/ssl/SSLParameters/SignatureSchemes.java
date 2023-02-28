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
 * @bug 8280494
 * @summary (D)TLS signature schemes
 * @library /javax/net/ssl/templates
 * @run main/othervm SignatureSchemes
 */

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.security.Security;

public class SignatureSchemes extends SSLSocketTemplate {
    private final String[] serverSignatureSchemes;
    private final String[] clientSignatureSchemes;
    private final boolean exceptionExpected;

    public SignatureSchemes(String[] serverSignatureSchemes,
                            String[] clientSignatureSchemes,
                            boolean exceptionExpected) {
        this.serverSignatureSchemes = serverSignatureSchemes;
        this.clientSignatureSchemes = clientSignatureSchemes;
        this.exceptionExpected = exceptionExpected;
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setSignatureSchemes(serverSignatureSchemes);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslParameters = socket.getSSLParameters();
        sslParameters.setSignatureSchemes(clientSignatureSchemes);
        socket.setSSLParameters(sslParameters);
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
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
                        "ecdsa_secp256r1_sha256",
                        "ed25519"
                },
                new String[] {
                        "ecdsa_secp256r1_sha256",
                        "ed25519"
                },
                false);
        runTest(new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                false);
        runTest(null,
                new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                false);
        runTest(new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                null,
                false);
        runTest(new String[0],
                new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                true);
        runTest(new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                new String[0],
                true);
        runTest(new String[] {
                        "ecdsa_secp256r1_shaNA"
                },
                new String[] {
                        "ecdsa_secp256r1_sha256"
                },
                true);
    }

    private static void runTest(String[] serverSignatureSchemes,
                                String[] clientSignatureSchemes,
                                boolean exceptionExpected) throws Exception {
        new SignatureSchemes(serverSignatureSchemes,
                clientSignatureSchemes, exceptionExpected).run();
    }
}
