/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8368157
 * @summary DefaultProxySelector should handle ws/wss like http/https
 * @run main/othervm WsProxySelector
 */

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

public class WsProxySelector {

    public static void main(String[] args) throws Exception {
        testWsUsesHttpProxy();
        testWssUsesHttpsProxy();
        testWsRespectsNonProxyHosts();
        testWssRespectsNonProxyHosts();
        testWsNoProxyConfigured();
        testMixedCaseScheme();
        testMixedCaseSchemeWss();
        testWsSocksProxyFallback();
        testWssSocksProxyFallback();
        testStaticProxySelectorWs();
        testStaticProxySelectorWss();
        System.out.println("All tests passed.");
    }

    // ws:// should use http.proxyHost/http.proxyPort
    static void testWsUsesHttpProxy() {
        withProperties(() -> {
            System.setProperty("http.proxyHost", "proxy.example.com");
            System.setProperty("http.proxyPort", "8080");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("ws://example.com/chat"));

            assertProxied(proxies, "proxy.example.com", 8080,
                    "ws should use http proxy settings");
        });
    }

    // wss:// should use https.proxyHost/https.proxyPort
    static void testWssUsesHttpsProxy() {
        withProperties(() -> {
            System.setProperty("https.proxyHost", "proxy.example.com");
            System.setProperty("https.proxyPort", "8443");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("wss://example.com/chat"));

            assertProxied(proxies, "proxy.example.com", 8443,
                    "wss should use https proxy settings");
        });
    }

    // ws:// should respect http.nonProxyHosts
    static void testWsRespectsNonProxyHosts() {
        withProperties(() -> {
            System.setProperty("http.proxyHost", "proxy.example.com");
            System.setProperty("http.nonProxyHosts", "example.com");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("ws://example.com/chat"));

            assertDirect(proxies,
                    "ws should respect http.nonProxyHosts");
        });
    }

    // wss:// shares http.nonProxyHosts with https
    static void testWssRespectsNonProxyHosts() {
        withProperties(() -> {
            System.setProperty("https.proxyHost", "proxy.example.com");
            System.setProperty("http.nonProxyHosts", "example.com");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("wss://example.com/chat"));

            assertDirect(proxies,
                    "wss should respect http.nonProxyHosts");
        });
    }

    // ws:// with no proxy configured should return DIRECT
    static void testWsNoProxyConfigured() {
        withProperties(() -> {
            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("ws://example.com/chat"));

            assertDirect(proxies,
                    "ws with no proxy should be DIRECT");
        });
    }

    // WS and Wss (mixed case) should work the same
    static void testMixedCaseScheme() {
        withProperties(() -> {
            System.setProperty("http.proxyHost", "proxy.example.com");
            System.setProperty("http.proxyPort", "8080");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("WS://example.com/chat"));

            // URI.getScheme() preserves case, but DefaultProxySelector
            // should handle it case-insensitively
            assertProxied(proxies, "proxy.example.com", 8080,
                    "WS (uppercase) should use http proxy settings");
        });
    }

    // Wss (mixed case) should work the same as wss
    static void testMixedCaseSchemeWss() {
        withProperties(() -> {
            System.setProperty("https.proxyHost", "proxy.example.com");
            System.setProperty("https.proxyPort", "8443");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("Wss://example.com/chat"));

            assertProxied(proxies, "proxy.example.com", 8443,
                    "Wss (mixed case) should use https proxy settings");
        });
    }

    // ws:// should fall back to SOCKS when only socksProxy is configured
    static void testWsSocksProxyFallback() {
        withProperties(() -> {
            System.setProperty("socksProxyHost", "socks.example.com");
            System.setProperty("socksProxyPort", "1080");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("ws://example.com/chat"));

            if (proxies.size() != 1
                    || proxies.get(0).type() != Proxy.Type.SOCKS) {
                throw new AssertionError(
                        "ws should fall back to SOCKS proxy, got: "
                                + proxies);
            }
        });
    }

    // wss:// should also fall back to SOCKS
    static void testWssSocksProxyFallback() {
        withProperties(() -> {
            System.setProperty("socksProxyHost", "socks.example.com");
            System.setProperty("socksProxyPort", "1080");

            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(URI.create("wss://example.com/chat"));

            if (proxies.size() != 1
                    || proxies.get(0).type() != Proxy.Type.SOCKS) {
                throw new AssertionError(
                        "wss should fall back to SOCKS proxy, got: "
                                + proxies);
            }
        });
    }

    // ProxySelector.of(addr) should also handle ws:// URIs
    static void testStaticProxySelectorWs() {
        InetSocketAddress addr =
                InetSocketAddress.createUnresolved(
                        "proxy.example.com", 8080);
        ProxySelector ps = ProxySelector.of(addr);

        List<Proxy> proxies =
                ps.select(URI.create("ws://example.com/chat"));

        assertProxied(proxies, "proxy.example.com", 8080,
                "ProxySelector.of() should proxy ws URIs");
    }

    // ProxySelector.of(addr) should also handle wss:// URIs
    static void testStaticProxySelectorWss() {
        InetSocketAddress addr =
                InetSocketAddress.createUnresolved(
                        "proxy.example.com", 8443);
        ProxySelector ps = ProxySelector.of(addr);

        List<Proxy> proxies =
                ps.select(URI.create("wss://example.com/chat"));

        assertProxied(proxies, "proxy.example.com", 8443,
                "ProxySelector.of() should proxy wss URIs");
    }

    // --- helpers ---

    static void assertProxied(List<Proxy> proxies, String host,
                              int port, String msg) {
        if (proxies.size() != 1
                || proxies.get(0).type() == Proxy.Type.DIRECT) {
            throw new AssertionError(msg + ", got: " + proxies);
        }
        InetSocketAddress sa =
                (InetSocketAddress) proxies.get(0).address();
        if (!sa.getHostString().equals(host) || sa.getPort() != port) {
            throw new AssertionError(msg
                    + ", expected " + host + ":" + port
                    + " but got " + sa);
        }
    }

    static void assertDirect(List<Proxy> proxies, String msg) {
        if (proxies.size() != 1
                || proxies.get(0).type() != Proxy.Type.DIRECT) {
            throw new AssertionError(msg + ", got: " + proxies);
        }
    }

    static void withProperties(Runnable test) {
        String[] keys = {
            "http.proxyHost", "http.proxyPort",
            "https.proxyHost", "https.proxyPort",
            "http.nonProxyHosts",
            "proxyHost", "proxyPort",
            "socksProxyHost", "socksProxyPort",
            "java.net.useSystemProxies"
        };
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            saved[i] = System.getProperty(keys[i]);
            System.clearProperty(keys[i]);
        }
        try {
            test.run();
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (saved[i] != null) {
                    System.setProperty(keys[i], saved[i]);
                } else {
                    System.clearProperty(keys[i]);
                }
            }
        }
    }
}
