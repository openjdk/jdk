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

/**
 * @test
 * @bug 6666666
 * @summary HttpServer.stop() blocks indefinitely if handler throws
 * @modules jdk.httpserver java.logging
 * @library /test/lib
 * @run main/othervm FailAndStopTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.Utils;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_CHUNKED;

public class FailAndStopTest implements HttpHandler {
    private static final int HTTP_STATUS_CODE_OK = 200;
    private static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");
    private final HttpServer server;


    public FailAndStopTest(HttpServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        throw new NullPointerException("Got you!");
    }


    public static void main(String[] args) throws Exception {
        LOGGER.setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        try {
            server.createContext("/context", new FailAndStopTest(server));
            server.start();

            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getAddress().getPort())
                    .path("/context")
                    .toURLUnchecked();

            HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            try {
                System.out.println("Client: Response code received: " + urlc.getResponseCode());
                InputStream is = urlc.getInputStream();
                is.readAllBytes();
                is.close();
            } catch (SocketException so) {
                // expected
                System.out.println("Got expected exception: " + so);
            }
        } finally {
            // if not fixed will cause the test to fail in jtreg timeout
            server.stop((int)Utils.adjustTimeout(5000));
            System.out.println("Server stopped as expected");
        }
    }
}
