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
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.Utils;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_CHUNKED;

public class FailAndStopTest implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");
    private static final String BODY = "OK";

    static enum TestCases {
        FAILNOW("failNow", true),
        ASSERTNOW("assertNow", true),
        RESPANDFAIL("failAfterResponseStatus", true),
        RESPANDASSERT("failAfterResponseStatus", true),
        CLOSEAFTERRESP("closeExchangeAfterResponseStatus", true),
        BODYANDFAIL("failAfterResponseStatus", true),
        BODYANDASSERT("assertAfterResponseStatus", true),
        CLOSEBEFOREOS("closeExchangeBeforeOS", false),
        CLOSEANDRETURN("closeAndReturn", false),
        CLOSEANDFAIL("closeAndFail", false),
        CLOSEANDASSERT("closeAndAssert", false);

        private final String query;
        private final boolean shouldFail;
        TestCases(String query, boolean shouldFail) {
            this.query = query;
            this.shouldFail = shouldFail;
        }
        boolean shouldFail(String method) {
            // in case of HEAD method the client should not
            // fail if we throw after sending response headers
            return switch (this) {
                case FAILNOW -> shouldFail;
                case ASSERTNOW -> shouldFail;
                default -> shouldFail && !"HEAD".equals(method);
            };
        }
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        TestCases step = TestCases.FAILNOW;
        if (query == null || query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new NullPointerException("Got you!");
        }
        step = TestCases.ASSERTNOW;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new AssertionError("Got you!");
        }
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, body.length);
        step = TestCases.RESPANDFAIL;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new NullPointerException("Got you!");
        }
        step = TestCases.RESPANDASSERT;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new AssertionError("Got you!");
        }
        step = TestCases.CLOSEAFTERRESP;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            ex.close();
            return;
        }
        if (!"HEAD".equals(ex.getRequestMethod())) {
            ex.getResponseBody().write(body);
        }
        step = TestCases.BODYANDFAIL;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new NullPointerException("Got you!");
        }
        step = TestCases.BODYANDASSERT;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new AssertionError("Got you!");
        }
        step = TestCases.CLOSEBEFOREOS;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            ex.close();
            return;
        }
        System.out.println("Server: closing response body");
        ex.getResponseBody().close();
        step = TestCases.CLOSEANDRETURN;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            ex.close();
            return;
        }
        step = TestCases.CLOSEANDFAIL;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new NullPointerException("Got you!");
        }
        step = TestCases.CLOSEANDASSERT;
        if (query.equals(step.query)) {
            System.out.println("Server: " + step);
            throw new AssertionError("Got you!");
        }
    }


    public static void main(String[] args) throws Exception {
        LOGGER.setLevel(Level.ALL);
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
        // test with GET
        for (var test : TestCases.values()) {
            test(test, Optional.empty());
        }
        // test with HEAD
        for (var test : TestCases.values()) {
            test(test, Optional.of("HEAD"));
        }
    }
    private static void test(TestCases test, Optional<String> method) throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        System.out.println("Test: " + method.orElse("GET") + " " + test.query);
        System.out.println("Server listening at: " + server.getAddress());
        try {
            server.createContext("/FailAndStopTest/", new FailAndStopTest());
            server.start();

            URL url = URIBuilder.newBuilder()
                    .scheme("http")
                    .loopback()
                    .port(server.getAddress().getPort())
                    .path("/FailAndStopTest/")
                    .query(test.query)
                    .toURLUnchecked();

            HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            if (method.isPresent()) urlc.setRequestMethod(method.get());
            try {
                System.out.println("Client: Response code received: " + urlc.getResponseCode());
                InputStream is = urlc.getInputStream();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                if (test.shouldFail(urlc.getRequestMethod())) {
                    throw new AssertionError("%s: test did not fail"
                            .formatted(test.query));
                }
                System.out.println("Client: read body: \"%s\"".formatted(body));
                if (!method.orElse("GET").equals("HEAD")) {
                    if (!BODY.equals(body)) {
                        throw new AssertionError("\"%s\" != \"%s\""
                                .formatted(body, BODY));
                    }
                } else if (!body.isEmpty()) {
                    throw new AssertionError("Body is not empty: " + body);
                }
            } catch (SocketException so) {
                if (test.shouldFail(urlc.getRequestMethod())) {
                    // expected
                    System.out.println(test.query + ": Got expected exception: " + so);
                } else {
                    throw new AssertionError("%s: test failed with %s"
                            .formatted(test.query, so), so);
                }
            }
        } finally {
            // if not fixed will cause the test to fail in jtreg timeout
            server.stop((int)Utils.adjustTimeout(5000));
            System.out.println("Server stopped as expected");
        }
    }
}
