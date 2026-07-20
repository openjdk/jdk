/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * library /test/lib /
 * build jdk.test.lib.net.SimpleSSLContext ProxyServer
 */
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient.Version;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestEchoHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestFileServerHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.common.TestServerConfigurator;
import jdk.test.lib.net.SimpleSSLContext;

public class LightWeightHttpServer {

    static final SSLContext ctx = SimpleSSLContext.findSSLContext();
    static HttpTestServer httpServer;
    static HttpTestServer httpsServer;
    static ExecutorService executor;
    static int port;
    static int httpsport;
    static String httproot;
    static String httpsroot;
    static ProxyServer proxy;
    static int proxyPort;
    static RedirectErrorHandler redirectErrorHandler, redirectErrorHandlerSecure;
    static RedirectHandler redirectHandler, redirectHandlerSecure;
    static DelayHandler delayHandler;
    static final String midSizedFilename = "/files/notsobigfile.txt";
    static final String smallFilename = "/files/smallfile.txt";
    static Path midSizedFile;
    static Path smallFile;
    static String fileroot;

    public static void initServer() throws IOException {

        Logger logger = Logger.getLogger("com.sun.net.httpserver");
        ConsoleHandler ch = new ConsoleHandler();
        logger.setLevel(Level.ALL);
        ch.setLevel(Level.ALL);
        logger.addHandler(ch);

        executor = Executors.newCachedThreadPool();

        String root = System.getProperty("test.src", ".") + "/docs";
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        httpServer = HttpTestServer.create(Version.HTTP_1_1, null, executor);
        httpsServer = HttpTestServer.create(Version.HTTP_1_1, ctx, executor);
        HttpTestHandler h = new HttpTestFileServerHandler(root);

        HttpTestContext c1 = httpServer.createContext("/files", h);
        HttpTestContext c2 = httpsServer.createContext("/files", h);
        HttpTestContext c3 = httpServer.createContext("/echo", new EchoHandler());
        redirectHandler = new RedirectHandler("/redirect");
        redirectHandlerSecure = new RedirectHandler("/redirect");
        HttpTestContext c4 = httpServer.createContext("/redirect", redirectHandler);
        HttpTestContext c41 = httpsServer.createContext("/redirect", redirectHandlerSecure);
        HttpTestContext c5 = httpsServer.createContext("/echo", new EchoHandler());
        HttpTestContext c6 = httpServer.createContext("/keepalive", new KeepAliveHandler());
        redirectErrorHandler = new RedirectErrorHandler("/redirecterror");
        redirectErrorHandlerSecure = new RedirectErrorHandler("/redirecterror");
        HttpTestContext c7 = httpServer.createContext("/redirecterror", redirectErrorHandler);
        HttpTestContext c71 = httpsServer.createContext("/redirecterror", redirectErrorHandlerSecure);
        delayHandler = new DelayHandler();
        HttpTestContext c8 = httpServer.createContext("/delay", delayHandler);
        HttpTestContext c81 = httpsServer.createContext("/delay", delayHandler);

        httpServer.start();
        httpsServer.start();

        port = httpServer.getAddress().getPort();
        System.out.println("HTTP server port = " + port);
        httpsport = httpsServer.getAddress().getPort();
        System.out.println("HTTPS server port = " + httpsport);
        httproot = "http://" + makeServerAuthority(httpServer.getAddress()) + "/";
        httpsroot = "https://" + makeServerAuthority(httpsServer.getAddress()) + "/";

        proxy = new ProxyServer(0, false);
        proxyPort = proxy.getPort();
        System.out.println("Proxy port = " + proxyPort);
    }

    private static String makeServerAuthority(final InetSocketAddress addr) {
        final String hostIP = addr.getAddress().getHostAddress();
        // escape for ipv6
        final String h = hostIP.contains(":") ? "[" + hostIP + "]" : hostIP;
        return h + ":" + addr.getPort();
    }

    public static void stop() throws IOException {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (httpsServer != null) {
            httpsServer.stop();
        }
        if (proxy != null) {
            proxy.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    static class EchoHandler extends HttpServerAdapters.HttpTestEchoHandler {
        @Override
        protected boolean useXFixed() {
            return true;
        }
    }

    static class RedirectErrorHandler implements HttpTestHandler {

        String root;
        volatile int count = 1;

        RedirectErrorHandler(String root) {
            this.root = root;
        }

        synchronized int count() {
            return count;
        }

        synchronized void increment() {
            count++;
        }

        @Override
        public synchronized void handle(HttpTestExchange t)
                throws IOException {
            byte[] buf = new byte[2048];
            try (InputStream is = t.getRequestBody()) {
                while (is.read(buf) != -1) ;
            }

            var map = t.getResponseHeaders();
            String redirect = root + "/foo/" + count;
            increment();
            map.addHeader("Location", redirect);
            t.sendResponseHeaders(301, HttpTestExchange.RSPBODY_EMPTY);
            t.close();
        }
    }

    static class RedirectHandler implements HttpTestHandler {

        String root;
        volatile int count = 0;

        RedirectHandler(String root) {
            this.root = root;
        }

        @Override
        public synchronized void handle(HttpTestExchange t)
                throws IOException {
            byte[] buf = new byte[2048];
            try (InputStream is = t.getRequestBody()) {
                while (is.read(buf) != -1) ;
            }

            var map = t.getResponseHeaders();

            if (count++ < 1) {
                map.addHeader("Location", root + "/foo/" + count);
            } else {
                map.addHeader("Location", SmokeTest.midSizedFilename);
            }
            t.sendResponseHeaders(301, HttpTestExchange.RSPBODY_EMPTY);
            t.close();
        }

        int count() {
            return count;
        }

        void reset() {
            count = 0;
        }
    }

    static class KeepAliveHandler implements HttpTestHandler {

        volatile int counter = 0;
        HashSet<Integer> portSet = new HashSet<>();
        volatile int[] ports = new int[4];

        void sleep(int n) {
            try {
                Thread.sleep(n);
            } catch (InterruptedException e) {
            }
        }

        @Override
        public synchronized void handle(HttpTestExchange t)
                throws IOException {
            int remotePort = t.getRemoteAddress().getPort();
            String result = "OK";

            int n = counter++;
            /// First test
            if (n < 4) {
                ports[n] = remotePort;
            }
            if (n == 3) {
                // check all values in ports[] are the same
                if (ports[0] != ports[1] || ports[2] != ports[3]
                        || ports[0] != ports[2]) {
                    result = "Error " + Integer.toString(n);
                    System.out.println(result);
                }
            }
            // Second test
            if (n >= 4 && n < 8) {
                // delay to ensure ports are different
                sleep(500);
                ports[n - 4] = remotePort;
            }
            if (n == 7) {
                // should be all different
                if (ports[0] == ports[1] || ports[2] == ports[3]
                        || ports[0] == ports[2]) {
                    result = "Error " + Integer.toString(n);
                    System.out.println(result);
                    System.out.printf("Ports: %d, %d, %d, %d\n",
                                      ports[0], ports[1], ports[2], ports[3]);
                }
                // setup for third test
                for (int i = 0; i < 4; i++) {
                    portSet.add(ports[i]);
                }
            }
            // Third test
            if (n > 7) {
                // just check that port is one of the ones in portSet
                if (!portSet.contains(remotePort)) {
                    System.out.println("UNEXPECTED REMOTE PORT " + remotePort);
                    result = "Error " + Integer.toString(n);
                    System.out.println(result);
                }
            }
            byte[] buf = new byte[2048];

            try (InputStream is = t.getRequestBody()) {
                while (is.read(buf) != -1) ;
            }
            byte[] bytes = result.getBytes("US-ASCII");
            t.sendResponseHeaders(200, HttpTestExchange.fixedRsp(bytes.length));
            OutputStream o = t.getResponseBody();
            o.write(bytes);
            t.close();
        }
    }

    static class DelayHandler implements HttpTestHandler {

        CyclicBarrier bar1 = new CyclicBarrier(2);
        CyclicBarrier bar2 = new CyclicBarrier(2);
        CyclicBarrier bar3 = new CyclicBarrier(2);

        CyclicBarrier barrier1() {
            return bar1;
        }

        CyclicBarrier barrier2() {
            return bar2;
        }

        @Override
        public synchronized void handle(HttpTestExchange he) throws IOException {
            try(InputStream is = he.getRequestBody()) {
                is.readAllBytes();
                bar1.await();
                bar2.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new IOException(e);
            }
            he.sendResponseHeaders(200, HttpTestExchange.RSPBODY_EMPTY); // will probably fail
            he.close();
        }
    }
}
