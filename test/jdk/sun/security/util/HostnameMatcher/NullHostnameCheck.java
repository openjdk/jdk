/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/*
 * @test
 * @bug 8211339
 * @summary Verify hostname returns an exception instead of null pointer when
 * creating a new engine
 * @run main NullHostnameCheck
 */


public final class NullHostnameCheck {

    public static void main(String[] args) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(
                new ByteArrayInputStream(Base64.getDecoder().
                        decode(keystoreB64)),
                "123456".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "123456".toCharArray());
        SSLContext serverCtx = SSLContext.getInstance("TLSv1.2");
        serverCtx.init(kmf.getKeyManagers(), null, null);
        SSLEngine serverEngine = serverCtx.createSSLEngine("localhost", -1);
        serverEngine.setUseClientMode(false);

        SSLContext clientCtx = SSLContext.getInstance("TLSv1.2");
        clientCtx.init(null, new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] x509Certificates, String s) {
                    }

                    @Override
                    public void checkServerTrusted(
                            X509Certificate[] x509Certificates, String s) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        }, null);

        SSLEngine clientEngine = clientCtx.createSSLEngine();
        clientEngine.setUseClientMode(true);

        SSLParameters sslParameters = clientEngine.getSSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        clientEngine.setSSLParameters(sslParameters);
        try {
            handshake(clientEngine, serverEngine);
            throw new Exception("Value was not null.  Unexpected.");
        } catch (SSLHandshakeException e) {
            if (e.getCause() instanceof CertificateException) {
                System.out.println("Correct Exception class thrown:\n\t" +
                        e.getMessage());
                return;
            }
            throw e;
        }
    }

    private static void handshake(SSLEngine clientEngine,
            SSLEngine serverEngine) throws SSLException{
        ByteBuffer cTOs = ByteBuffer.allocate(
                clientEngine.getSession().getPacketBufferSize());
        ByteBuffer sTOc = ByteBuffer.allocate(
                serverEngine.getSession().getPacketBufferSize());

        ByteBuffer serverAppReadBuffer = ByteBuffer.allocate(
                serverEngine.getSession().getApplicationBufferSize());
        ByteBuffer clientAppReadBuffer = ByteBuffer.allocate(
                clientEngine.getSession().getApplicationBufferSize());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        ByteBuffer empty = ByteBuffer.allocate(0);

        SSLEngineResult clientResult;
        SSLEngineResult serverResult;

        boolean clientHandshakeFinished = false;
        boolean serverHandshakeFinished = false;

        do {
            if (!clientHandshakeFinished) {
                clientResult = clientEngine.wrap(empty, cTOs);
                runDelegatedTasks(clientResult, clientEngine);

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            }

            if (!serverHandshakeFinished) {
                serverResult = serverEngine.wrap(empty, sTOc);
                runDelegatedTasks(serverResult, serverEngine);

                if (isHandshakeFinished(serverResult)) {
                    serverHandshakeFinished = true;
                }
            }

            cTOs.flip();
            sTOc.flip();

            if (!clientHandshakeFinished) {
                clientResult = clientEngine.unwrap(sTOc, clientAppReadBuffer);

                runDelegatedTasks(clientResult, clientEngine);

                if (isHandshakeFinished(clientResult)) {
                    clientHandshakeFinished = true;
                }
            }

            if (!serverHandshakeFinished) {
                serverResult = serverEngine.unwrap(cTOs, serverAppReadBuffer);
                runDelegatedTasks(serverResult, serverEngine);

                if (isHandshakeFinished(serverResult)) {
                    serverHandshakeFinished = true;
                }
            }

            sTOc.compact();
            cTOs.compact();
        } while (!clientHandshakeFinished || !serverHandshakeFinished);
    }

    private static boolean isHandshakeFinished(SSLEngineResult result) {
        return result.getHandshakeStatus() ==
                SSLEngineResult.HandshakeStatus.FINISHED;
    }

    private static void runDelegatedTasks(SSLEngineResult result,
            SSLEngine engine) {
        if (result.getHandshakeStatus() ==
                SSLEngineResult.HandshakeStatus.NEED_TASK) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }
                task.run();
            }
        }
    }

// Base64 of PKCS12 Keystore
static final String keystoreB64 =
        "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" +
        "RvBBTQP0GN26PaNdjOaE/AzK7bbhZGNAIDAYag";
}
