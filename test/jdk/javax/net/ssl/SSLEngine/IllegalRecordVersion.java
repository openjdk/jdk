/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

// This test case relies on updated static security property, no way to re-use
// security property in samevm/agentvm mode.

/*
 * @test
 * @bug 8042449
 * @summary Issue for negative byte major record version
 *
 * @run main/othervm/timeout=300 IllegalRecordVersion
 */

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;

public class IllegalRecordVersion {
    private static final String PATH_TO_STORES = "../etc";
    private static final String KEYSTORE_FILE = "keystore";
    private static final String TRUSTSTORE_FILE = "truststore";

    private static final String KEYSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                    "/" + KEYSTORE_FILE;
    private static final String TRUSTSTORE_PATH =
            System.getProperty("test.src", "./") + "/" + PATH_TO_STORES +
                    "/" + TRUSTSTORE_FILE;

    private static SSLContext getSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");
        char[] passphrase = "passphrase".toCharArray();

        ks.load(new FileInputStream(KEYSTORE_PATH), passphrase);
        ts.load(new FileInputStream(TRUSTSTORE_PATH), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance("TLS");

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslCtx;
    }

    public static void main(String args[]) throws Exception {
        SSLContext context = getSSLContext();

        SSLEngine cliEngine = context.createSSLEngine();
        cliEngine.setUseClientMode(true);
        cliEngine.setEnabledProtocols(new String[]{"TLSv1.2"});
        SSLEngine srvEngine = context.createSSLEngine();
        srvEngine.setUseClientMode(false);
        srvEngine.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

        SSLSession session = cliEngine.getSession();
        int netBufferMax = session.getPacketBufferSize();
        int appBufferMax = session.getApplicationBufferSize();

        ByteBuffer cliToSrv = ByteBuffer.allocateDirect(netBufferMax);
        ByteBuffer srvIBuff = ByteBuffer.allocateDirect(appBufferMax + 50);
        ByteBuffer cliOBuff = ByteBuffer.wrap("I'm client".getBytes());


        System.out.println("client hello (record version(0xa9, 0xa2))");
        SSLEngineResult cliRes = cliEngine.wrap(cliOBuff, cliToSrv);
        checkResult(cliRes, HandshakeStatus.NEED_UNWRAP);
        System.out.println("Client wrap result: " + cliRes);
        cliToSrv.flip();
        if (cliToSrv.limit() > 5) {
            cliToSrv.put(1, (byte)0xa9);
            cliToSrv.put(2, (byte)0xa2);
        }

        SSLEngineResult srv = srvEngine.unwrap(cliToSrv, srvIBuff);
        checkResult(srv, HandshakeStatus.NEED_TASK);
        runDelegatedTasks(srvEngine);

        handshake(cliEngine, srvEngine, netBufferMax);

        if (!cliEngine.getSession().getProtocol()
                .equals(srvEngine.getSession().getProtocol())) {
            throw new RuntimeException("Holy Crap!");
        }
    }
    private static boolean isHandshaking(SSLEngine e) {
        return (e.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING);
    }

    private static void handshake(SSLEngine client, SSLEngine server, int capacity) throws Exception {
        boolean clientDone = false;
        boolean serverDone = false;
        SSLEngineResult result2;
        SSLEngineResult result1;
        ByteBuffer emptyBuffer = ByteBuffer.allocate(capacity);
        ByteBuffer serverToClient = ByteBuffer.allocate(capacity);
        ByteBuffer clientToServer = ByteBuffer.allocate(capacity);
        while (isHandshaking(client) ||
                isHandshaking(server)) {

            System.out.println("================");

            result1 = client.wrap(emptyBuffer, clientToServer);
            //checkResult(result1, null, null, 0, -1, clientDone);
            result2 = server.wrap(emptyBuffer, serverToClient);
            //checkResult(result2, null, null, 0, -1, serverDone);

            if (result1.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            System.out.println("wrap1 = " + result1);
            System.out.println("wrap2 = " + result2);

            if (result1.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = client.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = server.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            clientToServer.flip();
            serverToClient.flip();

            System.out.println("----");

            result1 = client.unwrap(serverToClient, emptyBuffer);
            //checkResult(result1, null, null, -1, 0, clientDone);
            result2 = server.unwrap(clientToServer, emptyBuffer);
            //checkResult(result2, null, null, -1, 0, serverDone);

            if (result1.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            System.out.println("unwrap1 = " + result1);
            System.out.println("unwrap2 = " + result2);

            if (result1.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = client.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (result2.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = server.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            clientToServer.clear();
            serverToClient.clear();
        }

        System.out.println("\nDONE HANDSHAKING");
        System.out.println("================");

        if (!clientDone || !serverDone) {
            throw new RuntimeException("Both should be true:\n" +
                    " clientDone = " + clientDone + " serverDone = " + serverDone);
        }
    }

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            System.out.println("\trunning delegated task...");
            runnable.run();
        }
    }

    private static void checkResult(SSLEngineResult result, HandshakeStatus expectedStatus) {
        if(result.getHandshakeStatus() != expectedStatus) {
            throw new RuntimeException(String.format(
                    "Handshake status %s does not match expected status of %s",
                    result.getHandshakeStatus(), expectedStatus));
        }
    }

    private static void dumpBuffer(ByteBuffer buffer) {
        ByteBuffer tmp = buffer.duplicate();
        int count = 1;
        while(tmp.remaining() > 0) {
            System.out.printf("%02X ", tmp.get());
            if (count++ == 16) {
                System.out.println();
                count = 1;
            }
        }
    }
}