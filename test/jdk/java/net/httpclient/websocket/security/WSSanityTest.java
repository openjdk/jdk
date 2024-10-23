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
 * @run testng/othervm WSURLPermissionTest
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLPermission;
import java.security.PrivilegedExceptionAction;
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

    @DataProvider(name = "passingScenarios")
    public Object[][] passingScenarios() {
        HttpClient noProxyClient = HttpClient.newHttpClient();
        return new Object[][]{
            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                       // no actions
              new URLPermission[] { new URLPermission(wsURI.toString()) },
              "0"  /* for log file identification */ },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                       // scheme wildcard
              new URLPermission[] { new URLPermission("ws://*") },
              "0.1" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                       // port wildcard
              new URLPermission[] { new URLPermission("ws://"+wsURI.getHost()+":*") },
              "0.2" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // empty actions
              new URLPermission[] { new URLPermission(wsURI.toString(), "") },
              "1" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                         // colon
              new URLPermission[] { new URLPermission(wsURI.toString(), ":") },
              "2" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // wildcard
              new URLPermission[] { new URLPermission(wsURI.toString(), "*:*") },
              "3" },

            // WS permission checking is agnostic of method, any/none will do
            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // specific method
              new URLPermission[] { new URLPermission(wsURI.toString(), "GET") },
              "3.1" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // specific method
              new URLPermission[] { new URLPermission(wsURI.toString(), "POST") },
              "3.2" },

            { (PrivilegedExceptionAction<?>)() -> {
                URI uriWithPath = wsURI.resolve("/path/x");
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(uriWithPath, noOpListener).get().abort();
                 return null; },                                       // path
              new URLPermission[] { new URLPermission(wsURI.resolve("/path/x").toString()) },
              "4" },

            { (PrivilegedExceptionAction<?>)() -> {
                URI uriWithPath = wsURI.resolve("/path/x");
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(uriWithPath, noOpListener).get().abort();
                 return null; },                                       // same dir wildcard
              new URLPermission[] { new URLPermission(wsURI.resolve("/path/*").toString()) },
              "5" },

            { (PrivilegedExceptionAction<?>)() -> {
                URI uriWithPath = wsURI.resolve("/path/x");
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(uriWithPath, noOpListener).get().abort();
                 return null; },                                       // recursive
              new URLPermission[] { new URLPermission(wsURI.resolve("/path/-").toString()) },
              "6" },

            { (PrivilegedExceptionAction<?>)() -> {
                URI uriWithPath = wsURI.resolve("/path/x");
                 noProxyClient.newWebSocketBuilder()
                              .buildAsync(uriWithPath, noOpListener).get().abort();
                 return null; },                                       // recursive top
              new URLPermission[] { new URLPermission(wsURI.resolve("/-").toString()) },
              "7" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // header
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] { new URLPermission(wsURI.toString(), ":A-Header") },
              "8" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // header
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // wildcard
              new URLPermission[] { new URLPermission(wsURI.toString(), ":*") },
              "9" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // headers
                              .header("B-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] { new URLPermission(wsURI.toString(), ":A-Header,B-Header") },
              "10" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // headers
                              .header("B-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // wildcard
              new URLPermission[] { new URLPermission(wsURI.toString(), ":*") },
              "11" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // headers
                              .header("B-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // wildcards
              new URLPermission[] { new URLPermission(wsURI.toString(), "*:*") },
              "12" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // multi-value
                              .header("A-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // wildcard
              new URLPermission[] { new URLPermission(wsURI.toString(), ":*") },
              "13" },

            { (PrivilegedExceptionAction<?>)() -> {
                 noProxyClient.newWebSocketBuilder()
                              .header("A-Header", "A-Value")  // multi-value
                              .header("A-Header", "B-Value")  // headers
                              .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },                                        // single grant
              new URLPermission[] { new URLPermission(wsURI.toString(), ":A-Header") },
              "14" },

            // client with a DIRECT proxy
            { (PrivilegedExceptionAction<?>)() -> {
                 ProxySelector ps = ProxySelector.of(null);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] { new URLPermission(wsURI.toString()) },
              "15" },

            // client with a SOCKS proxy! ( expect implementation to ignore SOCKS )
            { (PrivilegedExceptionAction<?>)() -> {
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
              new URLPermission[] { new URLPermission(wsURI.toString()) },
              "16" },

            // client with a HTTP/HTTPS proxy
            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] {
                    new URLPermission(wsURI.toString()),            // CONNECT action string
                    new URLPermission("socket://"+proxyAddress.getHostName()
                                      +":"+proxyAddress.getPort(), "CONNECT")},
              "17" },

            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] {
                    new URLPermission(wsURI.toString()),            // no action string
                    new URLPermission("socket://"+proxyAddress.getHostName()
                                      +":"+proxyAddress.getPort())},
              "18" },

            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] {
                    new URLPermission(wsURI.toString()),            // wildcard headers
                    new URLPermission("socket://"+proxyAddress.getHostName()
                                      +":"+proxyAddress.getPort(), "CONNECT:*")},
              "19" },

            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 CountingProxySelector ps = CountingProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 assertEquals(ps.count(), 1);  // ps.select only invoked once
                 return null; },
              new URLPermission[] {
                    new URLPermission(wsURI.toString()),            // empty headers
                    new URLPermission("socket://"+proxyAddress.getHostName()
                                      +":"+proxyAddress.getPort(), "CONNECT:")},
              "20" },

            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] {
                    new URLPermission(wsURI.toString()),
                    new URLPermission("socket://*")},               // wildcard socket URL
              "21" },

            { (PrivilegedExceptionAction<?>)() -> {
                 assert proxyAddress != null;
                 ProxySelector ps = ProxySelector.of(proxyAddress);
                 HttpClient client = HttpClient.newBuilder().proxy(ps).build();
                 client.newWebSocketBuilder()
                       .buildAsync(wsURI, noOpListener).get().abort();
                 return null; },
              new URLPermission[] {
                    new URLPermission("ws://*"),                    // wildcard ws URL
                    new URLPermission("socket://*")},               // wildcard socket URL
              "22" },

        };
    }

    @Test(dataProvider = "passingScenarios")
    public void testWithNoSecurityManager(PrivilegedExceptionAction<?> action,
                                          URLPermission[] unused,
                                          String dataProviderId)
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
