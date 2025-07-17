/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293562
 * @summary Http keep-alive thread should close sockets without holding a lock
 * @library /test/lib
 * @run main/othervm -Dhttp.keepAlive.time.server=1 B8293562
 */

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import jdk.test.lib.net.URIBuilder;

public class B8293562 {
    static HttpServer server;
    static CountDownLatch closing = new CountDownLatch(1);
    static CountDownLatch secondRequestDone = new CountDownLatch(1);
    static CompletableFuture<Void> result = new CompletableFuture<>();

    public static void main(String[] args) throws Exception {
        startHttpServer();
        clientHttpCalls();
    }

    public static void startHttpServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", new NotFoundHandler());
        server.start();
    }

    public static void clientHttpCalls() throws Exception {
        try {
            System.out.println("http server listens on: " + server.getAddress().getPort());

            URL testUrl = URIBuilder.newBuilder()
                    .scheme("https")
                    .loopback()
                    .port(server.getAddress().getPort())
                    .toURL();

            // SlowCloseSocketFactory is not a real SSLSocketFactory;
            // it produces regular non-SSL sockets. Effectively, the request
            // is made over http.
            HttpsURLConnection.setDefaultSSLSocketFactory(new SlowCloseSocketFactory());
            System.out.println("Performing first request");
            HttpsURLConnection uc = (HttpsURLConnection)testUrl.openConnection(Proxy.NO_PROXY);
            byte[] buf = new byte[1024];
            try {
                uc.getInputStream();
                throw new RuntimeException("Expected 404 here");
            } catch (FileNotFoundException ignored) { }
            try (InputStream is = uc.getErrorStream()) {
                while (is.read(buf) >= 0) {
                }
            }
            System.out.println("First request completed");
            closing.await();
            // KeepAliveThread is closing the connection now
            System.out.println("Performing second request");
            HttpsURLConnection uc2 = (HttpsURLConnection)testUrl.openConnection(Proxy.NO_PROXY);

            try {
                uc2.getInputStream();
                throw new RuntimeException("Expected 404 here");
            } catch (FileNotFoundException ignored) { }
            try (InputStream is = uc2.getErrorStream()) {
                while (is.read(buf) >= 0) {
                }
            }
            System.out.println("Second request completed");
            // let the socket know it can close now
            secondRequestDone.countDown();
            result.get();
            System.out.println("Test completed successfully");
        } finally {
            server.stop(1);
        }
    }

    static class SlowCloseSocket extends SSLSocket {
        @Override
        public synchronized void close() throws IOException {
            String threadName = Thread.currentThread().getName();
            System.out.println("Connection closing, thread name: " + threadName);
            closing.countDown();
            super.close();
            if (threadName.equals("Keep-Alive-Timer")) {
                try {
                    if (secondRequestDone.await(5, TimeUnit.SECONDS)) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(new RuntimeException(
                                "Wait for second request timed out"));
                    }
                } catch (InterruptedException e) {
                    result.completeExceptionally(new RuntimeException(
                            "Wait for second request was interrupted"));
                }
            } else {
                result.completeExceptionally(new RuntimeException(
                        "Close invoked from unexpected thread"));
            }
            System.out.println("Connection closed");
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

    static class SlowCloseSocketFactory extends SSLSocketFactory {

        @Override
        public Socket createSocket() throws IOException {
            return new SlowCloseSocket();
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

    static class NotFoundHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.sendResponseHeaders(404, 3);
            t.getResponseBody().write("abc".getBytes(StandardCharsets.UTF_8));
            t.getResponseBody().close();
        }
    }
}

