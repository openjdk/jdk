/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /javax/net/ssl/templates
 * @summary Test that a New Session Ticket will be generated when a
 * SSLSessionBindingListener is set (boundValues)
 * @run main/othervm -Djavax.net.debug=ssl ResumptionUpdateBoundValues
 */

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import jdk.test.lib.process.OutputAnalyzer;

public class ResumptionUpdateBoundValues extends SSLContextTemplate {

    final CountDownLatch serverReady = new CountDownLatch(1);

    ArrayBlockingQueue<SBListener> sbListeners = new ArrayBlockingQueue<>(100);

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception clientException = null;

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */

    void doServerSide() throws Exception {
        SSLSocket sslSocket = null;

        SSLServerSocketFactory sslssf = createServerSSLContext().getServerSocketFactory();
        try (SSLServerSocket sslServerSocket =
                     (SSLServerSocket) sslssf.createServerSocket(serverPort, -1, InetAddress.getLoopbackAddress())) {
            serverPort = sslServerSocket.getLocalPort();

            /*
             * Signal Client, we're ready for his connect.
             */
            serverReady.countDown();

            sslSocket = (SSLSocket) sslServerSocket.accept();
            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslIS.read();
            sslOS.write(85);
            sslOS.flush();
            SSLSession sslSession = sslSocket.getSession();
            SBListener sbListener = new SBListener(sslSession);
            sslSession.putValue("x", sbListener);

            sslIS.read();
            sslOS.write(85);
            sslOS.flush();

            sslIS.read();
            sslOS.write(85);
            sslOS.flush();
        } finally {
            if (sslSocket != null) {
                sslSocket.close();
            }
        }
    }
    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */

    SBListener doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        if (!serverReady.await(10, TimeUnit.SECONDS)) {
            throw new RuntimeException("Server not ready in 10 seconds.");
        }

        SSLSocketFactory sslsf = createClientSSLContext().getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket)
                sslsf.createSocket(InetAddress.getLoopbackAddress(), serverPort)) {
            InputStream sslIS = sslSocket.getInputStream();
            OutputStream sslOS = sslSocket.getOutputStream();

            sslOS.write(280);
            sslOS.flush();
            sslIS.read();

            SSLSession sslSession = sslSocket.getSession();
            System.out.printf(" sslSession: %s %n   %s%n", sslSession, sslSession.getClass());
            SBListener sbListener = new SBListener(sslSession);

            sslOS.write(280);
            sslOS.flush();
            sslIS.read();

            sslOS.write(280);
            sslOS.flush();
            sslIS.read();

            sslOS.close();
            sslIS.close();
            sslSocket.close();

            return sbListener;
        }
    }


    public static void main(String[] args) throws Exception {
        PrintStream oldStderr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try(PrintStream capturedStream = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedStream);
            try {
                new ResumptionUpdateBoundValues().run();
            } finally {
                captured.flush();
                System.setErr(oldStderr);
                oldStderr.print(captured.toString(StandardCharsets.UTF_8));
            }
        }

        new OutputAnalyzer("", captured.toString(StandardCharsets.UTF_8))
            .stderrShouldContain("trigger new session ticket");
    }

    private void run() throws Exception {
        Thread clientThread = startClient();
        try {
            doServerSide();
        } finally {
            clientThread.join();
        }

        SBListener listener = null;
        while ((listener = sbListeners.poll()) != null) {
            if (!listener.check()) {
                System.out.printf("  sbListener not called on finalize: %s%n",
                        listener);
            }
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (clientException != null) {
            System.out.print("Client Exception:");
            throw clientException;
        }
    }

    Thread startClient() throws Exception {
        Thread t = new Thread("Client") {
            public void run() {
                try {
                    sbListeners.add(doClientSide());
                } catch (Exception e) {
                    /*
                     * Our client thread just died.
                     */
                    System.err.println("Client died..." + e);
                    clientException = e;
                }
            }
        };
        System.out.printf(" starting: %s%n", t);
        t.start();
        return t;
    }


    static class SBListener implements SSLSessionBindingListener {
        private volatile int unboundNotified;
        private final WeakReference<SSLSession> session;

        SBListener(SSLSession session) {
            this.unboundNotified = 0;
            this.session = new WeakReference<SSLSession>(session);
        }

        boolean check() {
            System.out.printf("  check: %s%n", this);
            return unboundNotified > 0 && session.get() == null;
        }

        @Override
        public void valueBound(SSLSessionBindingEvent event) {
            System.out.printf(" valueBound: %s%n", event.getName());
        }

        @Override
        public void valueUnbound(SSLSessionBindingEvent event) {
            System.out.printf(" valueUnbound: %s%n", event.getName());
            unboundNotified++;
        }

        public String toString() {
            return "count: " + unboundNotified +
                    ", ref: " + session.get();
        }
    }
}

