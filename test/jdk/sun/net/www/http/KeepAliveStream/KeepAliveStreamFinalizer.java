/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8240275
 * @library /test/lib
 * @run main/othervm KeepAliveStreamFinalizer
 * @summary HttpsURLConnection: connection must not be reused after finalization
 */

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;

public class KeepAliveStreamFinalizer {

    static Server server;
    private static volatile String failureReason;

    static class Server extends Thread {
        final ServerSocket srv;
        static final byte[] requestEnd = new byte[] {'\r', '\n', '\r', '\n'};

        Server(ServerSocket s) {
            srv = s;
        }

        boolean readOneRequest(InputStream is) throws IOException {
            int requestEndCount = 0, r;
            while ((r = is.read()) != -1) {
                if (r == requestEnd[requestEndCount]) {
                    requestEndCount++;
                    if (requestEndCount == 4) {
                        return true;
                    }
                } else {
                    requestEndCount = 0;
                }
            }
            return false;
        }

        public void run() {
            try {
                while (true) {
                    Socket ss = srv.accept();
                    Thread t1 = new Thread(new Runnable() {
                        public void run() {
                            try {
                                InputStream in = ss.getInputStream();
                                OutputStream out = ss.getOutputStream();
                                while (readOneRequest(in)) {
                                    out.write("HTTP/1.1 200 OK\r\nConnection: Keep-Alive\r\nContent-Length: 1\r\n\r\na".getBytes());
                                    out.flush();
                                }
                                in.close();
                                out.close();
                                ss.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    t1.setDaemon(true);
                    t1.start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress address = startHttpServer();
        clientHttpCalls(address);
        if (failureReason != null) {
            throw new RuntimeException(failureReason);
        }
    }

    public static InetSocketAddress startHttpServer() throws Exception {
        InetAddress localHost = InetAddress.getLoopbackAddress();
        InetSocketAddress address = new InetSocketAddress(localHost, 0);
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(address);
        server = new Server(serverSocket);
        server.setDaemon(true);
        server.start();
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    public static void doRequest(URL url) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection)url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        OutputStreamWriter out = new OutputStreamWriter(c.getOutputStream());
        out.write("test");
        out.close();
        int responseCode = c.getResponseCode();
        // Fully reading the body causes the HttpsClient to be added to the KeepAliveCache immediately,
        // which avoids this issue since GC will not finalize the HttpsClient.
    }

    public static void clientHttpCalls(InetSocketAddress address) throws Exception {
        try {
            System.err.println("http server listen on: " + address.getPort());
            String hostAddr = address.getAddress().getHostAddress();
            if (hostAddr.indexOf(':') > -1) hostAddr = "[" + hostAddr + "]";
            String baseURLStr = "https://" + hostAddr + ":" + address.getPort() + "/";

            URL testUrl = new URL(baseURLStr);

            // CheckFinalizeSocketFactory is not a real SSLSocketFactory;
            // it produces regular non-SSL sockets. Effectively, the request
            // is made over http.
            HttpsURLConnection.setDefaultSSLSocketFactory(new CheckFinalizeSocketFactory());
            // now perform up to 3 requests; with the broken KeepAliveStream finalizer,
            // the second request usually attempts to use a finalized socket
            for (int i = 0; i < 3; i++) {
                System.err.println("Request #" + (i + 1));
                doRequest(testUrl);
                System.gc();
                Thread.sleep(100);
                if (failureReason != null) break;
            }
        } finally {
            server.srv.close();
        }
    }

    static class CheckFinalizeSocket extends SSLSocket {
        private volatile boolean finalized;
        public void finalize() throws Throwable {
            System.err.println("In finalize");
            super.finalize();
            finalized = true;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (finalized) {
                System.err.println(failureReason = "getInputStream called after finalize");
                Thread.dumpStack();
            }
            return super.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (finalized) {
                System.err.println(failureReason = "getOutputStream called after finalize");
                Thread.dumpStack();
            }
            return super.getOutputStream();
        }

        @Override
        public synchronized void close() throws IOException {
            if (finalized) {
                System.err.println(failureReason = "close called after finalize");
                Thread.dumpStack();
            }
            super.close();
        }

        // required abstract method overrides
        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }
        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }
        @Override
        public void setEnabledCipherSuites(String[] suites) { }
        @Override
        public String[] getSupportedProtocols() {
            return new String[0];
        }
        @Override
        public String[] getEnabledProtocols() {
            return new String[0];
        }
        @Override
        public void setEnabledProtocols(String[] protocols) { }
        @Override
        public SSLSession getSession() {
            return null;
        }
        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) { }
        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) { }
        @Override
        public void startHandshake() throws IOException { }
        @Override
        public void setUseClientMode(boolean mode) { }
        @Override
        public boolean getUseClientMode() {
            return false;
        }
        @Override
        public void setNeedClientAuth(boolean need) { }
        @Override
        public boolean getNeedClientAuth() {
            return false;
        }
        @Override
        public void setWantClientAuth(boolean want) { }
        @Override
        public boolean getWantClientAuth() {
            return false;
        }
        @Override
        public void setEnableSessionCreation(boolean flag) { }
        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
    }

    static class CheckFinalizeSocketFactory extends SSLSocketFactory {

        @Override
        public Socket createSocket() throws IOException {
            return new CheckFinalizeSocket();
        }
        // required abstract method overrides
        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }
        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }
        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }
        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }
        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}

