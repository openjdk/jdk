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

/**
 * @test
 * @bug 8806542
 * @summary Trailing dot in hostname causes TLS handshake to fail
 * @library /javax/net/ssl/templates
 * @run main/othervm -Djdk.net.hosts.file=hostsForExample EndingDotHostname
 */

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EndingDotHostname {
    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.net.hosts.file", "hostsForExample");
        (new EndingDotHostname()).run();
    }

    public void run() throws Exception {
        bootUp();
    }

    // =================================================
    // Stuffs to boot up the client-server mode testing.
    private Thread serverThread = null;
    private volatile Exception serverException = null;
    private volatile Exception clientException = null;

    // Is the server ready to serve?
    protected final CountDownLatch serverCondition = new CountDownLatch(1);

    // Is the client ready to handshake?
    protected final CountDownLatch clientCondition = new CountDownLatch(1);

    // What's the server port?  Use any free port by default
    protected volatile int serverPort = 0;

    // Boot up the testing, used to drive remainder of the test.
    private void bootUp() throws Exception {
        Exception startException = null;
        try {
            startServer();
            startClient();
        } catch (Exception e) {
            startException = e;
        }

        // Wait for other side to close down.
        if (serverThread != null) {
            serverThread.join();
        }

        // The test is pretty much over. Which side threw an exception?
        Exception local = clientException;
        Exception remote = serverException;

        Exception exception = null;

        // Check various exception conditions.
        if ((local != null) && (remote != null)) {
            // If both failed, return the curthread's exception.
            local.initCause(remote);
            exception = local;
        } else if (local != null) {
            exception = local;
        } else if (remote != null) {
            exception = remote;
        } else if (startException != null) {
            exception = startException;
        }

        // If there was an exception *AND* a startException, output it.
        if (exception != null) {
            if (exception != startException && startException != null) {
                exception.addSuppressed(startException);
            }
            throw exception;
        }

        // Fall-through: no exception to throw!
    }

    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                doServerSide();
            } catch (Exception e) {
                // Our server thread just died. Release the client,
                // if not active already...
                serverException = e;
            }
        });

        serverThread.start();
    }

    private void startClient() {
        try {
            doClientSide();
        } catch (Exception e) {
            clientException = e;
        }
    }

    protected void doServerSide() throws Exception {
        // kick off the server side service
        SSLContext context = SSLExampleCert.createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();

        SSLServerSocket sslServerSocket =
                (SSLServerSocket)sslssf.createServerSocket();
        sslServerSocket.bind(new InetSocketAddress(
                InetAddress.getLoopbackAddress(), 0));
        serverPort = sslServerSocket.getLocalPort();

        // Signal the client, the server is ready to accept connection.
        serverCondition.countDown();

        // Try to accept a connection in 30 seconds.
        SSLSocket sslSocket;
        try {
            sslServerSocket.setSoTimeout(30000);
            sslSocket = (SSLSocket)sslServerSocket.accept();
        } catch (SocketTimeoutException ste) {
            // Ignore the test case if no connection within 30 seconds.
            System.out.println(
                    "No incoming client connection in 30 seconds. " +
                            "Ignore in server side.");
            return;
        } finally {
            sslServerSocket.close();
        }

        // handle the connection
        try {
            // Is it the expected client connection?
            //
            // Naughty test cases or third party routines may try to
            // connection to this server port unintentionally.  In
            // order to mitigate the impact of unexpected client
            // connections and avoid intermittent failure, it should
            // be checked that the accepted connection is really linked
            // to the expected client.
            boolean clientIsReady =
                    clientCondition.await(30L, TimeUnit.SECONDS);

            if (clientIsReady) {
                // Run the application in server side.
                runServerApplication(sslSocket);
            } else {    // Otherwise, ignore
                // We don't actually care about plain socket connections
                // for TLS communication testing generally.  Just ignore
                // the test if the accepted connection is not linked to
                // the expected client or the client connection timeout
                // in 30 seconds.
                System.out.println(
                        "The client is not the expected one or timeout. " +
                                "Ignore in server side.");
            }
        } finally {
            sslSocket.close();
        }
    }

    // Define the server side application of the test for the specified socket.
    protected void runServerApplication(SSLSocket socket) throws Exception {
        // here comes the test logic
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslIS.read();
        sslOS.write(85);
        sslOS.flush();
    }

    protected void doClientSide() throws Exception {
        // Wait for server to get started.
        //
        // The server side takes care of the issue if the server cannot
        // get started in 90 seconds.  The client side would just ignore
        // the test case if the serer is not ready.
        boolean serverIsReady =
                serverCondition.await(90L, TimeUnit.SECONDS);
        if (!serverIsReady) {
            System.out.println(
                    "The server is not ready yet in 90 seconds. " +
                            "Ignore in client side.");
            return;
        }

        SSLContext context = SSLExampleCert.createClientSSLContext();
        SSLSocketFactory sslsf = context.getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket(
                "www.example.com.", serverPort)) {
            // OK, here the client and server get connected.
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParameters);

            // Signal the server, the client is ready to communicate.
            clientCondition.countDown();

            // There is still a chance in theory that the server thread may
            // wait client-ready timeout and then quit.  The chance should
            // be really rare so we don't consider it until it becomes a
            // real problem.

            // Run the application in client side.
            runClientApplication(sslSocket);
        }
    }

    // Define the client side application of the test for the specified socket.
    protected void runClientApplication(SSLSocket socket) throws Exception {
        InputStream sslIS = socket.getInputStream();
        OutputStream sslOS = socket.getOutputStream();

        sslOS.write(280);
        sslOS.flush();
        sslIS.read();
    }
}

