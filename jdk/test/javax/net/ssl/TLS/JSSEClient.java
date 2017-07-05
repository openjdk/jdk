/**
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA or
 * visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

class JSSEClient extends CipherTestUtils.Client {

    private static final String DEFAULT = "DEFAULT";
    private static final String TLS = "TLS";

    private final SSLContext sslContext;
    private final MyX509KeyManager keyManager;
    private final int serverPort;
    private final String serverHost;
    private final String testedProtocol;

    JSSEClient(CipherTestUtils cipherTest, String serverHost, int serverPort,
            String testedProtocols, String testedCipherSuite) throws Exception {
        super(cipherTest, testedCipherSuite);
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.testedProtocol = testedProtocols;
        this.keyManager =
                new MyX509KeyManager(cipherTest.getClientKeyManager());
        sslContext = SSLContext.getInstance(TLS);
    }

    @Override
    void runTest(CipherTestUtils.TestParameters params) throws Exception {
        SSLSocket socket = null;
        try {
            System.out.println("Connecting to server...");
            keyManager.setAuthType(params.clientAuth);
            sslContext.init(new KeyManager[]{keyManager},
                    new TrustManager[]{cipherTest.getClientTrustManager()},
                    CipherTestUtils.secureRandom);
            SSLSocketFactory factory = (SSLSocketFactory) sslContext.
                    getSocketFactory();
            socket = (SSLSocket) factory.createSocket(serverHost,
                    serverPort);
            socket.setSoTimeout(CipherTestUtils.TIMEOUT);
            socket.setEnabledCipherSuites(params.cipherSuite.split(","));
            if (params.protocol != null && !params.protocol.trim().equals("")
                    && !params.protocol.trim().equals(DEFAULT)) {
                socket.setEnabledProtocols(params.protocol.split(","));
            }
            CipherTestUtils.printInfo(socket);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            sendRequest(in, out);
            SSLSession session = socket.getSession();
            session.invalidate();
            String cipherSuite = session.getCipherSuite();
            if (params.cipherSuite.equals(cipherSuite) == false) {
                throw new RuntimeException("Negotiated ciphersuite mismatch: "
                        + cipherSuite + " != " + params.cipherSuite);
            }
            String protocol = session.getProtocol();
            if (!DEFAULT.equals(params.protocol)
                    && !params.protocol.contains(protocol)) {
                throw new RuntimeException("Negotiated protocol mismatch: "
                        + protocol + " != " + params.protocol);
            }
            if (!cipherSuite.contains("DH_anon")) {
                session.getPeerCertificates();
            }
            Certificate[] certificates = session.getLocalCertificates();
            if (params.clientAuth == null) {
                if (certificates != null) {
                    throw new RuntimeException("Local certificates "
                            + "should be null");
                }
            } else {
                if ((certificates == null) || (certificates.length == 0)) {
                    throw new RuntimeException("Certificates missing");
                }
                String keyAlg = certificates[0].getPublicKey().getAlgorithm();
                if ("EC".equals(keyAlg)) {
                    keyAlg = "ECDSA";
                }
                if (params.clientAuth == null ? keyAlg != null
                        : !params.clientAuth.equals(keyAlg)) {
                    throw new RuntimeException("Certificate type mismatch: "
                            + keyAlg + " != " + params.clientAuth);
                }
            }
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
