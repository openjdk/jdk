/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341964
 * @library /test/lib
 * @run main/othervm DisabledCipherSuitesNotNegotiated client
 * @run main/othervm DisabledCipherSuitesNotNegotiated server
 */

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.security.SecurityUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLHandshakeException;

public class DisabledCipherSuitesNotNegotiated {
    private static final String TLS_PROTOCOL = "TLSv1.2";
    private static volatile int serverPort = 0;
    private static volatile Exception serverException = null;

    private static final CountDownLatch waitForServer = new CountDownLatch(1);
    private static final int WAIT_FOR_SERVER_SECS = 5;

    private static final String DISABLED_CIPHERSUITE = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";
    private static final String DISABLED_CIPHER_WILDCARD = "TLS_ECDH*WITH_AES_256_GCM_*";

    private static void runServer(boolean disabledInClient) throws Exception {
        SSLContext ctx = SSLContext.getInstance(TLS_PROTOCOL);
        ctx.init(null, null, null);
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        try(SSLServerSocket serverSocket = (SSLServerSocket)factory
                .createServerSocket(0, -1, InetAddress.getLoopbackAddress())) {
            serverPort = serverSocket.getLocalPort();
            waitForServer.countDown();

            if (disabledInClient) {
                // set cipher suite to disabled ciphersuite
                serverSocket.setEnabledCipherSuites(new String[]{DISABLED_CIPHERSUITE});
            }

            try(SSLSocket clientSocket = (SSLSocket) serverSocket.accept()) {
                try {
                    clientSocket.getInputStream().readAllBytes();
                    throw new Exception("SERVER: The expected handshake exception was not thrown.");
                } catch (SSLHandshakeException exc) {
                    System.out.println("Server caught expected SSLHandshakeException");
                    exc.printStackTrace(System.out);
                }
            }
        }
    }

    private static void runClient(boolean disableInClient, int portNumber) throws Exception {
        SSLContext ctx = SSLContext.getInstance(TLS_PROTOCOL);
        ctx.init(null, null, null);
        SSLSocketFactory factory = ctx.getSocketFactory();
        try(SSLSocket socket = (SSLSocket)factory.createSocket("localhost", portNumber)) {
            if (!disableInClient) {
                socket.setEnabledCipherSuites(new String[]{DISABLED_CIPHERSUITE});
            }

            try {
                socket.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
                throw new Exception("CLIENT: The expected handshake exception was not thrown.");
            } catch (SSLHandshakeException exc) {
                System.out.println("Client caught expected SSLHandshakeException");
            }
        }
    }

    public static void main(String [] args) throws Exception {
        if (args.length == 1) {
            // run server-side
            final boolean disabledInClient = args[0].equals("client");
            if (!disabledInClient) {
                SecurityUtils.addToDisabledTlsAlgs(DISABLED_CIPHER_WILDCARD);
            }
            try(ExecutorService executorService = Executors.newSingleThreadExecutor()) {
                executorService.submit(() -> {
                    try {
                        runServer(disabledInClient);
                    } catch (Exception exc) {
                        System.out.println("Server Exception:");
                        exc.printStackTrace(System.out);
                        serverException = exc;
                        throw new RuntimeException(exc);
                    }
                });

                if (!waitForServer.await(WAIT_FOR_SERVER_SECS, TimeUnit.SECONDS)) {
                    throw new Exception("Server did not start within " +
                            WAIT_FOR_SERVER_SECS + " seconds.");
                }

                System.out.printf("Server listening on port %d.%nStarting client process...",
                        serverPort);

                OutputAnalyzer oa = ProcessTools.executeProcess(
                        ProcessTools.createTestJavaProcessBuilder("DisabledCipherSuitesNotNegotiated",
                                "" + disabledInClient, "" + serverPort));
                oa.shouldHaveExitValue(0);
                System.out.println("Client output:");
                System.out.println(oa.getOutput());
                if (serverException != null) {
                    throw new Exception ("Server-side threw an unexpected exception: "
                            + serverException);
                }
            }

        } else if (args.length == 2) {
            // run client-side
            boolean disabledInClient = Boolean.parseBoolean(args[0]);
            if (disabledInClient) {
                SecurityUtils.addToDisabledTlsAlgs(DISABLED_CIPHER_WILDCARD);
            }
            runClient(Boolean.parseBoolean(args[0]), Integer.parseInt(args[1]));

        } else {
            throw new Exception(
                    "DisabledCipherSuitesNotNegotiated called with invalid arguments");
        }
    }

}
