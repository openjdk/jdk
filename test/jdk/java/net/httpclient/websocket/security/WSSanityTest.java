/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic sanity checks for WebSocket URI from the Builder
 * @compile ../DummyWebSocketServer.java ../../ProxyServer.java
 * @run testng/othervm WSSanityTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class WSSanityTest {

    URI wsURI;
    DummyWebSocketServer webSocketServer;
    InetSocketAddress proxyAddress;

    @BeforeTest
    public void setup() throws Exception {
        ProxyServer proxyServer = new ProxyServer(0, true);
        proxyAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(),
                                             proxyServer.getPort());
        webSocketServer = new DummyWebSocketServer();
        webSocketServer.open();
        wsURI = webSocketServer.getURI();

        System.out.println("Proxy Server: " + proxyAddress);
        System.out.println("DummyWebSocketServer: " + wsURI);
    }

    @AfterTest
    public void teardown() {
        webSocketServer.close();
    }

    static class NoOpListener implements WebSocket.Listener {}
    static final WebSocket.Listener noOpListener = new NoOpListener();

    interface ExceptionAction<T> {
        T run() throws Exception;
    }

    @DataProvider(name = "passingScenarios")
    public Object[][] passingScenarios() {
        HttpClient noProxyClient = HttpClient.newHttpClient();
        return new Object[][]{
            { (ExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "0"  /* for log file identification */ },

            { (ExceptionAction<?>)() -> {
                URI uriWithPath = wsURI.resolve("/path/x");
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(uriWithPath, noOpListener).get().abort();
                 return null; },
              "1" },

            { (ExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // header
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "2" },

            { (ExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // headers
                              .header("B-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "3" },

            // client with a DIRECT proxy
            { (ExceptionAction<?>)() -> {
                 ProxySelector ps = ProxySelector.of(null);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "4" },

            // client with a SOCKS proxy! ( expect implementation to ignore SOCKS )
            { (ExceptionAction<?>)() -> {
                 ProxySelector ps = new ProxySelector() {
                     @Override public List<Proxy> select(URI uri) {
                         return List.of(new Proxy(Proxy.Type.SOCKS, proxyAddress)); }
                     @Override
                     public void connectFailed(URI uri, SocketAddress sa, IOException ioe) { }
                 };
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "5" },

            // client with an HTTP/HTTPS proxy
            { (ExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              "6" },

            { (ExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 CountingProxySelector ps = CountingProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 assertEquals(ps.count(), 1);  // ps.select only invoked once
                 return null; },
              "7" },

        };
    }

    @Test(dataProvider = "passingScenarios")
    public void testScenarios(ExceptionAction<?> action, String dataProviderId)
        throws Exception
    {
        action.run();
    }

    /**
     * A Proxy Selector that wraps a ProxySelector.of(), and counts the number
     * of times its select method has been invoked. This can be used to ensure
     * that the Proxy Selector is invoked only once per WebSocket.Builder::buildAsync
     * invocation.
     */
    static class CountingProxySelector extends ProxySelector {
        private final ProxySelector proxySelector;
        private volatile int count; // 0
        private CountingProxySelector(InetSocketAddress proxyAddress) {
            proxySelector = ProxySelector.of(proxyAddress);
        }

        public static CountingProxySelector of(InetSocketAddress proxyAddress) {
            return new CountingProxySelector(proxyAddress);
        }

        int count() { return count; }

        @Override
        public List<Proxy> select(URI uri) {
            System.out.println("PS: uri");
            Throwable t = new Throwable();
            t.printStackTrace(System.out);
            count++;
            return proxySelector.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            proxySelector.connectFailed(uri, sa, ioe);
        }
    }
}
