/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @library /javax/net/ssl/templates
 * @bug 8283577
 * @summary Test SSLEngine to use read-only input bytebuffers
 * @run main/othervm ReadOnlyEngine
 */


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReadOnlyEngine {

    private static String pathToStores = "../etc";
    private static String keyStoreFile = "keystore";
    private static String trustStoreFile = "truststore";
    private static char[] passwd = "passphrase".toCharArray();

    private static String keyFilename =
        System.getProperty("test.src", "./") + "/" + pathToStores +
            "/" + keyStoreFile;
    private static String trustFilename =
        System.getProperty("test.src", "./") + "/" + pathToStores +
            "/" + trustStoreFile;

    SSLEngine server;
    SSLEngine client;
    final static ExecutorService executor = Executors.newSingleThreadExecutor();

    HandshakeStatus doHandshake(SSLEngine engine, ByteBuffer src,
        ByteBuffer dst) {
        HandshakeStatus status;
        status = engine.getHandshakeStatus();
        while (status != SSLEngineResult.HandshakeStatus.FINISHED &&
            status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            dst.clear();
            switch (status) {
                case NEED_UNWRAP:
                    try {
                        return receive(engine, src, dst);
                    } catch (SSLException e) {
                        e.printStackTrace();
                    }
                    break;
                case NEED_WRAP:
                    try {
                        return send(engine, src, dst);
                    } catch (SSLException e) {
                        e.printStackTrace();
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        executor.execute(task);
                    }
                    status = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                    break;
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " +
                        status);
            }
        }
        return status;
    }

    HandshakeStatus send(SSLEngine engine, ByteBuffer src, ByteBuffer dst)
        throws SSLException {
        SSLEngineResult status = engine.wrap(src, dst);
        dst.flip();
        return status.getHandshakeStatus();
    }

    HandshakeStatus receive(SSLEngine engine, ByteBuffer src, ByteBuffer dst)
        throws SSLException {
        SSLEngineResult status = engine.unwrap(src, dst);
        dst.flip();
        return status.getHandshakeStatus();
    }

    ReadOnlyEngine(SSLContext sslc, String ciphersuite) throws Exception {
        System.err.println("==== Test Protocol: " + sslc.getProtocol() +
            ", CipherSuite: " + ciphersuite);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        KeyStore ts = KeyStore.getInstance("PKCS12");

        ks.load(new FileInputStream(keyFilename), passwd);
        ts.load(new FileInputStream(trustFilename), passwd);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passwd);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        sslc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        client = sslc.createSSLEngine("client", 1);
        client.setUseClientMode(true);
        server = sslc.createSSLEngine("server", 2);
        if (ciphersuite != null) {
            server.setEnabledCipherSuites(new String[] { ciphersuite });
        }
        server.setUseClientMode(false);

        SSLSession session = server.getSession();
        int maxData = session.getPacketBufferSize();

        ByteBuffer in = ByteBuffer.allocate(maxData);
        ByteBuffer out = ByteBuffer.allocate(maxData);

        HandshakeStatus statusClient, statusServer;
        client.beginHandshake();
        server.beginHandshake();

        // Do TLS handshake
        do {
            statusClient = doHandshake(client, out, in);
            statusServer = doHandshake(server, in, out);
        } while (statusClient != HandshakeStatus.NOT_HANDSHAKING ||
            statusServer != HandshakeStatus.NOT_HANDSHAKING);

        // Read NST
        in.clear();
        receive(client, out, in);

        System.out.println("done");

        // Send bytes from the client and make sure the server receives the same
        in.clear();
        out.clear();
        String testString = "ASDF";
        in.put(testString.getBytes()).flip();
        String testResult;
        System.out.println("1: Client send: " + testString);
        send(client, in.asReadOnlyBuffer(), out);
        in.clear();
        receive(server, out.asReadOnlyBuffer(), in);
        testResult = StandardCharsets.UTF_8.decode(in.duplicate()).toString();
        System.out.println("1: Server receive: " + testResult);
        if (!testString.equalsIgnoreCase(testResult)) {
            throw new Exception("unequal");
        }

        // Send bytes from the server and make sure the client receives the same
        out.clear();
        in.clear();
        System.out.println("2: Server send: " + testString);
        in.put(testString.getBytes()).flip();
        send(server, in, out);
        in.clear();
        receive(client, out, in);
        testResult = StandardCharsets.UTF_8.decode(in.duplicate()).toString();
        System.out.println("2: Client receive: " + testResult);
        if (!testString.equalsIgnoreCase(testResult)) {
            throw new Exception("not equal");
        }
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1.3"),
            "TLS_AES_256_GCM_SHA384");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1.3"),
            "TLS_CHACHA20_POLY1305_SHA256");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1.2"),
            "TLS_RSA_WITH_AES_128_GCM_SHA256");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1.2"),
            "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1.1"),
            "TLS_RSA_WITH_AES_128_CBC_SHA");
        new ReadOnlyEngine(SSLContext.getInstance("TLSv1"),
            "TLS_RSA_WITH_AES_128_CBC_SHA");
    }
}
