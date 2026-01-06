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
 * @summary Test that the HttpsURLConnection does not set IP address literals for
 *          SNI hostname during TLS handshake
 * @library /test/lib
 * @modules java.base/sun.net.util
 * @comment Insert -Djavax.net.debug=all into the following lines to enable SSL debugging
 * @run main/othervm SubjectAltNameIP 127.0.0.1
 * @run main/othervm SubjectAltNameIP ::1
 */

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import jdk.test.lib.Asserts;
import jdk.test.lib.net.IPSupport;
import jdk.test.lib.net.SimpleSSLContext;
import jtreg.SkippedException;
import sun.net.util.IPAddressUtil;

public class SubjectAltNameIP {

    // Is the server ready to serve?
    private final CountDownLatch serverReady = new CountDownLatch(1);

    // Use any free port by default.
    volatile int serverPort = 0;

    // Stores an exception thrown by server in a separate thread.
    volatile Exception serverException = null;

    // SSLSocket object created by HttpsClient internally.
    SSLSocket clientSSLSocket = null;

    // The hostname the server socket is bound to.
    String hostName;

    static final byte[] requestEnd = new byte[] {'\r', '\n', '\r', '\n' };

    // Read until the end of the request.
    void readOneRequest(InputStream is) throws IOException {
        int requestEndCount = 0, r;
        while ((r = is.read()) != -1) {
            if (r == requestEnd[requestEndCount]) {
                requestEndCount++;
                if (requestEndCount == 4) {
                    break;
                }
            } else {
                requestEndCount = 0;
            }
        }
    }

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
            (SSLServerSocket) sslssf.createServerSocket(
                    serverPort, 0,
                    InetAddress.getByName(hostName));
        sslServerSocket.setEnabledProtocols(new String[]{"TLSv1.3"});
        serverPort = sslServerSocket.getLocalPort();

        /*
         * Signal the client, the server is ready to accept connection.
         */
        serverReady.countDown();

        SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
        OutputStream sslOS = sslSocket.getOutputStream();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sslOS));
        bw.write("HTTP/1.1 200 OK\r\n\r\n");
        bw.flush();
        readOneRequest(sslSocket.getInputStream());
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
        serverReady.await();
        if (serverException != null) {
            throw new RuntimeException("Server failed to start.", serverException);
        }

        SSLSocketFactory sf = new SimpleSSLContext().get().getSocketFactory();
        URI uri = new URI("https://" + hostName + ":" + serverPort + "/index.html");
        HttpsURLConnection conn = (HttpsURLConnection)uri.toURL().openConnection();

        /*
         * Simulate an external JSSE implementation and store the client SSLSocket
         * used internally.
         */
        conn.setSSLSocketFactory(wrapSocketFactory(sf,
                sslSocket -> {
                    Asserts.assertEquals(null, clientSSLSocket, "clientSSLSocket is");
                    clientSSLSocket = sslSocket;
                }));
        conn.getInputStream();

        var sniSN = clientSSLSocket.getSSLParameters().getServerNames();
        if (sniSN != null && !sniSN.isEmpty()) {
            throw new RuntimeException("SNI server name '" +
                    sniSN.getFirst() + "' must not be set.");
        }

        if (conn.getResponseCode() == -1) {
            throw new RuntimeException("getResponseCode() returns -1");
        }
    }

    public static void main(String[] args) throws Exception {
        boolean isIpv6Addr = IPAddressUtil.isIPv6LiteralAddress(args[0]);

        if (isIpv6Addr && !IPSupport.hasIPv6()) {
            throw new SkippedException("Skipping test - IPv6 is not supported");
        }
        /*
         * Start the tests.
         */
        if (isIpv6Addr) { // use the URL notion wrapper
            new SubjectAltNameIP("[" + args[0] + "]");
        } else {
            new SubjectAltNameIP(args[0]);
        }
    }

    Thread serverThread = null;

    /*
     * Primary constructor, used to drive remainder of the test.
     *
     * Fork off the other side, then do your work.
     */
    SubjectAltNameIP(String host) throws Exception {
        hostName = host;
        startServer();
        doClientSide();

        /*
         * Wait for other side to close down.
         */
        serverThread.join();

        if (serverException != null)
            throw serverException;
    }

    void startServer() {
        serverThread = new Thread(() -> {
            try {
                doServerSide();
            } catch (Exception e) {
                /*
                 * Our server thread just died.
                 *
                 * Store the exception and release the client.
                 */
                serverException = e;
                serverReady.countDown();
            }
        });
        serverThread.start();
    }

    /*
     * Wraps SSLSocketImpl to simulate a different JSSE implementation
     */
    private static SSLSocketFactory wrapSocketFactory(final SSLSocketFactory wrap, final Consumer<SSLSocket> store) {
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
            public Socket createSocket(Socket s, String host, int port, boolean autoClose)
                    throws IOException {
                final SSLSocket so =
                        (SSLSocket) wrap.createSocket(s, host, port, autoClose);

                // store the underlying SSLSocket for later use
                store.accept(so);

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
            public Socket createSocket(String h, int p) {
                return null;
            }
            @Override
            public Socket createSocket(String h, int p, InetAddress ipa, int lp) {
                return null;
            }
            @Override
            public Socket createSocket(InetAddress h, int p) {
                return null;
            }
            @Override
            public Socket createSocket(InetAddress a, int p, InetAddress l, int lp) {
                return null;
            }
        };
    }
}
