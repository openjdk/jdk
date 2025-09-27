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

/*
 * @test
 * @bug 8156514
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.TestUtil
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @compile ../ReferenceTracker.java
 * @run testng/othervm
 *      -Djdk.httpclient.HttpClient.log=frames,ssl,requests,responses,errors
 *      -Djdk.internal.httpclient.debug=true
 *      H3RedirectTest
 */

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

public class H3RedirectTest implements HttpServerAdapters {
    static int httpPort;
    static SSLContext sslContext;
    static HttpTestServer http3Server;
    static HttpClient client;

    static String httpURIString, altURIString1, altURIString2;
    static URI httpURI, altURI1, altURI2;

    static Supplier<String> sup(String... args) {
        Iterator<String> i = Arrays.asList(args).iterator();
        // need to know when to stop calling it.
        return i::next;
    }

    static class Redirector extends HttpTestRedirectHandler {
        private InetSocketAddress remoteAddr;
        private boolean error = false;

        Redirector(Supplier<String> supplier) {
            super(supplier);
        }

        @Override
        protected synchronized void examineExchange(HttpTestExchange ex) {
            InetSocketAddress addr = ex.getRemoteAddress();
            if (remoteAddr == null) {
                remoteAddr = addr;
                return;
            }
            // check that the client addr/port stays the same, proving
            // that the connection didn't get dropped.
            if (!remoteAddr.equals(addr)) {
                System.err.printf("Error %s/%s\n", remoteAddr,
                        addr.toString());
                error = true;
            }
        }

        @Override
        protected int redirectCode() {
            return 308; // we need to use a code that preserves the body
        }

        public synchronized boolean error() {
            return error;
        }
    }

    static void initialize() throws Exception {
        try {
            SimpleSSLContext sslct = new SimpleSSLContext();
            sslContext = sslct.get();
            client = getClient();
            http3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
            httpPort = http3Server.getAddress().getPort();
            String serverAuth = http3Server.serverAuthority();

            // urls are accessed in sequence below. The first two are on
            // different servers. Third on same server as second. So, the
            // client should use the same http connection.
            httpURIString = "https://" + serverAuth + "/foo/";
            httpURI = URI.create(httpURIString);
            altURIString1 = "https://" + serverAuth + "/redir";
            altURI1 = URI.create(altURIString1);
            altURIString2 = "https://" + serverAuth + "/redir_again";
            altURI2 = URI.create(altURIString2);

            Redirector r = new Redirector(sup(altURIString1, altURIString2));
            http3Server.addHandler(r, "/foo");
            http3Server.addHandler(r, "/redir");
            http3Server.addHandler(new HttpTestEchoHandler(), "/redir_again");

            http3Server.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public static void test() throws Exception {
        try {
            initialize();
            simpleTest();
            ReferenceTracker.INSTANCE.track(client);
            client = null;
            System.gc();
            var error = ReferenceTracker.INSTANCE.check(1500);
            if (error != null) throw error;
        } finally {
            http3Server.stop();
        }
    }

    static HttpClient getClient() {
        if (client == null) {
            client = HttpServerAdapters.createClientBuilderForH3()
                               .followRedirects(HttpClient.Redirect.ALWAYS)
                               .sslContext(sslContext)
                               .version(HTTP_3)
                               .build();
        }
        return client;
    }

    static URI getURI() {
        return URI.create(httpURIString);
    }

    static void checkStatus(int expected, int found) throws Exception {
        if (expected != found) {
            System.err.printf ("Test failed: wrong status code %d/%d\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkURIs(URI expected, URI found) throws Exception {
        System.out.printf ("Expected: %s, Found: %s\n", expected, found);
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong URI %s/%s\n",
                    expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void checkStrings(String expected, String found) throws Exception {
        if (!expected.equals(found)) {
            System.err.printf ("Test failed: wrong string %s/%s\n",
                expected, found);
            throw new RuntimeException("Test failed");
        }
    }

    static void check(boolean cond, Object... msg) {
        if (cond)
            return;
        StringBuilder sb = new StringBuilder();
        for (Object o : msg)
            sb.append(o);
        throw new RuntimeException(sb.toString());
    }

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    static void simpleTest() throws Exception {
        URI uri = getURI();
        System.err.println("Request to " + uri);

        HttpClient client = getClient();
        HttpRequest req = HttpRequest.newBuilder(uri)
                                     .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                                     .POST(BodyPublishers.ofString(SIMPLE_STRING))
                                     .build();
        CompletableFuture<HttpResponse<String>> cf = client.sendAsync(req, BodyHandlers.ofString());
        HttpResponse<String> response = cf.join();

        checkStatus(200, response.statusCode());
        String responseBody = response.body();
        checkStrings(SIMPLE_STRING, responseBody);
        checkURIs(response.uri(), altURI2);

        // check two previous responses
        HttpResponse<String> prev = response.previousResponse()
            .orElseThrow(() -> new RuntimeException("no previous response"));
        checkURIs(prev.uri(), altURI1);

        prev = prev.previousResponse()
            .orElseThrow(() -> new RuntimeException("no previous response"));
        checkURIs(prev.uri(), httpURI);

        checkPreviousRedirectResponses(req, response);

        System.err.println("DONE");
    }

    static void checkPreviousRedirectResponses(HttpRequest initialRequest,
                                               HttpResponse<?> finalResponse) {
        // there must be at least one previous response
        finalResponse.previousResponse()
                .orElseThrow(() -> new RuntimeException("no previous response"));

        HttpResponse<?> response = finalResponse;
        do {
            URI uri = response.uri();
            response = response.previousResponse().get();
            check(300 <= response.statusCode() && response.statusCode() <= 309,
                    "Expected 300 <= code <= 309, got:" + response.statusCode());
            check(response.body() == null, "Unexpected body: " + response.body());
            String locationHeader = response.headers().firstValue("Location")
                    .orElseThrow(() -> new RuntimeException("no previous Location"));
            check(uri.toString().endsWith(locationHeader),
                    "URI: " + uri + ", Location: " + locationHeader);
        } while (response.previousResponse().isPresent());

        // initial
        check(initialRequest.equals(response.request()),
                "Expected initial request [%s] to equal last prev req [%s]",
                initialRequest, response.request());
    }
}
