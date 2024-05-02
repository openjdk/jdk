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
 * @library /test/lib
 * @bug 8291226 8291638 8330523
 * @modules java.base/sun.net.www.http:+open
 *          java.base/sun.net.www.protocol.http:+open
 * @run main/othervm KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=100 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.proxy=200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=100 -Dhttp.keepAlive.time.proxy=200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=-100 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.proxy=-200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=-100 -Dhttp.keepAlive.time.proxy=-200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=0 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.proxy=0 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=0 -Dhttp.keepAlive.time.proxy=0 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=0 -Dhttp.keepAlive.time.proxy=-200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=-100 -Dhttp.keepAlive.time.proxy=0 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=100 -Dhttp.keepAlive.time.proxy=0 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=0 -Dhttp.keepAlive.time.proxy=200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=100 -Dhttp.keepAlive.time.proxy=-200 KeepAliveTest
 * @run main/othervm -Dhttp.keepAlive.time.server=-100 -Dhttp.keepAlive.time.proxy=200 KeepAliveTest
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Phaser;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdk.test.lib.net.URIBuilder;

import sun.net.www.http.HttpClient;
import sun.net.www.http.KeepAliveCache;
import sun.net.www.protocol.http.HttpURLConnection;

public class KeepAliveTest {

    /*
     * This test covers a matrix of 10 server scenarios and 16 client scenarios.
     */
    private static final Logger logger = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");

    private static final String NEW_LINE = "\r\n";

    private static final String CONNECTION_KEEP_ALIVE_ONLY = "Connection: keep-alive";
    private static final String PROXY_CONNECTION_KEEP_ALIVE_ONLY = "Proxy-Connection: keep-alive";
    private static final String KEEP_ALIVE_TIMEOUT_NEG = "Keep-alive: timeout=-20";
    private static final String KEEP_ALIVE_TIMEOUT_ZERO = "Keep-alive: timeout=0";
    private static final String KEEP_ALIVE_TIMEOUT = "Keep-alive: timeout=20";
    private static final String KEEP_ALIVE_PROXY_TIMEOUT = "Keep-alive: timeout=120";
    private static final String CONNECTION_KEEP_ALIVE_WITH_TIMEOUT = CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT;

    private static final String[] serverHeaders = {
        null,
        CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE,
        CONNECTION_KEEP_ALIVE_WITH_TIMEOUT + NEW_LINE,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_PROXY_TIMEOUT + NEW_LINE,
        CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG + NEW_LINE,
        CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO + NEW_LINE,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_NEG + NEW_LINE,
        PROXY_CONNECTION_KEEP_ALIVE_ONLY + NEW_LINE + KEEP_ALIVE_TIMEOUT_ZERO + NEW_LINE
    };

    private static KeepAliveCache keepAliveCache;

    private static Constructor<?> keepAliveKeyClassconstructor;

    // variables set by server thread
    private volatile int serverPort;
    private volatile boolean isProxySet;

    private static final Phaser serverGate = new Phaser(2);

    private void readAll(Socket s) throws IOException {
        byte[] buf = new byte[128];
        int c;
        String request = "";
        InputStream is = s.getInputStream();
        while ((c = is.read(buf)) > 0) {
            request += new String(buf, 0, c, StandardCharsets.US_ASCII);
            if (request.contains("\r\n\r\n")) {
                return;
            }
        }
        if (c == -1) {
            throw new IOException("Socket closed");
        }
    }

    private void executeServer(int scenarioNumber) {
        String scenarioHeaders = serverHeaders[scenarioNumber];
        if (scenarioHeaders != null) {
            // isProxySet should be set before Server is moved to Listen State.
            isProxySet = scenarioHeaders.contains("Proxy");
        }
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            serverPort = serverSocket.getLocalPort();
            serverGate.arrive();

            // Server will be waiting for clients to connect.
            try (Socket socket = serverSocket.accept()) {
                readAll(socket);
                try (OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream())) {
                    String BODY = "SERVER REPLY: Hello world";
                    String CLEN = "Content-Length: " + BODY.length() + NEW_LINE;

                    // send common headers
                    out.write("HTTP/1.1 200 OK\r\n");
                    out.write("Content-Type: text/plain; charset=iso-8859-1\r\n");

                    // set scenario headers
                    if (scenarioHeaders != null) {
                        out.write(scenarioHeaders);
                    }

                    // send content
                    out.write(CLEN);
                    out.write(NEW_LINE);
                    out.write(BODY);
                }
            }
        } catch (IOException ioe) {
            throw new RuntimeException("IOException in server thread", ioe);
        }
    }

    private void fetchInfo(int expectedValue, HttpURLConnection httpUrlConnection) throws Exception {
        Object expectedKeepAliveKey = keepAliveKeyClassconstructor.newInstance(httpUrlConnection.getURL(), null);
        Object clientVectorObjectInMap = keepAliveCache.get(expectedKeepAliveKey);
        System.out.println("ClientVector for KeepAliveKey:" + clientVectorObjectInMap);
        HttpClient httpClientCached = keepAliveCache.get(httpUrlConnection.getURL(), null);
        System.out.println("HttpClient in Cache:" + httpClientCached);

        if (httpClientCached != null) {
            System.out.println("KeepingAlive:" + httpClientCached.isKeepingAlive());
            System.out.println("UsingProxy:" + httpClientCached.getUsingProxy());
            System.out.println("ProxiedHost:" + httpClientCached.getProxyHostUsed());
            System.out.println("ProxiedPort:" + httpClientCached.getProxyPortUsed());
            Class<?> clientVectorClass = Class.forName("sun.net.www.http.KeepAliveCache$ClientVector");
            Field napField = clientVectorClass.getDeclaredField("nap");
            napField.setAccessible(true);
            int napValue = (int) napField.get(clientVectorObjectInMap);
            int actualValue = napValue / 1000;
            if (expectedValue == actualValue) {
                System.out.printf("Cache time:%d\n", actualValue);
            } else {
                throw new RuntimeException("Sleep time of " + actualValue + " not expected (" + expectedValue + ")");
            }
        } else {
            if (expectedValue == 0) {
                System.out.println("Connection not cached.");
            } else {
                throw new RuntimeException("Connection was not cached although expected with sleep time of:" + expectedValue);
            }
        }
    }

    private void connectToServerURL(int expectedValue) throws Exception {
        // wait until ServerSocket moves to listening state.
        serverGate.arriveAndAwaitAdvance();
        URL url = URIBuilder.newBuilder().scheme("http").loopback().port(serverPort).toURL();
        System.out.println("connecting to server URL: " + url + ", isProxySet: " + isProxySet);
        HttpURLConnection httpUrlConnection = null;

        // isProxySet is set to true when Expected Server Response contains Proxy-Connection header.
        if (isProxySet) {
            httpUrlConnection = (HttpURLConnection) url
                .openConnection(new Proxy(Type.HTTP, new InetSocketAddress("localhost", serverPort)));
        } else {
            httpUrlConnection = (HttpURLConnection) url.openConnection();
        }

        try (InputStreamReader inputStreamReader = new InputStreamReader(httpUrlConnection.getInputStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                System.out.println(line);
            }
        }
        for (Entry<String, List<String>> header : httpUrlConnection.getHeaderFields().entrySet()) {
            System.out.println(header.getKey() + "=" + header.getValue());
        }
        fetchInfo(expectedValue, httpUrlConnection);
    }

    private int getExpectedCachingValue(int serverScenario) {
        if (serverScenario == 2) {
            // Connection: keep-alive && Keep-alive: timeout=20
            //
            // server side keep-alive timeout is what counts here
            return 20;
        } else if (serverScenario == 3 || serverScenario == 4 || serverScenario == 8) {
            // Proxy-Connection: keep-alive
            // Connection:keep-alive && Proxy-connection:keep-alive
            // Proxy-Connection:keep-alive && Keep-alive:timeout=-20
            //
            // Proxy-connection:keep-alive is set, timeout could be invalid -> value of http.keepAlive.time.proxy or default of 60
            int httpKeepAliveTimeProxy;
            try {
                httpKeepAliveTimeProxy = Integer.valueOf(System.getProperty("http.keepAlive.time.proxy"));
            } catch (NumberFormatException e) {
                httpKeepAliveTimeProxy = -1;
            }
            return httpKeepAliveTimeProxy < 0 ? 60 : httpKeepAliveTimeProxy;
        } else if (serverScenario == 5) {
            // Proxy-connection:keep-alive && Keep-alive:timeout=120
            //
            // server side keep-alive timeout is what counts here
            return 120;
        } else if (serverScenario == 7 || serverScenario == 9) {
            // Connection: keep-alive && Keep-alive: timeout=0
            // Proxy-Connection:keep-alive && Keep-alive:timeout=0
            //
            // no caching
            return 0;
        } else {
            // No server parameters
            // Connection: keep-alive
            // Connection: keep-alive && Keep-alive: timeout=-20
            //
            // Nothing or Connection:keep-alive is set, timeout could be invalid -> value of http.keepAlive.time.server or default of 5
            int httpKeepAliveTimeServer;
            try {
                httpKeepAliveTimeServer = Integer.valueOf(System.getProperty("http.keepAlive.time.server"));
            } catch (NumberFormatException e) {
                httpKeepAliveTimeServer = -1;
            }
            return httpKeepAliveTimeServer < 0 ? 5 : httpKeepAliveTimeServer;
        }
    }

    private void runScenario(int scenarioNumber) throws Exception {
        int expectedValue = getExpectedCachingValue(scenarioNumber);
        System.out.println("Expecting Cache Time of " + expectedValue + " for server headers:");
        if (serverHeaders[scenarioNumber] == null) {
            System.out.println();
        } else {
            System.out.print(serverHeaders[scenarioNumber]);
        }
        Thread server = Thread.ofPlatform().start(() -> executeServer(scenarioNumber));
        connectToServerURL(expectedValue);
        server.join();
        System.out.println();
    }

    private static void initialize() throws Exception {
        System.clearProperty("http.proxyPort");
        System.clearProperty("http.proxyHost");
        System.setProperty("java.net.useSystemProxies", "false");

        Field keepAliveField = sun.net.www.http.HttpClient.class.getDeclaredField("kac");
        keepAliveField.setAccessible(true);
        keepAliveCache = (KeepAliveCache) keepAliveField.get(null);
        System.out.println("KeepAliveCache: " + keepAliveCache);
        keepAliveKeyClassconstructor = Class.forName("sun.net.www.http.KeepAliveKey").getDeclaredConstructors()[0];
        keepAliveKeyClassconstructor.setAccessible(true);

        logger.setLevel(Level.FINEST);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.FINEST);
        logger.addHandler(h);

        System.out.println("Client properties: http.keepAlive.time.server=" + System.getProperty("http.keepAlive.time.server") +
                ", http.keepAlive.time.proxy=" + System.getProperty("http.keepAlive.time.proxy"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage:java KeepAliveTest.java <scenarioNumber>");
        } else if (args.length == 1) {
            // an individual test scenario
            try {
                int scenarioNumber = Integer.valueOf(args[0]);
                if (scenarioNumber < 0 || scenarioNumber > 9) {
                    throw new IllegalArgumentException("Scenario " + scenarioNumber + " does not exist");
                }
                initialize();
                new KeepAliveTest().runScenario(scenarioNumber);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Scenario must be a number, got " + args[0]);
            }
        } else {
            // all server scenarios
            initialize();
            for (int i = 0; i < 10; i++) {
                new KeepAliveTest().runScenario(i);
            }
        }
    }
}
