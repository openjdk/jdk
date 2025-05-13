/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.UnsupportedProtocolVersionException;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.net.SimpleSSLContext;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;


/*
 * @test
 * @summary verifies that the SSLParameters configured with specific cipher suites
 *          and TLS protocol versions gets used by the HttpClient for HTTP/3
 * @library /test/jdk/java/net/httpclient/lib /test/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run main/othervm
 *       -Djdk.internal.httpclient.debug=true
 *       -Djdk.httpclient.HttpClient.log=all
 *       H3QuicTLSConnection
 */
public class H3QuicTLSConnection {

    private static final SSLParameters DEFAULT_SSL_PARAMETERS = new SSLParameters();

    // expect highest supported version we know about
    private static String expectedTLSVersion(SSLContext ctx) throws Exception {
        if (ctx == null) {
            ctx = SSLContext.getDefault();
        }
        SSLParameters params = ctx.getSupportedSSLParameters();
        String[] protocols = params.getProtocols();
        for (String prot : protocols) {
            if (prot.equals("TLSv1.3"))
                return "TLSv1.3";
        }
        return "TLSv1.2";
    }

    public static void main(String[] args) throws Exception {
        // create and set the default SSLContext
        SSLContext context = new SimpleSSLContext().get();
        SSLContext.setDefault(context);

        Handler handler = new Handler();

        try (HttpTestServer server = HttpTestServer.create(HTTP_3_URI_ONLY, SSLContext.getDefault())) {
            server.addHandler(handler, "/");
            server.start();

            String uriString = "https://" + server.serverAuthority();

            // run test cases
            boolean success = true;

            SSLParameters parameters = null;
            success &= expectFailure(
                    "---\nTest #1: SSL parameters is null, expect NPE",
                    () -> connect(uriString, parameters),
                    NullPointerException.class,
                    Optional.empty());

            success &= expectSuccess(
                    "---\nTest #2: default SSL parameters, "
                            + "expect successful connection",
                    () -> connect(uriString, DEFAULT_SSL_PARAMETERS));
            success &= checkProtocol(handler.getSSLSession(), expectedTLSVersion(null));

            success &= expectFailure(
                    "---\nTest #3: SSL parameters with "
                            + "TLS_AES_128_GCM_SHA256 cipher suite, but TLSv1.2 "
                            + "expect UnsupportedProtocolVersionException",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"TLS_AES_128_GCM_SHA256"},
                            new String[]{"TLSv1.2"})),
                    UnsupportedProtocolVersionException.class,
                    Optional.empty());

            // set SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA and expect it to fail since
            // it's not supported with TLS v1.3
            success &= expectFailure(
                    "---\nTest #4: SSL parameters with "
                            + "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA cipher suite, "
                            + "expect No appropriate protocol " +
                            "(protocol is disabled or cipher suites are inappropriate)",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA"},
                            new String[]{"TLSv1.3"})),
                    SSLHandshakeException.class,
                    Optional.of("protocol is disabled or cipher suites are inappropriate"));

            // set TLS_CHACHA_POLY1305_SHA256 cipher suite
            // which is not supported by the (default) SunJSSE provider
            success &= expectFailure(
                    "---\nTest #5: SSL parameters with "
                            + "TLS_CHACHA_POLY1305_SHA256 cipher suite, "
                            + "expect IllegalArgumentException: Unsupported CipherSuite",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"TLS_CHACHA_POLY1305_SHA256"},
                            new String[]{"TLSv1.3"})),
                    IllegalArgumentException.class,
                    Optional.of("Unsupported CipherSuite"));

            // set TLS_AES_128_GCM_SHA256 and TLS_AES_256_GCM_SHA384 cipher suite
            var suites = List.of("TLS_AES_128_GCM_SHA256",
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_CHACHA20_POLY1305_SHA256");
            success &= expectSuccess(
                    "---\nTest #6: SSL parameters with "
                            + "TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, " +
                            "and TLS_CHACHA20_POLY1305_SHA256 cipher suites,"
                            + " expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            suites.toArray(new String[0]),
                            new String[]{"TLSv1.3"})));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.3");
            success &= checkCipherSuite(handler.getSSLSession(), suites);

            // set TLS_AES_128_GCM_SHA256 cipher suite
            success &= expectSuccess(
                    "---\nTest #7: SSL parameters with "
                            + "TLS_AES_128_GCM_SHA256 cipher suites,"
                            + " expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"TLS_AES_128_GCM_SHA256"},
                            new String[]{"TLSv1.3"})));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.3");
            success &= checkCipherSuite(handler.getSSLSession(),
                    "TLS_AES_128_GCM_SHA256");

            // set TLS_AES_256_GCM_SHA384 cipher suite
            success &= expectSuccess(
                    "---\nTest #8: SSL parameters with "
                            + "TLS_AES_256_GCM_SHA384 cipher suites,"
                            + " expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"TLS_AES_256_GCM_SHA384"},
                            new String[]{"TLSv1.3"})));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.3");
            success &= checkCipherSuite(handler.getSSLSession(),
                    "TLS_AES_256_GCM_SHA384");

            // set TLS_CHACHA20_POLY1305_SHA256 cipher suite
            success &= expectSuccess(
                    "---\nTest #9: SSL parameters with "
                            + "TLS_CHACHA20_POLY1305_SHA256 cipher suites,"
                            + " expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            new String[]{"TLS_CHACHA20_POLY1305_SHA256"},
                            new String[]{"TLSv1.3"})));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.3");
            success &= checkCipherSuite(handler.getSSLSession(),
                    "TLS_CHACHA20_POLY1305_SHA256");

            if (success) {
                System.out.println("Test passed");
            } else {
                throw new RuntimeException("At least one test case failed");
            }
        }
    }

    private interface Test {
        void run() throws Exception;
    }

    private static class Handler implements HttpTestHandler {

        private static final byte[] BODY = "Test response".getBytes();

        private volatile SSLSession sslSession;

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("Handler: received request to "
                    + t.getRequestURI());

            try (InputStream is = t.getRequestBody()) {
                byte[] body = is.readAllBytes();
                System.out.println("Handler: read " + body.length
                        + " bytes of body: ");
                System.out.println(new String(body));
            }

            sslSession = t.getSSLSession();

            try (OutputStream os = t.getResponseBody()) {
                t.sendResponseHeaders(200, BODY.length);
                os.write(BODY);
            }

        }

        SSLSession getSSLSession() {
            return sslSession;
        }
    }

    private static void connect(String uriString, SSLParameters sslParameters)
            throws URISyntaxException, IOException, InterruptedException {
        HttpClient.Builder builder = HttpServerAdapters.createClientBuilderForH3()
                .proxy(Builder.NO_PROXY)
                .version(HttpClient.Version.HTTP_3);
        if (sslParameters != DEFAULT_SSL_PARAMETERS)
            builder.sslParameters(sslParameters);
        try (final HttpClient client = builder.build()) {
            HttpRequest request = HttpRequest.newBuilder(new URI(uriString))
                    .POST(BodyPublishers.ofString("body"))
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .version(Version.HTTP_3)
                    .build();
            String body = client.send(request, BodyHandlers.ofString()).body();
            System.out.println("Response: " + body);
        } catch (UncheckedIOException uio) {
            throw uio.getCause();
        }
    }

    private static boolean checkProtocol(SSLSession session, String protocol) {
        if (session == null) {
            System.out.println("Check protocol: no session provided");
            return false;
        }

        System.out.println("Check protocol: negotiated protocol: "
                + session.getProtocol());
        System.out.println("Check protocol: expected protocol: "
                + protocol);
        if (!protocol.equals(session.getProtocol())) {
            System.out.println("Check protocol: unexpected negotiated protocol");
            return false;
        }

        return true;
    }

    private static boolean checkCipherSuite(SSLSession session, String ciphersuite) {
        if (session == null) {
            System.out.println("Check protocol: no session provided");
            return false;
        }

        System.out.println("Check protocol: negotiated ciphersuite: "
                + session.getCipherSuite());
        System.out.println("Check protocol: expected ciphersuite: "
                + ciphersuite);
        if (!ciphersuite.equals(session.getCipherSuite())) {
            System.out.println("Check protocol: unexpected negotiated ciphersuite");
            return false;
        }

        return true;
    }

    private static boolean checkCipherSuite(SSLSession session, List<String> ciphersuites) {
        if (session == null) {
            System.out.println("Check protocol: no session provided");
            return false;
        }

        System.out.println("Check protocol: negotiated ciphersuite: "
                + session.getCipherSuite());
        System.out.println("Check protocol: expected ciphersuite in: "
                + ciphersuites);
        if (!ciphersuites.contains(session.getCipherSuite())) {
            System.out.println("Check protocol: unexpected negotiated ciphersuite");
            return false;
        }

        return true;
    }

    private static boolean expectSuccess(String message, Test test) {
        System.out.println(message);
        try {
            test.run();
            System.out.println("Passed");
            return true;
        } catch (Exception e) {
            System.out.println("Failed: unexpected exception:");
            e.printStackTrace(System.out);
            return false;
        }
    }

    private static boolean expectFailure(String message, Test test,
                                         Class<? extends Throwable> expectedException,
                                         Optional<String> exceptionMsg) {

        System.out.println(message);
        try {
            test.run();
            System.out.println("Failed: unexpected successful connection");
            return false;
        } catch (Exception e) {
            System.out.println("Got an exception:");
            e.printStackTrace(System.out);
            if (expectedException != null
                    && !expectedException.isAssignableFrom(e.getClass())) {
                System.out.printf("Failed: expected %s, but got %s%n",
                        expectedException.getName(),
                        e.getClass().getName());
                return false;
            }
            if (exceptionMsg.isPresent()) {
                final String actualMsg = e.getMessage();
                if (actualMsg == null || !actualMsg.contains(exceptionMsg.get())) {
                    System.out.printf("Failed: exception message was expected"
                                    + " to contain \"%s\", but got \"%s\"%n",
                            exceptionMsg.get(), actualMsg);
                    return false;
                }
            }
            System.out.println("Passed: expected exception");
            return true;
        }
    }
}
