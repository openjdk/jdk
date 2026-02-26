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
 * @bug 8377302
 * @summary HttpServer.stop() blocks indefinitely if handler throws
 * @modules jdk.httpserver java.logging
 * @library /test/lib
 * @run main/othervm ${test.main.class}
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import jdk.test.lib.Utils;
import static com.sun.net.httpserver.HttpExchange.RSPBODY_CHUNKED;

public class FailAndStopTest implements HttpHandler {
    // Keep that logger in a static field to make sure it doesn't
    // get GC'ed and recreated before the HttpServer is initialized.
    private static final Logger LOGGER = Logger.getLogger("com.sun.net.httpserver");
    private static final String BODY = "OK";

    static enum TestCases {
        FAILNOW("failNow", TestCases::shouldAlwaysFail),
        ASSERTNOW("assertNow", TestCases::shouldAlwaysFail),
        RESPANDFAIL("failAfterResponseStatus", TestCases::shouldFailExceptForHead),
        RESPANDASSERT("assertAfterResponseStatus", TestCases::shouldFailExceptForHead),
        CLOSEAFTERRESP("closeExchangeAfterResponseStatus", TestCases::shouldFailExceptForHead),
        BODYANDFAIL("failAfterBody", TestCases::shouldFailExceptForHeadOrHttps),
        BODYANDASSERT("assertAfterBody", TestCases::shouldFailExceptForHeadOrHttps),
        CLOSEBEFOREOS("closeExchangeBeforeOS", TestCases::shouldNeverFail),
        CLOSEANDRETURN("closeAndReturn", TestCases::shouldNeverFail),
        CLOSEANDFAIL("closeAndFail", TestCases::shouldNeverFail),
        CLOSEANDASSERT("closeAndAssert", TestCases::shouldNeverFail);

        private final String query;
        private BiPredicate<String,String> shouldFail;
        TestCases(String query, BiPredicate<String,String> shouldFail) {
            this.query = query;
            this.shouldFail = shouldFail;
        }
        boolean shouldFail(String scheme, String method) {
            // in case of HEAD method the client should not
            // fail if we throw after sending response headers
            return shouldFail.test(scheme, method);
        }
        private static boolean shouldAlwaysFail(String scheme, String method) {
            return true;
        }
        private static boolean shouldNeverFail(String scheme, String method) {
            return false;
        }
        private static boolean shouldFailExceptForHead(String scheme, String method) {
            return !"HEAD".equals(method);
        }
        private static boolean shouldFailExceptForHeadOrHttps(String scheme, String method) {
            // When using https, the buffered response bytes may be sent
            // when the connection is closed, in which case the full body
            // will be correctly received, and the client connection
            // will not fail. With plain http, the bytes are not sent and
            // the client fails with premature end of file.
            return !"HEAD".equals(method) && !"https".equalsIgnoreCase(scheme);
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

    private static void enableHttpServerLogging() {
        // set HttpServer's logger to ALL
        LOGGER.setLevel(Level.ALL);
        // get the root logger, get its first handler (by default
        // it's a ConsoleHandler), and set its level to ALL (by
        // default its level is INFO).
        Logger.getLogger("").getHandlers()[0].setLevel(Level.ALL);
    }


    public static void main(String[] args) throws Exception {

        enableHttpServerLogging();

        // test with GET
        for (var test : TestCases.values()) {
            test(test, Optional.empty(), "http");
            test(test, Optional.empty(), "https");
        }
        // test with HEAD
        for (var test : TestCases.values()) {
            test(test, Optional.of("HEAD"), "http");
            test(test, Optional.of("HEAD"), "https");
        }
    }

    private static SSLContext initSSLContext(boolean secure) {
        SSLContext context = secure ? SimpleSSLContext.findSSLContext() : null;
        if (secure) {
            SSLContext.setDefault(context);
        }
        return context;
    }

    private static HttpServer createHttpServer(SSLContext context) throws IOException {
        var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        if (context != null) {
            var server = HttpsServer.create(address, 0);
            server.setHttpsConfigurator(new HttpsConfigurator(context));
            return server;
        } else {
            return HttpServer.create(address, 0);
        }
    }

    private static HttpURLConnection createConnection(URL url, boolean secure)
            throws IOException {
        HttpURLConnection urlc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        if (secure) {
            ((HttpsURLConnection)urlc).setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }
        return urlc;
    }

    private static void test(TestCases test, Optional<String> method, String scheme)
            throws Exception {
        boolean secure = "https".equalsIgnoreCase(scheme);
        SSLContext context = initSSLContext(secure);
        HttpServer server = createHttpServer(context);

        System.out.println("Test: " + method.orElse("GET") + " " + test.query);
        System.out.println("Server listening at: " + server.getAddress());
        try {
            server.createContext("/FailAndStopTest/", new FailAndStopTest());
            server.start();

            URL url = URIBuilder.newBuilder()
                    .scheme(scheme)
                    .loopback()
                    .port(server.getAddress().getPort())
                    .path("/FailAndStopTest/")
                    .query(test.query)
                    .toURLUnchecked();
            System.out.println("Connecting to: " + url);
            HttpURLConnection urlc = createConnection(url, secure);
            if (method.isPresent()) urlc.setRequestMethod(method.get());
            try {
                System.out.println("Client: Response code received: " + urlc.getResponseCode());
                InputStream is = urlc.getInputStream();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                is.close();
                System.out.printf("Client: read body: \"%s\"%n", body);
                if (test.shouldFail(scheme, urlc.getRequestMethod())) {
                    throw new AssertionError(test.query + ": test did not fail");
                }
                if (!method.orElse("GET").equals("HEAD")) {
                    if (!BODY.equals(body)) {
                        throw new AssertionError(
                                String.format("\"%s\" != \"%s\"", body, BODY));
                    }
                } else if (!body.isEmpty()) {
                    throw new AssertionError("Body is not empty: " + body);
                }
            } catch (IOException so) {
                if (test.shouldFail(scheme, urlc.getRequestMethod())) {
                    // expected
                    System.out.println(test.query + ": Got expected exception: " + so);
                } else if (!test.shouldFail("http", urlc.getRequestMethod())) {
                    // When using https, the buffered response bytes may be sent
                    // when the connection is closed, in which case the full body
                    // will be correctly received, and the client connection
                    // will not fail. With plain http, the bytes are not sent and
                    // the client fails with premature end of file.
                    // So only fail here if the test should not fail with plain
                    // http - we want to accept possible exception for https...
                    throw new AssertionError(
                                String.format("%s: test failed with %s", test.query, so), so);
                } else {
                    System.out.printf("%s: WARNING: unexpected exception: %s%n", test.query, so);
                    // should only happen in those two cases:
                    assert secure && !"HEAD".equals(method) &&
                            (test == TestCases.BODYANDFAIL || test == TestCases.BODYANDASSERT);
                }
            }
        } finally {
            // if not fixed will cause the test to fail in jtreg timeout
            server.stop((int)Utils.adjustTimeout(5000));
            System.out.println("Server stopped as expected");
        }
    }
}
