/*
 * Copyright (c) 2004, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 5049976
 * @library /test/lib
 * @run main/othervm SetChunkedStreamingMode
 * @summary Unspecified NPE is thrown when streaming output mode is enabled
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;

public class SetChunkedStreamingMode implements HttpHandler {

    void okReply (HttpExchange req) throws IOException {
        req.sendResponseHeaders(200, 0);
        try(PrintWriter pw = new PrintWriter(req.getResponseBody())) {
            pw.print("Hello .");
        }
        System.out.println ("Server: sent response");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        okReply(exchange);
    }

    static HttpServer server;

    public static void main (String[] args) throws Exception {
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            server = HttpServer.create(new InetSocketAddress(loopback, 0), 10);
            server.createContext("/", new SetChunkedStreamingMode());
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            System.out.println ("Server: listening on port: " + server.getAddress().getPort());
            URL url = URIBuilder.newBuilder()
                .scheme("http")
                .loopback()
                .port(server.getAddress().getPort())
                .path("/")
                .toURL();
            System.out.println ("Client: connecting to " + url);
            HttpURLConnection urlc = (HttpURLConnection)url.openConnection();
            urlc.setChunkedStreamingMode (0);
            urlc.setRequestMethod("POST");
            urlc.setDoOutput(true);
            InputStream is = urlc.getInputStream();
        } catch (Exception e) {
            if (server != null) {
                server.stop(1);
            }
            throw e;
        }
        server.stop(1);
    }

    public static void except (String s) {
        server.stop(1);
        throw new RuntimeException (s);
    }
}
