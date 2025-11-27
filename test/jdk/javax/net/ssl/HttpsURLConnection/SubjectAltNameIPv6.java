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
 * @bug 8369950
 * @library /test/lib
 * @summary TLS connection to IPv6 address fails with BCJSSE due to IllegalArgumentException
 * @run main/othervm SubjectAltNameIPv6
 */

import javax.net.ssl.*;
import java.io.*;
import java.net.*;

import jdk.test.lib.net.IPSupport;
import jdk.test.lib.net.SimpleSSLContext;
import jtreg.SkippedException;

public class SubjectAltNameIPv6 {
    /*
     * =============================================================
     * Set the various variables needed for the tests, then
     * specify what tests to run on each side.
     */

    /*
     * Should we run the client or server in a separate thread?
     * Both sides can throw exceptions, but do you have a preference
     * as to which side should be the main thread.
     */
    static boolean separateServerThread = true;

    /*
     * Is the server ready to serve?
     */
    volatile static boolean serverReady = false;

    /*
     * Turn on SSL debugging?
     */
    static boolean debug = false;

    /*
     * If the client or server is doing some kind of object creation
     * that the other side depends on, and that thread prematurely
     * exits, you may experience a hang.  The test harness will
     * terminate all hung threads after its timeout has expired,
     * currently 3 minutes by default, but you might try to be
     * smart about it....
     */

    /*
     * Define the server side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doServerSide() throws Exception {
        SSLServerSocketFactory sslssf =
            new SimpleSSLContext().get().getServerSocketFactory();
        SSLServerSocket sslServerSocket =
            (SSLServerSocket) sslssf.createServerSocket(serverPort, 0, InetAddress.getByName("[::1]"));
        sslServerSocket.setEnabledProtocols(new String[]{"TLSv1.3"});
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal Client, we're ready for his connect.
         */
        serverReady = true;

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        OutputStream sslOS = sslSocket.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sslOS));
        bw.write("HTTP/1.1 200 OK\r\n\r\n\r\n");
        bw.flush();
        Thread.sleep(5000);
        sslSocket.close();
    }

    /*
     * Define the client side of the test.
     *
     * If the server prematurely exits, serverReady will be set to true
     * to avoid infinite hangs.
     */
    void doClientSide() throws Exception {

        /*
         * Wait for server to get started.
         */
        while (!serverReady) {
            Thread.sleep(50);
        }

        SSLSocketFactory sf = new SimpleSSLContext().get().getSocketFactory();
        URL url = new URL("https://[::1]:" + serverPort + "/index.html");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();

        /*
         * Simulate an external JSSE implementation.
         */
        conn.setSSLSocketFactory(wrapSocketFactory(sf));
        conn.getInputStream();

        if (conn.getResponseCode() == -1) {
            throw new RuntimeException("getResponseCode() returns -1");
        }
    }

    /*
     * =============================================================
     * The remainder is just support stuff
     */

    // use any free port by default
    volatile int serverPort = 0;

    volatile Exception serverException = null;
    volatile Exception clientException = null;

    public static void main(String[] args) throws Exception {

        if (!IPSupport.hasIPv6()) {
            throw new SkippedException("Skipping test - IPv6 is not supported");
        }
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }
        /*
         * Start the tests.
         */
        new SubjectAltNameIPv6();
    }

    Thread clientThread = null;
    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SubjectAltNameIPv6() throws Exception {
        if (separateServerThread) {
            startServer(true);
            startClient(false);
        } else {
            startClient(true);
            startServer(false);
        }

        /*
         * Wait for other side to close down.
         */
        if (separateServerThread) {
            serverThread.join();
        } else {
            clientThread.join();
        }

        /*
         * When we get here, the test is pretty much over.
         *
         * If the main thread excepted, that propagates back
         * immediately.  If the other thread threw an exception, we
         * should report back.
         */
        if (serverException != null)
            throw serverException;
        if (clientException != null)
            throw clientException;
    }

    void startServer(boolean newThread) throws Exception {
        if (newThread) {
            serverThread = new Thread() {
                public void run() {
                    try {
                        doServerSide();
                    } catch (Exception e) {
                        /*
                         * Our server thread just died.
                         *
                         * Release the client, if not active already...
                         */
                        System.err.println("Server died:");
                        serverReady = true;
                        serverException = e;
                    }
                }
            };
            serverThread.start();
        } else {
            doServerSide();
        }
    }

    void startClient(boolean newThread) throws Exception {
        if (newThread) {
            clientThread = new Thread() {
                public void run() {
                    try {
                        doClientSide();
                    } catch (Exception e) {
                        /*
                         * Our client thread just died.
                         */
                        System.err.println("Client died...");
                        clientException = e;
                    }
                }
            };
            clientThread.start();
        } else {
            doClientSide();
        }
    }

    /*
     * Wraps SSLSocketImpl to simulate a different JSSE implementation
     */
    private static SSLSocketFactory wrapSocketFactory(final SSLSocketFactory wrap) {
        return new SSLSocketFactory() {
            @Override
            public String[] getDefaultCipherSuites() {
                return wrap.getDefaultCipherSuites();
            }
            @Override
            public String[] getSupportedCipherSuites() {
                return wrap.getSupportedCipherSuites();
            }
            @Override
            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                final SSLSocket so = (SSLSocket) wrap.createSocket(s, host, port, autoClose);
                return new SSLSocket() {
                    @Override
                    public void connect(SocketAddress endpoint,
                                        int timeout) throws IOException {
                        so.connect(endpoint, timeout);
                    }
                    @Override
                    public String[] getSupportedCipherSuites() {
                        return so.getSupportedCipherSuites();
                    }
                    @Override
                    public String[] getEnabledCipherSuites() {
                        return so.getEnabledCipherSuites();
                    }
                    @Override
                    public void setEnabledCipherSuites(String[] suites) {
                        so.setEnabledCipherSuites(suites);
                    }
                    @Override
                    public String[] getSupportedProtocols() {
                        return so.getSupportedProtocols();
                    }
                    @Override
                    public String[] getEnabledProtocols() {
                        return so.getEnabledProtocols();
                    }
                    @Override
                    public void setEnabledProtocols(String[] protocols) {
                        so.setEnabledProtocols(protocols);
                    }
                    @Override
                    public SSLSession getSession() {
                        return so.getSession();
                    }
                    @Override
                    public SSLSession getHandshakeSession() {
                        return so.getHandshakeSession();
                    }
                    @Override
                    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
                        so.addHandshakeCompletedListener(listener);
                    }
                    @Override
                    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
                        so.removeHandshakeCompletedListener(listener);
                    }
                    @Override
                    public void startHandshake() throws IOException {
                        so.startHandshake();
                    }
                    @Override
                    public void setUseClientMode(boolean mode) {
                        so.setUseClientMode(mode);
                    }
                    @Override
                    public boolean getUseClientMode() {
                        return so.getUseClientMode();
                    }
                    @Override
                    public void setNeedClientAuth(boolean need) {
                    }
                    @Override
                    public boolean getNeedClientAuth() {
                        return false;
                    }
                    @Override
                    public void setWantClientAuth(boolean want) {
                    }
                    @Override
                    public boolean getWantClientAuth() {
                        return false;
                    }
                    @Override
                    public void setEnableSessionCreation(boolean flag) {
                        so.setEnableSessionCreation(flag);
                    }
                    @Override
                    public boolean getEnableSessionCreation() {
                        return so.getEnableSessionCreation();
                    }
                    @Override
                    public void close() throws IOException {
                        so.close();
                    }
                    @Override
                    public boolean isClosed() {
                        return so.isClosed();
                    }
                    @Override
                    public void shutdownInput() throws IOException {
                        so.shutdownInput();
                    }
                    @Override
                    public boolean isInputShutdown() {
                        return so.isInputShutdown();
                    }
                    @Override
                    public void shutdownOutput() throws IOException {
                        so.shutdownOutput();
                    }
                    @Override
                    public boolean isOutputShutdown() {
                        return so.isOutputShutdown();
                    }
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return so.getInputStream();
                    }
                    @Override
                    public OutputStream getOutputStream() throws IOException {
                        return so.getOutputStream();
                    }
                    @Override
                    public SSLParameters getSSLParameters() {
                        return so.getSSLParameters();
                    }
                    @Override
                    public void setSSLParameters(SSLParameters params) {
                        so.setSSLParameters(params);
                    }
                };
            }
            @Override
            public Socket createSocket(String h, int p) throws IOException, UnknownHostException {
                return null;
            }
            @Override
            public Socket createSocket(String h, int p, InetAddress ipa, int lp) throws IOException, UnknownHostException {
                return null;
            }
            @Override
            public Socket createSocket(InetAddress h, int p) throws IOException {
                return null;
            }
            @Override
            public Socket createSocket(InetAddress a, int p, InetAddress l, int lp) throws IOException {
                return null;
            }
        };
    }
}
