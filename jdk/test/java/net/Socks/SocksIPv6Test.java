/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7100957
 * @modules jdk.httpserver
 * @summary Java doesn't correctly handle the SOCKS protocol when used over IPv6.
 * @run testng SocksIPv6Test
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.Proxy;
import java.lang.Override;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import com.sun.net.httpserver.*;
import java.io.BufferedWriter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class SocksIPv6Test {

    private HttpServer server;
    private SocksServer socks;
    private String response = "Hello.";
    private static boolean shouldRun = false;

    @BeforeClass
    public void setUp() throws Exception {
        shouldRun = ensureInet6AddressFamily() && ensureIPv6OnLoopback();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(200, response.length());
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(ex.getResponseBody(), "UTF-8"))) {
                writer.write(response);
            }
            ex.close();
        });
        server.start();

        socks = new SocksServer(0, false);
        socks.addUser("user", "pass");
        socks.start();

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                return new java.net.PasswordAuthentication(
                        "user", "pass".toCharArray());
            }
        });
    }

    private boolean ensureIPv6OnLoopback() throws Exception {
        boolean ipv6 = false;

        List<NetworkInterface> nics = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nic : nics) {
            if (!nic.isLoopback()) {
                continue;
            }
            List<InetAddress> addrs = Collections.list(nic.getInetAddresses());
            for (InetAddress addr : addrs) {
                if (addr instanceof Inet6Address) {
                    ipv6 = true;
                    break;
                }
            }
        }
        if (!ipv6)
            System.out.println("IPv6 is not enabled on loopback. Skipping test suite.");
        return ipv6;
    }

    private boolean ensureInet6AddressFamily() throws IOException {
        try (ServerSocket s = new ServerSocket()) {
            s.bind(new InetSocketAddress("::1", 0));
            return true;
        } catch (SocketException e) {
            System.out.println("Inet 6 address family is not available. Skipping test suite.");
        }
        return false;
    }

    @Test(groups = "unit")
    public void testSocksOverIPv6() throws Exception {
        if (!shouldRun) return;

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("::1",
                socks.getPort()));
        URL url = new URL("http://[::1]:" + server.getAddress().getPort());
        java.net.URLConnection conn = url.openConnection(proxy);
        String actual = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            actual = reader.readLine();
        }
        assertEquals(actual, response);
    }

    @Test(groups = "unit")
    public void testSocksOverIPv6Hostname() throws Exception {
        if (!shouldRun) return;

        String ipv6Hostname = InetAddress.getByName("::1").getHostName();
        String ipv4Hostname = InetAddress.getByName("127.0.0.1").getHostName();

        if (ipv6Hostname.equals(InetAddress.getByName("::1").getHostAddress())) {
            System.out.println("Unable to get the hostname of the IPv6 loopback "
                    + "address. Skipping test case.");
            return;
        }

        if (ipv6Hostname.equals(ipv4Hostname)) {
            System.out.println("IPv6 and IPv4 loopback addresses map to the"
                    + " same hostname. Skipping test case.");
            return;
        }

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(ipv6Hostname,
                socks.getPort()));
        URL url = new URL("http://" + ipv6Hostname + ":" + server.getAddress().getPort());
        java.net.URLConnection conn = url.openConnection(proxy);
        String actual = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            actual = reader.readLine();
        }
        assertEquals(actual, response);
    }

    @AfterClass
    public void tearDown() {
        if (server != null) {
            server.stop(1);
        }
        if (socks != null) {
            socks.terminate();
        }
    }
}
