/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import static jdk.incubator.http.HttpRequest.BodyProcessor.fromString;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;

/*
 * @test
 * @bug 8150769 8157107
 * @key intermittent
 * @library server
 * @summary Checks that SSL parameters can be set for HTTP/2 connection
 * @modules jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 * @run main/othervm TLSConnection
 */
public class TLSConnection {

    private static final String KEYSTORE = System.getProperty("test.src")
            + File.separator + "keystore.p12";
   private static final String PASSWORD = "password";

    public static void main(String[] args) throws Exception {

        // enable all logging
        System.setProperty("jdk.httpclient.HttpClient.log", "all,frames:all");

        // initialize JSSE
        System.setProperty("javax.net.ssl.keyStore", KEYSTORE);
        System.setProperty("javax.net.ssl.keyStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", KEYSTORE);
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);

        Handler handler = new Handler();

        try (Http2TestServer server = new Http2TestServer(true, 0)) {
            server.addHandler(handler, "/");
            server.start();

            int port = server.getAddress().getPort();
            String uriString = "https://127.0.0.1:" + Integer.toString(port);

            // run test cases
            boolean success = true;

            SSLParameters parameters = null;
            success &= expectFailure(
                    "Test #1: SSL parameters is null, expect NPE",
                    () -> connect(uriString, parameters),
                    NullPointerException.class);

            success &= expectSuccess(
                    "Test #2: default SSL parameters, "
                            + "expect successful connection",
                    () -> connect(uriString, new SSLParameters()));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.2");

            // set SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA cipher suite
            // which has less priority in default cipher suite list
            success &= expectSuccess(
                    "Test #3: SSL parameters with "
                            + "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA cipher suite, "
                            + "expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            new String[] { "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA" })));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.2");
            success &= checkCipherSuite(handler.getSSLSession(),
                    "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA");

            // set TLS_RSA_WITH_AES_128_CBC_SHA cipher suite
            // which has less priority in default cipher suite list
            // also set TLSv11 protocol
            success &= expectSuccess(
                    "Test #4: SSL parameters with "
                            + "TLS_RSA_WITH_AES_128_CBC_SHA cipher suite,"
                            + " expect successful connection",
                    () -> connect(uriString, new SSLParameters(
                            new String[] { "TLS_RSA_WITH_AES_128_CBC_SHA" },
                            new String[] { "TLSv1.1" })));
            success &= checkProtocol(handler.getSSLSession(), "TLSv1.1");
            success &= checkCipherSuite(handler.getSSLSession(),
                    "TLS_RSA_WITH_AES_128_CBC_SHA");

            if (success) {
                System.out.println("Test passed");
            } else {
                throw new RuntimeException("At least one test case failed");
            }
        }
    }

    private static interface Test {

        public void run() throws Exception;
    }

    private static class Handler implements Http2Handler {

        private static final byte[] BODY = "Test response".getBytes();

        private volatile SSLSession sslSession;

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            System.out.println("Handler: received request to "
                    + t.getRequestURI());

            try (InputStream is = t.getRequestBody()) {
                byte[] body = is.readAllBytes();
                System.out.println("Handler: read " + body.length
                        + " bytes of body: ");
                System.out.println(new String(body));
            }

            try (OutputStream os = t.getResponseBody()) {
                t.sendResponseHeaders(200, BODY.length);
                os.write(BODY);
            }

            sslSession = t.getSSLSession();
        }

        SSLSession getSSLSession() {
            return sslSession;
        }
    }

    private static void connect(String uriString, SSLParameters sslParameters)
        throws URISyntaxException, IOException, InterruptedException
    {
        HttpClient client = HttpClient.newBuilder()
                                      .sslParameters(sslParameters)
                                      .version(HttpClient.Version.HTTP_2)
                                      .build();
        HttpRequest request = HttpRequest.newBuilder(new URI(uriString))
                                         .POST(fromString("body"))
                                         .build();
        String body = client.send(request, asString()).body();

        System.out.println("Response: " + body);
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
            Class<? extends Throwable> expectedException) {

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
            System.out.println("Passed: expected exception");
            return true;
        }
    }

}
