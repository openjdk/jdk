/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331682
 * @summary Slow networks/Impatient clients can potentially send
 *          unencrypted TLSv1.3 alerts that won't parse on the server.
 * @library /javax/net/ssl/templates
 * @library /test/lib
 * @run main/othervm SSLSocketNoServerHelloClientShutdown
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.security.SecurityUtils.inspectTlsBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Override;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * To reproduce @bug 8331682 (client sends an unencrypted TLS alert during 1.3 handshake)
 * with SSLSockets we use an SSLSocket on the server side and a plain TCP socket backed by
 * SSLEngine on the client side.
 */
public class SSLSocketNoServerHelloClientShutdown extends SSLEngineNoServerHelloClientShutdown {

    private volatile Exception clientException;
    private volatile Exception serverException;
    private volatile Socket clientSocket;

    public static void main(String[] args) throws Exception {
        new SSLSocketNoServerHelloClientShutdown().runTest();
    }

    public SSLSocketNoServerHelloClientShutdown() throws Exception {
        super();
    }

    private void runTest() throws Exception {
        // Set up SSL server
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        try (SSLServerSocket serverSocket = (SSLServerSocket) sslssf.createServerSocket()) {

            serverSocket.setReuseAddress(false);
            serverSocket.bind(null);
            int port = serverSocket.getLocalPort();
            log("Port: " + port);
            Thread thread = createClientThread(port);

            try {
                // Server-side SSL socket that will read.
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                InputStream is = socket.getInputStream();
                byte[] inbound = new byte[8192];

                int len = is.read(inbound);
                log("Server reads " + len + " bytes");

            } catch (Exception e) {
                serverException = e;
                log(e.getMessage());
            } finally {
                thread.join();
            }
        } finally {
            if (serverException != null && serverException instanceof SSLHandshakeException) {
                throw serverException;
            }
            if (clientException != null) {
                throw clientException;
            }
        }
    }

    private Thread createClientThread(final int port) {

        Thread t = new Thread("ClientThread") {
            @Override
            public void run() {
                try {
                    Queue<ByteBuffer> delayed = new ArrayDeque<>() {};
                    SSLEngineResult clientResult;
                    // Client-side plain TCP socket.
                    clientSocket = new Socket("localhost", port);

                    log("=================");

                    // Produce client_hello
                    log("---Client Wrap client_hello---");
                    clientResult = clientEngine.wrap(clientOut, cTOs);
                    logEngineStatus(clientEngine, clientResult);
                    runDelegatedTasks(clientEngine);

                    // Send client_hello, read and store all available messages from the server.
                    while (delayed.size() < 6) {
                        ByteBuffer msg = clientWriteRead();
                        if (msg == null) {
                            break;
                        }
                        delayed.add(msg);
                    }

                    // Shutdown client
                    log("---Client closeOutbound---");
                    clientEngine.closeOutbound();

                    // Produce an unencrypted user_canceled
                    log("---Client Wrap user_canceled---");
                    clientResult = clientEngine.wrap(clientOut, cTOs);
                    logEngineStatus(clientEngine, clientResult);
                    runDelegatedTasks(clientEngine);

                    // Produce an unencrypted close_notify
                    log("---Client Wrap close_notify---");
                    clientResult = clientEngine.wrap(clientOut, cTOs);
                    logEngineStatus(clientEngine, clientResult);
                    assertTrue(clientEngine.isOutboundDone());
                    assertEquals(clientResult.getStatus(), Status.CLOSED);
                    runDelegatedTasks(clientEngine);

                    // Send user_canceled and close_notify alerts to server. Server should process
                    // 2 unencrypted alerts and send its own close_notify alert back to the client.
                    ByteBuffer serverCloseNotify = clientWriteRead();

                    // Consume delayed messages.
                    for (int i = 1; !delayed.isEmpty(); i++) {
                        ByteBuffer msg = delayed.remove();
                        inspectTlsBuffer(msg);

                        log("---Client Unwrap delayed flight " + i + "---");
                        clientResult = clientEngine.unwrap(msg, clientIn);
                        logEngineStatus(clientEngine, clientResult);
                        runDelegatedTasks(clientEngine);
                    }

                    // Consume close_notify alert from server.
                    assert serverCloseNotify != null;
                    inspectTlsBuffer(serverCloseNotify);

                    log("---Client Unwrap close_notify response---");
                    clientResult = clientEngine.unwrap(serverCloseNotify, clientIn);
                    logEngineStatus(clientEngine, clientResult);
                    assertTrue(clientEngine.isOutboundDone());
                    assertTrue(clientEngine.isInboundDone());
                    runDelegatedTasks(clientEngine);

                } catch (Exception e) {
                    clientException = e;
                }
            }
        };

        t.start();
        return t;
    }

    protected ByteBuffer clientWriteRead() throws IOException {
        OutputStream os = clientSocket.getOutputStream();
        InputStream is = clientSocket.getInputStream();
        byte[] inbound = new byte[8192];

        cTOs.flip();
        inspectTlsBuffer(cTOs);

        byte[] outbound = new byte[cTOs.limit() - cTOs.position()];
        cTOs.get(outbound);
        cTOs.compact();

        log("---Client writes " + outbound.length + " bytes---");
        os.write(outbound);
        os.flush();

        int len = 0;

        try {
            len = is.read(inbound);
            log("---Client reads " + len + " bytes---");
        } catch (Exception e) {
            log(e.getMessage());
            return null;
        }

        if (len < 1) {
            return null;
        }

        ByteBuffer flight = ByteBuffer.wrap(inbound);
        flight.limit(len);
        return flight;
    }
}
