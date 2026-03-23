/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.test.lib.Platform;
import jdk.test.lib.net.SimpleSSLContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/*
 * @test id=default
 * @bug 8369920
 * @summary Verifies whether `QuicSelector` uses virtual threads
 *          as expected when no explicit configuration is provided
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors,http3
 *              H3QuicVTTest
 */
/*
 * @test id=never
 * @bug 8369920
 * @summary Verifies that `QuicSelector` does *not* use virtual threads
            when explicitly configured to "never" use them
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.internal.httpclient.quic.selector.useVirtualThreads=never
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors,http3
 *              H3QuicVTTest
 */
/*
 * @test id=always
 * @bug 8369920
 * @summary Verifies that `QuicSelector` does *always* use virtual threads
            when explicitly configured to "always" use them
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.internal.httpclient.quic.selector.useVirtualThreads=always
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors,http3
 *              H3QuicVTTest
 */
/*
 * @test id=explicit-default
 * @bug 8369920
 * @summary Verifies whether `QuicSelector` uses virtual threads
 *          as expected when `default` is explicitly configured
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.internal.httpclient.quic.selector.useVirtualThreads=default
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors,http3
 *              H3QuicVTTest
 */
/*
 * @test id=garbage
 * @bug 8369920
 * @summary Verifies whether `QuicSelector` uses virtual threads when
            it is configured using an invalid value
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run junit/othervm
 *              -Djdk.internal.httpclient.quic.selector.useVirtualThreads=garbage
 *              -Djdk.httpclient.HttpClient.log=requests,responses,headers,errors,http3
 *              H3QuicVTTest
 */
// -Djava.security.debug=all
class H3QuicVTTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static HttpTestServer h3Server;
    private static String requestURI;

    enum UseVTForSelector { ALWAYS, NEVER, DEFAULT }
    private static final String PROP_NAME = "jdk.internal.httpclient.quic.selector.useVirtualThreads";
    private static final UseVTForSelector USE_VT_FOR_SELECTOR;
    static {
        String useVtForSelector =
                System.getProperty(PROP_NAME, "default");
        USE_VT_FOR_SELECTOR = Stream.of(UseVTForSelector.values())
                .filter((v) -> v.name().equalsIgnoreCase(useVtForSelector))
                .findFirst().orElse(UseVTForSelector.DEFAULT);
    }

    private static boolean isQuicSelectorThreadVirtual() {
        return switch (USE_VT_FOR_SELECTOR) {
            case ALWAYS -> true;
            case NEVER  -> false;
            default     -> !Platform.isWindows();
        };
    }

    @BeforeAll
    static void beforeClass() throws Exception {
        // create an H3 only server
        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3Server.addHandler((exchange) -> exchange.sendResponseHeaders(200, 0), "/hello");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURI = "https://" + h3Server.serverAuthority() + "/hello";
    }

    @AfterAll
    static void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    /**
     * Issues various HTTP3 requests and verifies the responses are received
     */
    @Test
    void testBasicRequests() throws Exception {
        try (final HttpClient client = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .version(HTTP_3)
                .sslContext(sslContext).build()) {
            final URI reqURI = new URI(requestURI);
            final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);

            // GET
            final HttpRequest req1 = reqBuilder.copy().GET().build();
            System.out.println("\nIssuing request: " + req1);
            final HttpResponse<Void> resp1 = client.send(req1, BodyHandlers.discarding());
            Assertions.assertEquals(200, resp1.statusCode(), "unexpected response code for GET request");
            assertSelectorThread(client);

            // POST
            final HttpRequest req2 = reqBuilder.copy().POST(BodyPublishers.ofString("foo")).build();
            System.out.println("\nIssuing request: " + req2);
            final HttpResponse<Void> resp2 = client.send(req2, BodyHandlers.discarding());
            Assertions.assertEquals(200, resp2.statusCode(), "unexpected response code for POST request");
            assertSelectorThread(client);

            // HEAD
            final HttpRequest req3 = reqBuilder.copy().HEAD().build();
            System.out.println("\nIssuing request: " + req3);
            final HttpResponse<Void> resp3 = client.send(req3, BodyHandlers.discarding());
            Assertions.assertEquals(200, resp3.statusCode(), "unexpected response code for HEAD request");
            assertSelectorThread(client);
        }
    }

    // This method attempts to determine whether the quic selector thread
    // is a platform thread or a virtual thread, and throws if expectations
    // are not met.
    // Since we don't have access to the quic selector thread, the method
    // uses a roundabout way to figure this out: it enumerates all
    // platform threads, and if it finds a thread whose name matches
    // the expected name of the quic selector thread it concludes that the
    // selector thread is a platform thread. Otherwise, it assumes
    // that the thread is virtual.
    private static void assertSelectorThread(HttpClient client) {
        String clientId = client.toString().substring(client.toString().indexOf('('));
        String name = "Thread(QuicSelector(HttpClientImpl" + clientId + "))";
        Set<String> threads = new HashSet<>(Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .toList());
        boolean found = threads.contains(name);
        String status = found == isQuicSelectorThreadVirtual() ? "ERROR" : "SUCCESS";
        String propval = System.getProperty(PROP_NAME);
        if (propval == null) {
            System.out.printf("%s not defined, virtual=%s, thread found=%s%n",
                    PROP_NAME, isQuicSelectorThreadVirtual(), found);
        } else {
            System.out.printf("%s=%s, virtual=%s, thread found=%s%n",
                    PROP_NAME, propval, isQuicSelectorThreadVirtual(), found);
        }
        final String msg;
        if (found) {
            msg = "%s found in %s".formatted(name, threads);
            System.out.printf("%s: %s%n", status, msg);
        } else {
            msg = "%s not found in %s".formatted(name, threads);
            System.out.printf("%s: %s%n", status, msg);
        }
        Assertions.assertEquals(!isQuicSelectorThreadVirtual(), found, msg);
    }
}
