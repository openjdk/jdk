/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @library /javax/net/ssl/templates /test/lib
 * @run main/othervm SSLSocketNoServerHelloClientShutdown
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.fail;
import static jdk.test.lib.security.SecurityUtils.inspectTlsBuffer;

import java.io.InputStream;
import java.lang.Override;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * To reproduce @bug 8331682 (client sends an unencrypted TLS alert during
 * TLSv1.3 handshake) with SSLSockets we use an SSLSocket on the server side
 * and a plain TCP socket backed by SSLEngine on the client side.
 * Using SSLEngine allows the client to force the generation of the plaintext
 * alert messages.
 */
public class SSLSocketNoServerHelloClientShutdown
    extends SSLEngineNoServerHelloClientShutdown {

    private volatile Exception clientException;
    private volatile Exception serverException;
    private final CountDownLatch serverLatch;

    public static void main(String[] args) throws Exception {
        new SSLSocketNoServerHelloClientShutdown().runTest();
    }

    public SSLSocketNoServerHelloClientShutdown() throws Exception {
        super();
        serverLatch = new CountDownLatch(1);
    }

    private void runTest() throws Exception {
        // Set up SSL server
        SSLContext context = createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        try (SSLServerSocket serverSocket =
            (SSLServerSocket) sslssf.createServerSocket()) {

            serverSocket.setReuseAddress(false);
            serverSocket.bind(null);
            int port = serverSocket.getLocalPort();
            log("Port: " + port);
            Thread thread = createClientThread(port);

            try {
                // Server-side SSL socket that will read.
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                InputStream is = socket.getInputStream();
                byte[] inbound = new byte[512];

                log("===Server is ready and reading===");
                if (is.read(inbound) > 0) {
                    throw new Exception("Server returned data");
                }
            } catch (Exception e) {
                serverException = e;
                log(e.toString());
            } finally {
                serverLatch.countDown();
                thread.join();
            }
        } finally {
            if (serverException != null) {
                serverException.printStackTrace();
                assertEquals(
                    SSLProtocolException.class, serverException.getClass());
                assertEquals(GeneralSecurityException.class,
                             serverException.getCause().getClass());
                assertEquals(
                    EXCEPTION_MSG, serverException.getCause().getMessage());
            } else {
                fail("Server should have thrown SSLProtocolException");
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
                // Client-side plain TCP socket.
                try (SocketChannel clientSocketChannel = SocketChannel.open(
                        new InetSocketAddress("localhost", port))) {

                    SSLEngineResult clientResult;

                    log("=================");

                    // Produce client_hello
                    log("---Client Wrap client_hello---");
                    clientResult = clientEngine.wrap(clientOut, cTOs);
                    logEngineStatus(clientEngine, clientResult);
                    runDelegatedTasks(clientEngine);

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
                    runDelegatedTasks(clientEngine);
                    assertTrue(clientEngine.isOutboundDone());
                    assertEquals(clientResult.getStatus(), Status.CLOSED);

                    // Send client_hello, user_canceled alert and close_notify
                    // alert to server. Server should throw a proper exception
                    // when receiving an unencrypted 2 byte packet user_canceled
                    // alert.
                    cTOs.flip();
                    inspectTlsBuffer(cTOs);
                    log("---Client sends unencrypted alerts---");
                    int len = clientSocketChannel.write(cTOs);

                    serverLatch.await();
                } catch (Exception e) {
                    clientException = e;
                }
            }
        };

        t.start();
        return t;
    }
}
