/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8042449 8299870
 * @library /javax/net/ssl/templates
 * @summary Verify successful handshake ignores invalid record version
 *
 * @run main/timeout=300 HandshakeWithInvalidRecordVersion
 */

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.io.*;
import java.security.*;
import java.nio.*;
import java.util.Arrays;

public class HandshakeWithInvalidRecordVersion extends SSLContextTemplate {
    private static final boolean DEBUG = Boolean.getBoolean("test.debug");


    public static void main(String [] args) throws Exception {
        var runner = new HandshakeWithInvalidRecordVersion();
        runner.executeTest("TLSv1.2",
                new String[]{"TLSv1.2"}, new String[]{"TLSv1.3", "TLSv1.2"});

        runner.executeTest("TLSv1.2",
                new String[]{"TLSv1.3", "TLSv1.2"}, new String[]{"TLSv1.2"});

        runner.executeTest("TLSv1.3",
                new String[]{"TLSv1.2", "TLSv1.3"}, new String[]{"TLSv1.3"});

        runner.executeTest("TLSv1.3",
                new String[]{"TLSv1.3"}, new String[]{"TLSv1.2", "TLSv1.3"});
    }


    private void executeTest(String expectedProtocol, String[] clientProtocols,
                                    String[] serverProtocols) throws Exception {
        System.out.printf("Executing test%n"
                + "Client protocols: %s%nServer protocols: %s%nExpected negotiated: %s%n",
                Arrays.toString(clientProtocols), Arrays.toString(serverProtocols),
                expectedProtocol);

        SSLEngine cliEngine = createClientSSLContext().createSSLEngine();
        cliEngine.setUseClientMode(true);
        cliEngine.setEnabledProtocols(clientProtocols);
        SSLEngine srvEngine = createServerSSLContext().createSSLEngine();
        srvEngine.setUseClientMode(false);
        srvEngine.setEnabledProtocols(serverProtocols);

        SSLSession session = cliEngine.getSession();
        int netBufferMax = session.getPacketBufferSize();
        int appBufferMax = session.getApplicationBufferSize();

        ByteBuffer cliToSrv = ByteBuffer.allocateDirect(netBufferMax);
        ByteBuffer srvIBuff = ByteBuffer.allocateDirect(appBufferMax + 50);
        ByteBuffer cliOBuff = ByteBuffer.wrap("I'm client".getBytes());


        System.out.println("Generating ClientHello");
        SSLEngineResult cliRes = cliEngine.wrap(cliOBuff, cliToSrv);
        checkResult(cliRes, HandshakeStatus.NEED_UNWRAP);
        log("Client wrap result: " + cliRes);
        cliToSrv.flip();
        if (cliToSrv.limit() > 5) {
            System.out.println("Setting record version to (0xa9, 0xa2)");
            cliToSrv.put(1, (byte)0xa9);
            cliToSrv.put(2, (byte)0xa2);
        } else {
            throw new RuntimeException("ClientHello message is only "
                    + cliToSrv.limit() + "bytes. Expecting at least 6 bytes. ");
        }

        System.out.println("Processing ClientHello");
        SSLEngineResult srv = srvEngine.unwrap(cliToSrv, srvIBuff);
        checkResult(srv, HandshakeStatus.NEED_TASK);
        runDelegatedTasks(srvEngine);

        finishHandshake(cliEngine, srvEngine);

        if (!cliEngine.getSession().getProtocol()
                .equals(srvEngine.getSession().getProtocol())
            || !cliEngine.getSession().getProtocol().equals(expectedProtocol)) {
            throw new RuntimeException("Client and server did not negotiate protocol. "
                    + "Expected: " + expectedProtocol + ". Negotiated: "
                    + cliEngine.getSession().getProtocol());
        }
    }
    private boolean isHandshaking(SSLEngine e) {
        return (e.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING);
    }

    private void finishHandshake(SSLEngine client, SSLEngine server) throws Exception {
        boolean clientDone = false;
        boolean serverDone = false;
        SSLEngineResult serverResult;
        SSLEngineResult clientResult;
        int capacity = client.getSession().getPacketBufferSize();
        ByteBuffer emptyBuffer = ByteBuffer.allocate(capacity);
        ByteBuffer serverToClient = ByteBuffer.allocate(capacity);
        ByteBuffer clientToServer = ByteBuffer.allocate(capacity);

        System.out.println("Finishing handshake...");
        while (isHandshaking(client) ||
                isHandshaking(server)) {

            log("================");

            clientResult = client.wrap(emptyBuffer, clientToServer);
            serverResult = server.wrap(emptyBuffer, serverToClient);

            if (clientResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (serverResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            log("wrap1 = " + clientResult);
            log("wrap2 = " + serverResult);

            if (clientResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = client.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (serverResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = server.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            clientToServer.flip();
            serverToClient.flip();

            log("----");

            clientResult = client.unwrap(serverToClient, emptyBuffer);
            serverResult = server.unwrap(clientToServer, emptyBuffer);

            if (clientResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                clientDone = true;
            }

            if (serverResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                serverDone = true;
            }

            log("unwrap1 = " + clientResult);
            log("unwrap2 = " + serverResult);

            if (clientResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = client.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            if (serverResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable runnable;
                while ((runnable = server.getDelegatedTask()) != null) {
                    runnable.run();
                }
            }

            clientToServer.clear();
            serverToClient.clear();
        }

        System.out.println("Handshake complete");

        if (!clientDone || !serverDone) {
            throw new RuntimeException("Both should be true:\n" +
                    " clientDone = " + clientDone + " serverDone = " + serverDone);
        }
    }

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable runnable;
        while ((runnable = engine.getDelegatedTask()) != null) {
            log("\trunning delegated task...");
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

    private static void log(Object msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }
}
