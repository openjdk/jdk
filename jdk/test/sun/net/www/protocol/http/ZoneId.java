/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8027308
 * @summary  verifies that HttpURLConnection does not send the zone id in the
 *           'Host' field of the header:
 *              Host: [fe80::a00:27ff:aaaa:aaaa] instead of
 *              Host: [fe80::a00:27ff:aaaa:aaaa%eth0]"
 */

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ZoneId {

    public static void main(String[] args) throws Exception {

        InetAddress address = getAppropriateIPv6Address();

        if (address == null) {
            System.out.println(
                    "The test will be skipped as not a single " +
                    "appropriate IPv6 address was found on this machine");
            return;
        }
        String ip6_literal = address.getHostAddress();

        System.out.println("Found an appropriate IPv6 address: " + address);

        System.out.println("Starting http server...");
        HttpServer server =
                HttpServer.create(new InetSocketAddress(address, 0), 0);
        CompletableFuture<Headers> headers = new CompletableFuture<>();
        server.createContext("/", createCapturingHandler(headers));
        server.start();
        System.out.println("Started at " + server.getAddress());
        try {
            String spec = "http://[" + address.getHostAddress() + "]:" + server.getAddress().getPort();
            System.out.println("Client is connecting to: " + spec);
            URLConnection urlConnection = new URL(spec).openConnection();
            ((sun.net.www.protocol.http.HttpURLConnection) urlConnection)
                    .getResponseCode();
        } finally {
            System.out.println("Shutting down the server...");
            server.stop(0);
        }

        int idx = ip6_literal.lastIndexOf('%');
        String ip6_address = ip6_literal.substring(0, idx);
        List<String> hosts = headers.get().get("Host");

        System.out.println("Host: " + hosts);

        if (hosts.size() != 1 || hosts.get(0).contains("%") ||
                                !hosts.get(0).contains(ip6_address)) {
            throw new RuntimeException("FAIL");
        }
    }

    private static InetAddress getAppropriateIPv6Address() throws SocketException {
        System.out.println("Searching through the network interfaces...");
        Enumeration<NetworkInterface> is = NetworkInterface.getNetworkInterfaces();
        while (is.hasMoreElements()) {
            NetworkInterface i = is.nextElement();
            System.out.println("\tinterface: " + i);

            // just a "good enough" marker that the interface
            // does not support a loopback and therefore should not be used
            if ( i.getHardwareAddress() == null) continue;
            if (!i.isUp()) continue;

            Enumeration<InetAddress> as = i.getInetAddresses();
            while (as.hasMoreElements()) {
                InetAddress a = as.nextElement();
                System.out.println("\t\taddress: " + a.getHostAddress());
                if ( !(a instanceof Inet6Address &&
                       a.toString().contains("%")) ) {
                    continue;
                }
                return a;
            }
        }
        return null;
    }

    private static HttpHandler createCapturingHandler(CompletableFuture<Headers> headers) {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                headers.complete(exchange.getRequestHeaders());
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                exchange.close();
            }
        };
    }
}
