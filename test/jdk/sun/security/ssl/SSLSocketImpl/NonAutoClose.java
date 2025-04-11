/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 4404399
 * @comment this test does not work in TLSv1.3 as the spec changes the
 *          behaviors of close_notify.
 * @summary When a layered SSL socket is closed, it should wait for close_notify
 * @library /test/lib /javax/net/ssl/templates
 * @run main/othervm NonAutoClose TLSv1
 * @run main/othervm NonAutoClose TLSv1.1
 * @run main/othervm NonAutoClose TLSv1.2
 * @author Brad Wetmore
 */

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.*;

import jdk.test.lib.security.SecurityUtils;

import static jdk.test.lib.Asserts.assertEquals;

public class NonAutoClose extends SSLContextTemplate {

    /*
     * Is the server ready to serve?
     */
    private static final CountDownLatch SERVER_READY = new CountDownLatch(1);

    /*
     * Turn on SSL debugging?
     */
    private final static boolean DEBUG = Boolean.getBoolean("test.debug");
    private final static int NUM_ITERATIONS = 10;
    private final static int PLAIN_SERVER_VAL = 1;
    private final static int PLAIN_CLIENT_VAL = 2;
    private final static int TLS_SERVER_VAL = 3;
    private final static int TLS_CLIENT_VAL = 4;

    /*
     * Define the server side of the test.
     */
    private void doServerSide() throws Exception {
        System.out.println("Starting server");
        SSLSocketFactory sslsf = createServerSSLContext().getSocketFactory();

        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            SERVER_PORT = serverSocket.getLocalPort();

            /*
             * Signal Client, we're ready for his connect.
             */
            System.out.println("Signal server ready");
            SERVER_READY.countDown();

            try (Socket plainSocket = serverSocket.accept();
                 InputStream is = plainSocket.getInputStream();
                 OutputStream os = plainSocket.getOutputStream()) {

                assertEquals(PLAIN_CLIENT_VAL, is.read());

                os.write(PLAIN_SERVER_VAL);
                os.flush();

                for (int i = 1; i <= NUM_ITERATIONS; i++) {
                    if (DEBUG) {
                        System.out.println("=================================");
                        System.out.println("Server Iteration #" + i);
                    }

                    try (SSLSocket ssls = (SSLSocket) sslsf.createSocket(plainSocket,
                            plainSocket.getInetAddress().getHostName(),
                            plainSocket.getPort(), false)) {

                        ssls.setEnabledProtocols(new String[]{protocol});
                        ssls.setUseClientMode(false);
                        try (InputStream sslis = ssls.getInputStream();
                             OutputStream sslos = ssls.getOutputStream()) {

                            assertEquals(TLS_CLIENT_VAL, sslis.read());

                            sslos.write(TLS_SERVER_VAL);
                            sslos.flush();
                        }
                    }
                }

                assertEquals(PLAIN_CLIENT_VAL, is.read());

                os.write(PLAIN_SERVER_VAL);
                os.flush();
            }
        }
    }

    /*
     * Define the client side of the test.
     */
    private void doClientSide() throws Exception {
        /*
         * Wait for server to get started.
         */
        System.out.println("Waiting for server ready");
        SERVER_READY.await();

        SSLSocketFactory sslsf = createClientSSLContext().getSocketFactory();

        try (Socket plainSocket = new Socket(InetAddress.getLocalHost(), SERVER_PORT);
             InputStream is = plainSocket.getInputStream();
             OutputStream os = plainSocket.getOutputStream()) {

            os.write(PLAIN_CLIENT_VAL);
            os.flush();

            assertEquals(PLAIN_SERVER_VAL, is.read());

            for (int i = 1; i <= NUM_ITERATIONS; i++) {
                if (DEBUG) {
                    System.out.println("===================================");
                    System.out.println("Client Iteration #" + i);
                }
                try (SSLSocket ssls = (SSLSocket) sslsf.createSocket(plainSocket,
                        plainSocket.getInetAddress().getHostName(),
                        plainSocket.getPort(), false);
                     InputStream sslis = ssls.getInputStream();
                     OutputStream sslos = ssls.getOutputStream()) {

                    ssls.setUseClientMode(true);

                    sslos.write(TLS_CLIENT_VAL);
                    sslos.flush();

                    assertEquals(TLS_SERVER_VAL, sslis.read());
                }
            }

            os.write(PLAIN_CLIENT_VAL);
            os.flush();
            assertEquals(PLAIN_SERVER_VAL, is.read());
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    private volatile int SERVER_PORT = 0;
    private volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {
        String protocol = args[0];
        if ("TLSv1".equals(protocol) || "TLSv1.1".equals(protocol)) {
            SecurityUtils.removeFromDisabledTlsAlgs(protocol);
        }

        if (DEBUG)
            System.setProperty("javax.net.debug", "all");

        /*
         * Start the tests.
         */
        new NonAutoClose(protocol);
    }

    private Thread clientThread = null;
    private final String protocol;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    NonAutoClose(String protocol) throws Exception {
        this.protocol = protocol;
        startClient();
        doServerSide();

        /*
         * Wait for other side to close down.
         */
        clientThread.join();

        if (clientException != null) {
            System.err.print("Client Exception:");
            throw clientException;
        }
    }

    private void startClient() {
        clientThread = new Thread(() -> {
            try {
                doClientSide();
            } catch (Exception e) {
                /*
                 * Our client thread just died.
                 */
                System.err.println("Client died...");
                clientException = e;
            }
        });
        clientThread.start();
    }
}
