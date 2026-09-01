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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;

import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.net.ssl.SSLContext;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange.RSPBODY_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @test
 * @summary Verify that a connection with overdue idle timeout is not reused
 *
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 *
 * @comment Why do we force the usage of virtual threads at the selector? The
 *          problem we are stressing is not an issue specific to virtual threads
 *          or their usage in the HTTP client. We're forcing the usage of
 *          virtual threads, because this way it is easier to starve the thread
 *          pool used by the selector. This issue could very well be observed
 *          using platform threads, but it would be more difficult to reproduce
 *          reliably.
 *
 * @comment Why do we skip the test on Windows? On Windows, the selector
 *          implementation (i.e., `WEPollSelectorImpl`) blocks a virtual thread
 *          without releasing its carrier. With both
 *          `jdk.virtualThreadScheduler.{parallelism,maxPoolSize}` set to 1, no
 *          carrier remains to compensate for the blocked selector, and the
 *          initial client request cannot make progress. We could increase the
 *          VT scheduler capacity, but this contradicts with the reason we fix
 *          it to 1 in the first place: to starve the HTTP Client selector
 *          threads.
 *
 * @comment Why do we configure both `{quic,tcp}.selector.useVirtualThreads`?
 *          As of date, connection eviction is triggered by the selector of
 *          `HttpClientImpl`, not by the QUIC selector. Being prudent, we fix
 *          both to virtual threads.
 *
 * @comment Why are both `parallelism` and `maxPoolSize` 1? Because the default
 *          carrier thread pool (i.e., FJP) requires `parallelism <= maxPoolSize`
 *          and having 1 thread in the pool is easier to make it starve.
 *
 * @requires os.family != "windows"
 *
 * @run junit/othervm
 *      -Djdk.httpclient.keepalive.timeout=1
 *      -Djdk.internal.httpclient.quic.selector.useVirtualThreads=always
 *      -Djdk.internal.httpclient.tcp.selector.useVirtualThreads=always
 *      -Djdk.virtualThreadScheduler.parallelism=1
 *      -Djdk.virtualThreadScheduler.maxPoolSize=1
 *      ${test.main.class}
 */

class IdleConnectionTimeoutReuseTest {

    /**
     * @implNote
     * This test has several timing-sensitive assumptions. If these assumptions
     * hold, the test will verify the subject behavior. If not, the test will
     * and should pass anyway. Therefore, it is not a problem if the assumptions
     * don't hold. Local testing has shown that these assumptions do hold almost
     * always.
     */
    @ParameterizedTest
    @EnumSource(InfraFactory.class)
    void testDelayedIdleTimeout(InfraFactory infraFactory) throws Throwable {
        try (var server = infraFactory.createStartedServer();
             var client = infraFactory.createClient()) {

            // Issue the 1st request establishing the connection
            var request = infraFactory.createRequest(server);
            var response1 = client.send(request, discarding());
            assertEquals(200, response1.statusCode());
            var response1Label = response1.connectionLabel().orElseThrow();

            // Give the worker that closes the first exchange time to register the
            // idle timer. Note that this is timing-sensitive, and hence, an
            // assumption.
            Thread.sleep(Utils.adjustTimeout(200));

            // In the JTreg `@test` configuration above,
            //
            // 1. Virtual thread pool is configured to have at most 1 carrier thread.
            // 2. HTTP client's selector is configured to use virtual threads.
            //
            // Occupy that single carrier thread to block the HTTP client's selector
            // from processing idle timeouts.
            var carrierBlockerStarted = new CountDownLatch(1);
            var carrierBlockerStopped = new AtomicBoolean();
            var carrierBlocker = Thread.ofVirtual().start(() -> {
                carrierBlockerStarted.countDown();
                while (!carrierBlockerStopped.get()) {
                    Thread.onSpinWait();
                }
            });
            carrierBlockerStarted.await();

            try {

                // The virtual selector cannot process the 1s timeout while its
                // only carrier is occupied. Let the timeout become overdue
                // before reserving the connection for the 2nd request.
                Thread.sleep(Utils.adjustTimeout(1500));

                // Execute the 2nd request
                var response2Future = client.sendAsync(request, discarding());

                // Release the carrier thread blocker, so both idle timeout
                // processing and serving of the 2nd request can proceed. We
                // first sleep some to allow the latter to proceed as much as
                // possible. This increases its chances to get executed first.
                Thread.sleep(Utils.adjustTimeout(100));
                carrierBlockerStopped.set(true);
                carrierBlocker.join();

                // At this stage, we cannot know for certain if idle timeout
                // processing or serving of the 2nd request gets executed first.
                // If it is the latter, we will be verifying what this test aims
                // to stress. In either case, the 2nd request should not be
                // served using the timed out connection.
                var response2 = response2Future.join();
                assertEquals(200, response2.statusCode());
                assertNotEquals(
                        response1Label, response2.connectionLabel().orElseThrow(),
                        "The 1st overdue connection should not have been reused!");

            } finally {
                carrierBlockerStopped.set(true);
                carrierBlocker.join();
            }

        }
    }

    enum InfraFactory {

        H1C(false, HTTP_1_1),

        H1S(true, HTTP_1_1),

        H2C(false, HTTP_2),

        H2S(true, HTTP_2),

        H3(
                true,
                HTTP_3,
                () -> HttpTestServer.create(HTTP_3_URI_ONLY, SSL_CONTEXT),
                requestBuilder -> requestBuilder
                        .version(HTTP_3)
                        .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY));

        private final boolean secure;

        private final Version version;

        private final ThrowingSupplier<HttpTestServer> serverFactory;

        private final Consumer<HttpRequest.Builder> requestBuilderConfigurer;

        private final String handlerPath = "/idle-timeout-" + this;

        InfraFactory(boolean secure, Version version) {
            this(
                    secure,
                    version,
                    () -> HttpTestServer.create(version, secure ? SSL_CONTEXT : null),
                    requestBuilder -> requestBuilder.version(version));
        }

        InfraFactory(
                boolean secure,
                Version version,
                ThrowingSupplier<HttpTestServer> serverFactory,
                Consumer<HttpRequest.Builder> requestBuilderConfigurer) {
            this.secure = secure;
            this.version = version;
            this.serverFactory = serverFactory;
            this.requestBuilderConfigurer = requestBuilderConfigurer;
        }

        private HttpTestServer createStartedServer() throws Throwable {
            var server = serverFactory.get();
            server.addHandler(this::send200, handlerPath);
            server.start();
            return server;
        }

        private void send200(HttpTestExchange exchange) throws IOException  {
            exchange.sendResponseHeaders(200, RSPBODY_EMPTY);
        }

        private HttpRequest createRequest(HttpTestServer server) {
            var requestUri = URIBuilder.newBuilder()
                    .scheme(secure ? "https" : "http")
                    .host(server.getAddress().getAddress())
                    .port(server.getAddress().getPort())
                    .path(handlerPath)
                    .buildUnchecked();
            var requestBuilder = HttpRequest.newBuilder(requestUri);
            requestBuilderConfigurer.accept(requestBuilder);
            return requestBuilder.build();
        }

        private HttpClient createClient() {
            var clientBuilder = HttpServerAdapters.createClientBuilderFor(version)
                    .proxy(NO_PROXY);
            if (secure) {
                clientBuilder.sslContext(SSL_CONTEXT);
            }
            return clientBuilder.build();
        }

    }

    private static final SSLContext SSL_CONTEXT = SimpleSSLContext.findSSLContext();

}
