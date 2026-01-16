/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

import javax.net.ssl.SSLContext;

import jdk.test.lib.net.SimpleSSLContext;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/*
 * @test
 * @summary Verifies that the HTTP client correctly handles various alt-svc usages
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.HttpServerAdapters
 *
 * @run testng/othervm -Djdk.internal.httpclient.debug=true
 *                     -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *                     AltServiceUsageTest
 */
public class AltServiceUsageTest implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private HttpTestServer originServer;
    private HttpTestServer altServer;

    private DatagramChannel udpNotResponding;

    @BeforeClass
    public void beforeClass() throws Exception {
        // attempt to create an HTTP/3 server, an HTTP/2 server, and a
        // DatagramChannel bound to the same port as the HTTP/2 server
        int count = 0;
        InetSocketAddress altServerAddress = null, originServerAddress = null;
        while (count++ < 10) {

            createServers();
            altServerAddress = altServer.getAddress();
            originServerAddress = originServer.getAddress();

            if (originServerAddress.equals(altServerAddress)) break;
            udpNotResponding = DatagramChannel.open();
            try {
                udpNotResponding.bind(originServerAddress);
                break;
            } catch (IOException x) {
                System.out.printf("Failed to bind udpNotResponding to %s: %s%n",
                        originServerAddress, x);
                safeStop(altServer);
                safeStop(originServer);
                udpNotResponding.close();
            }
        }
        if (count > 10) {
            throw new AssertionError("Couldn't reserve UDP port at " + originServerAddress);
        }

        System.out.println("HTTP/3 service started at " + altServerAddress);
        System.out.println("HTTP/2 service started at " + originServerAddress);
        System.err.println("**** All servers started. Test will start shortly ****");
    }

    private void createServers() throws IOException {
        altServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        altServer.addHandler(new All200OKHandler(), "/foo/");
        altServer.addHandler(new RequireAltUsedHeader(), "/bar/");
        altServer.addHandler(new All200OKHandler(), "/maxAgeAltSvc/");
        altServer.start();

        originServer = HttpTestServer.create(HTTP_2, sslContext);
        originServer.addHandler(new H3AltServicePublisher(altServer.getAddress()), "/foo/");
        originServer.addHandler(new H3AltSvcPublisherWith421(altServer.getAddress()), "/foo421/");
        originServer.addHandler(new H3AltServicePublisher(altServer.getAddress()), "/bar/");
        originServer.addHandler(new H3AltSvcPublisherWithMaxAge(altServer.getAddress()), "/maxAgeAltSvc/");
        originServer.start();
    }

    @AfterClass
    public void afterClass() throws Exception {
        safeStop(originServer);
        safeStop(altServer);
        safeClose(udpNotResponding);
    }

    private static void safeStop(final HttpTestServer server) {
        if (server == null) {
            return;
        }
        final InetSocketAddress serverAddr = server.getAddress();
        try {
            System.out.println("Stopping server " + serverAddr);
            server.stop();
        } catch (Exception e) {
            System.err.println("Ignoring exception: " + e.getMessage() + " that occurred " +
                    "during stop of server: " + serverAddr);
        }
    }

    private static void safeClose(final DatagramChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            System.out.println("Closing DatagramChannel " + channel.getLocalAddress());
            channel.close();
        } catch (Exception e) {
            System.err.println("Ignoring exception: " + e.getMessage() + " that occurred " +
                    "during close of DatagramChannel: " + channel);
        }
    }

    private static class H3AltServicePublisher implements HttpTestHandler {
        private static final String RESPONSE_CONTENT = "apple";

        private final String altSvcHeader;

        /**
         * @param altServerAddr Address of the alt service which will be advertised by this handler
         */
        private H3AltServicePublisher(final InetSocketAddress altServerAddr) {
            this.altSvcHeader = "h3=\"" + altServerAddr.getHostName() + ":" + altServerAddr.getPort()
                    + "\"; persist=1; intentional-unknown-param-which-is-expected-to-be-ignored=foo;";
        }

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            String maxAgeParam = "";
            if (getMaxAge().isPresent()) {
                maxAgeParam = "; ma=" + getMaxAge().getAsLong();
            }
            exchange.getResponseHeaders().addHeader("alt-svc", altSvcHeader + maxAgeParam);
            final int statusCode = getResponseCode();
            System.out.println("Sending response with status code " + statusCode + " and " +
                    "alt-svc header " + altSvcHeader);
            final byte[] content = RESPONSE_CONTENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, content.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }

        protected OptionalLong getMaxAge() {
            return OptionalLong.empty();
        }

        protected int getResponseCode() {
            return 200;
        }
    }

    private static final class H3AltSvcPublisherWith421 extends H3AltServicePublisher {

        private H3AltSvcPublisherWith421(InetSocketAddress h3ServerAddr) {
            super(h3ServerAddr);
        }

        @Override
        protected int getResponseCode() {
            return 421;
        }
    }

    private static final class H3AltSvcPublisherWithMaxAge extends H3AltServicePublisher {

        private H3AltSvcPublisherWithMaxAge(InetSocketAddress h3ServerAddr) {
            super(h3ServerAddr);
        }

        @Override
        protected OptionalLong getMaxAge() {
            return OptionalLong.of(2);
        }
    }

    private static final class All200OKHandler implements HttpTestHandler {
        private static final String RESPONSE_CONTENT = "orange";

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            final byte[] content = RESPONSE_CONTENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, content.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    private static final class RequireAltUsedHeader implements HttpTestHandler {
        private static final String RESPONSE_CONTENT = "tomato";

        @Override
        public void handle(final HttpTestExchange exchange) throws IOException {
            final Optional<String> altUsed = exchange.getRequestHeaders().firstValue("alt-used");
            if (altUsed.isEmpty()) {
                System.out.println("Error - Missing alt-used header in request");
                exchange.sendResponseHeaders(400, 0);
                return;
            }
            if (altUsed.get().isBlank()) {
                System.out.println("Error - Blank value for alt-used header in request");
                exchange.sendResponseHeaders(400, 0);
                return;
            }
            System.out.println("Found alt-used request header: " + altUsed.get());
            final byte[] content = RESPONSE_CONTENT.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, content.length);
            try (final OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    /**
     * This test sends a HTTP3 request to a server which responds back with an alt-svc header pointing
     * to a different server. The test then issues the exact same request again and this time it
     * expects the alt-service host/server to handle that request.
     */
    @Test
    public void testAltSvcHeaderUsage() throws Exception {
         HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .version(HTTP_3)
                .build();
        // send a HTTP3 request to a server which is expected to respond back
        // with a 200 response and an alt-svc header pointing to another/different H3 server
        final URI reqURI = URI.create("https://" + toHostPort(originServer) + "/foo/");
        final HttpRequest request = HttpRequest.newBuilder(reqURI)
                .GET()
                .setOption(H3_DISCOVERY, ALT_SVC)
                .build();
        System.out.println("Issuing request " + reqURI);
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(response.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
        Assert.assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
        System.out.println("Received alt-svc header value: " + altSvcHeader.get());
        final String expectedHeader = "h3=\"" + toHostPort(altServer) + "\"";
        Assert.assertTrue(altSvcHeader.get().contains(expectedHeader),
                "Unexpected alt-svc header value: " + altSvcHeader.get()
                        + ", was expected to contain: " + expectedHeader);

        // now issue the same request again and this time expect it to be handled by the alt-service
        System.out.println("Again issuing request " + reqURI);
        final HttpResponse<String> secondResponse = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(secondResponse.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(secondResponse.body(), All200OKHandler.RESPONSE_CONTENT,
                "Unexpected response body");
        var TRACKER = ReferenceTracker.INSTANCE;
        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        var error = TRACKER.check(tracker, 1500);
        if (error != null) throw error;
    }

    /**
     * This test sends a HTTP3 request to a server which responds back with an alt-svc header pointing
     * to a different server and a response code of 421. The spec states that when this response code
     * is sent, any alt-svc headers should be ignored by the HTTP client. This test then issues the same
     * request again and expects that the alt-service wasn't used.
     */
    @Test
    public void testDontUseAltServiceFor421() throws Exception {
         HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .version(HTTP_3)
                .build();
        final URI reqURI = URI.create("https://" + toHostPort(originServer) + "/foo421/");
        final HttpRequest request = HttpRequest.newBuilder(reqURI)
                .GET()
                .setOption(H3_DISCOVERY, ALT_SVC)
                .build();
        System.out.println("Issuing request " + reqURI);
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(response.statusCode(), 421, "Unexpected response code");
        Assert.assertEquals(response.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
        Assert.assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
        System.out.println("Received alt-svc header value: " + altSvcHeader.get());
        final String expectedHeader = "h3=\"" + toHostPort(altServer) + "\"";
        Assert.assertTrue(altSvcHeader.get().contains(expectedHeader),
                "Unexpected alt-svc header value: " + altSvcHeader.get()
                        + ", was expected to contain: " + expectedHeader);

        // now issue the same request again and this time expect it to be handled by the alt-service
        System.out.println("Again issuing request " + reqURI);
        final HttpResponse<String> secondResponse = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(secondResponse.statusCode(), 421, "Unexpected response code");
        Assert.assertEquals(response.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        var TRACKER = ReferenceTracker.INSTANCE;
        var tracker = TRACKER.getTracker(client);
        client = null;
        System.gc();
        var error = TRACKER.check(tracker, 1500);
        if (error != null) throw error;
    }

    /**
     * This test sends a HTTP3 request to a server which responds back with an alt-svc header pointing
     * to a different server. The test then issues the exact same request again and this time it
     * expects the alt-service host/server to handle that request. The alt-service host/server which
     * handles this request will assert that the request came in with an "alt-used" header (which
     * is expected to be set by the HTTP client). If such a header is missing then that server
     * responds back with an erroneous response code of 4xx.
     */
    @Test
    public void testAltUsedHeader() throws Exception {
        HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .version(HTTP_3)
                .build();
        // send a HTTP3 request to a server which is expected to respond back
        // with a 200 response and an alt-svc header pointing to another/different H3 server
        final URI reqURI = URI.create("https://" + toHostPort(originServer) + "/bar/");
        final HttpRequest request = HttpRequest.newBuilder(reqURI)
                .GET()
                .setOption(H3_DISCOVERY, ALT_SVC)
                .build();
        System.out.println("Issuing request " + reqURI);
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(response.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
        Assert.assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
        System.out.println("Received alt-svc header value: " + altSvcHeader.get());
        final String expectedHeader = "h3=\"" + toHostPort(altServer) + "\"";
        Assert.assertTrue(altSvcHeader.get().contains(expectedHeader),
                "Unexpected alt-svc header value: " + altSvcHeader.get()
                        + ", was expected to contain: " + expectedHeader);

        // now issue the same request again and this time expect it to be handled by the alt-service
        // (which on the server side will assert the presence of the alt-used header, set by the
        // HTTP client)
        System.out.println("Again issuing request " + reqURI);
        final HttpResponse<String> secondResponse = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(secondResponse.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(secondResponse.body(), RequireAltUsedHeader.RESPONSE_CONTENT,
                "Unexpected response body");
        var TRACKER = ReferenceTracker.INSTANCE;
        var tracker = TRACKER.getTracker(client);
        client = null;
        var error = TRACKER.check(tracker, 1500);
        if (error != null) throw error;
    }


    /**
     * This test sends a HTTP3 request to a server which responds back with an alt-svc header pointing
     * to a different server. The advertised alt-svc is expected to have a max age of some seconds.
     * The test then immediately issues the exact same request again and this time it
     * expects the alt-service host/server to handle that request. Once this is done, the test waits
     * for a while (duration is greater than the max age of the advertised alt-service) and then
     * issues the exact same request again. This time the request is expected to be handled by the
     * origin server and not the alt-service (since the alt-service is expected to have expired by
     * now)
     */
    @Test
    public void testAltSvcMaxAgeExpiry() throws Exception {
         HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslContext(sslContext)
                .version(HTTP_3)
                .build();
        // send a HTTP3 request to a server which is expected to respond back
        // with a 200 response and an alt-svc header pointing to another/different H3 server
        final URI reqURI = URI.create("https://" + toHostPort(originServer) + "/maxAgeAltSvc/");
        final HttpRequest request = HttpRequest.newBuilder(reqURI)
                .GET()
                .setOption(H3_DISCOVERY, ALT_SVC)
                .build();
        System.out.println("Issuing request " + reqURI);
        final HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(response.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(response.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        final Optional<String> altSvcHeader = response.headers().firstValue("alt-svc");
        Assert.assertTrue(altSvcHeader.isPresent(), "alt-svc header is missing in response");
        System.out.println("Received alt-svc header value: " + altSvcHeader.get());
        final String expectedHeader = "h3=\"" + toHostPort(altServer) + "\"";
        Assert.assertTrue(altSvcHeader.get().contains(expectedHeader),
                "Unexpected alt-svc header value: " + altSvcHeader.get()
                        + ", was expected to contain: " + expectedHeader);

        // now issue the same request again and this time expect it to be handled by the alt-service
        System.out.println("Again issuing request " + reqURI);
        final HttpResponse<String> secondResponse = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(secondResponse.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(secondResponse.body(), All200OKHandler.RESPONSE_CONTENT,
                "Unexpected response body");

        // wait for alt-service to expire
        final long sleepDuration = 4000;
        System.out.println("Sleeping for " + sleepDuration + " milli seconds for alt-service to expire");
        Thread.sleep(sleepDuration);
        // now issue the same request again and this time expect it to be handled by the origin server
        // since the alt-service is expected to be expired
        System.out.println("Issuing request for a third time " + reqURI);
        final HttpResponse<String> thirdResponse = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Assert.assertEquals(thirdResponse.statusCode(), 200, "Unexpected response code");
        Assert.assertEquals(thirdResponse.body(), H3AltServicePublisher.RESPONSE_CONTENT,
                "Unexpected response body");
        var TRACKER = ReferenceTracker.INSTANCE;
        var tracker = TRACKER.getTracker(client);
        System.gc();
        client = null;
        var error = TRACKER.check(tracker, 1500);
        if (error != null) throw error;
    }

    private static String toHostPort(final HttpTestServer server) {
        final InetSocketAddress addr = server.getAddress();
        return addr.getHostName() + ":" + addr.getPort();
    }
}
