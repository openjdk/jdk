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
 * @bug 8376031
 * @modules jdk.httpserver
 * @library /test/lib
 * @summary Ensure HttpsURLConnection::getServerCertificates does not
 *   throw after calling getResponseCode() if the response doesn't have
 *   a body.
 * @run main/othervm ${test.main.class}
 * @run main/othervm -Djava.net.preferIPv6Addresses=true ${test.main.class}
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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

import static com.sun.net.httpserver.HttpExchange.RSPBODY_EMPTY;


public class GetServerCertificates {

    static final String URI_PATH = "/GetServerCertificates/";
    static final String BODY = "Go raibh maith agat";
    enum TESTS {
        HEAD("head", 200, "HEAD"),
        NOBODY("nobody", 200, "GET", "POST"),
        S204("204", 204, "GET", "POST"),
        S304("304", 304, "GET", "POST"),
        S200("200", 200, "GET", "POST");
        final String test;
        final int code;
        final List<String> methods;
        private TESTS(String test, int code, String... methods) {
            this.test = test;
            this.code = code;
            this.methods = List.of(methods);
        }
        boolean isFor(String path) {
            return path != null && path.endsWith("/" + test);
        }

        String test() { return test; }
        int code() { return code; }
        List<String> methods() { return methods; }
        static Optional<TESTS> fromPath(String path) {
            return Stream.of(values())
                    .filter(test -> test.isFor(path))
                    .findFirst();
        }
    }

     void test(String[] args) throws Exception {
         SSLContext.setDefault(SimpleSSLContext.findSSLContext());
         HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
             @Override
             public boolean verify(String hostname, SSLSession session) {
                 return true;
             }
         });
         HttpServer server = startHttpServer();
         try {
            InetSocketAddress address = server.getAddress();
            URI uri = URIBuilder.newBuilder()
                                .scheme("https")
                                .host(address.getAddress())
                                .port(address.getPort())
                                .path(URI_PATH)
                                .build();
            for (var test : TESTS.values()) {
                for (String method : test.methods()) {
                    doClient(method, uri, test);
                }
            }

        } finally {
             server.stop(1000);
         }
    }

    void doClient(String method, URI baseUri, TESTS test) throws Exception {
        assert baseUri.getRawQuery() == null;
        assert baseUri.getRawFragment() == null;
        assert test.methods().contains(method);

        String uriStr = baseUri.toString();
        if (!uriStr.endsWith("/")) uriStr = uriStr + "/";

        URI uri = new URI(uriStr + test.test());
        assert uri.toString().endsWith("/" + test.test());
        int code = test.code();
        System.out.println("doClient(%s, %s, %s)"
                .formatted(method, test.test(), test.code));

        // first request - should create a TCP connection
        HttpsURLConnection uc = (HttpsURLConnection)
                uri.toURL().openConnection(Proxy.NO_PROXY);
        if (!"GET".equals(method)) {
            uc.setRequestMethod(method);
        }
        try {
            uc.getServerCertificates();
            throw new AssertionError("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            System.out.println("Got expected ISE: " + ise);
        }
        int resp = uc.getResponseCode();
        check(resp == code, "Unexpected response code. Expected %s, got %s"
                .formatted(code, resp));

        check(uc.getServerCertificates());
        if (test == TESTS.S200) {
            byte[] bytes = uc.getInputStream().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("body: " + body);
            check(BODY.equals(body), "Unexpected response body. Expected \"%s\", got \"%s\""
                    .formatted(BODY, body));
        }

        // second request - should go on the same TCP connection.
        // We don't have a reliable way to test that, and it could
        // go on a new TCP connection if the previous connection
        // was already closed. It is not an issue either way.
        uc = (HttpsURLConnection)
                uri.toURL().openConnection(Proxy.NO_PROXY);
        if (!"GET".equals(method)) {
            uc.setRequestMethod(method);
        }
        try {
            uc.getServerCertificates();
            throw new AssertionError("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            System.out.println("Got expected ISE: " + ise);
        }
        resp = uc.getResponseCode();
        check(resp == code, "Unexpected response code. Expected %s, got %s"
                .formatted(code, resp));

        check(uc.getServerCertificates());
        if (test == TESTS.S200) {
            byte[] bytes = uc.getInputStream().readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("body: " + body);
            check(BODY.equals(body), "Unexpected response body. Expected \"%s\", got \"%s\""
                    .formatted(BODY, body));
        }

        uc.disconnect();
        try {
            uc.getServerCertificates();
            throw new AssertionError("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            System.out.println("Got expected ISE: " + ise);
        }

        // third request - forces the connection to close
        // after use so that we don't find any connection in the pool
        // for the next test case, assuming there was only
        // one connection in the first place.
        // Again there's no easy way to verify that the pool
        // is empty (and it's not really necessary to bother)
        uc = (HttpsURLConnection)
                uri.toURL().openConnection(Proxy.NO_PROXY);
        if (!"GET".equals(method)) {
            uc.setRequestMethod(method);
        }
        uc.setRequestProperty("Connection", "close");
        try {
            uc.getServerCertificates();
            throw new AssertionError("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            System.out.println("Got expected ISE: " + ise);
        }
        resp = uc.getResponseCode();
        check(resp == code, "Unexpected response code. Expected %s, got %s"
                .formatted(code, resp));
        check(uc.getServerCertificates());
        uc.disconnect();
        try {
            uc.getServerCertificates();
            throw new AssertionError("Expected IllegalStateException not thrown");
        } catch (IllegalStateException ise) {
            System.out.println("Got expected ISE: " + ise);
        }
    }

    // HTTP Server
    HttpServer startHttpServer() throws IOException {
        InetAddress localhost = InetAddress.getLoopbackAddress();
        HttpsServer httpServer = HttpsServer
                .create(new InetSocketAddress(localhost, 0), 0);
        var configurator = new HttpsConfigurator(SimpleSSLContext.findSSLContext());
        httpServer.setHttpsConfigurator(configurator);
        httpServer.createContext(URI_PATH, new SimpleHandler());
        httpServer.start();
        return httpServer;
    }

    static class SimpleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String path = t.getRequestURI().getRawPath();
                var test = TESTS.fromPath(path);
                if (!path.startsWith(URI_PATH) || !test.isPresent()) {
                    t.getRequestBody().close();
                    t.getResponseHeaders().add("Connection", "close");
                    t.sendResponseHeaders(421, RSPBODY_EMPTY);
                    t.close();
                    return;
                }
                try (var is = t.getRequestBody()) {
                    is.readAllBytes();
                }
                switch (test.get()) {
                    case S204, S304, NOBODY ->
                        t.sendResponseHeaders(test.get().code(), RSPBODY_EMPTY);
                    case S200 -> {
                        byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
                        t.sendResponseHeaders(test.get().code(), bytes.length);
                        try (var os = t.getResponseBody()) {
                            os.write(bytes);
                        }
                    }
                    case HEAD -> {
                        assert t.getRequestMethod().equals("HEAD");
                        byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
                        t.sendResponseHeaders(test.get().code(), bytes.length);
                    }
                }
                t.close();
            } catch (Throwable error) {
                error.printStackTrace();
                throw error;
            }
        }
    }

    volatile int passed = 0, failed = 0;
    boolean debug = false;
    void pass() {passed++;}
    void fail() {failed++;}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void debug(String message) { if (debug) System.out.println(message); }
    void check(boolean cond, String failMessage) {if (cond) pass(); else fail(failMessage);}
    void check(java.security.cert.Certificate[] certs) {
        // Use List.of to check that certs is not null and does not
        // contain null. NullPointerException will be thrown here
        // if that happens, which will make the test fail.
        check(!List.of(certs).isEmpty(), "no certificates returned");
    }
    public static void main(String[] args) throws Throwable {
        Class<?> k = new Object(){}.getClass().getEnclosingClass();
        try {k.getMethod("instanceMain",String[].class)
                .invoke( k.newInstance(), (Object) args);}
        catch (Throwable e) {throw e.getCause();}}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
