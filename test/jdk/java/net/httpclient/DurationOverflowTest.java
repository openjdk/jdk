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

import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestEchoHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static jdk.httpclient.test.lib.common.HttpServerAdapters.createClientBuilderFor;

/*
 * @test id=withoutPropertyConfig
 * @bug 8368528
 * @summary Verifies that `Duration`-accepting programmatic public APIs, either
 *          individually, or in combination, work with arbitrarily large values
 *
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 *
 * @run junit DurationOverflowTest
 */

/*
 * @test id=withPropertyConfig
 * @bug 8368528
 * @summary Verifies that `Duration`-accepting programmatic public APIs, either
 *          individually, or in combination, work with arbitrarily large values
 *          when combined with duration-accepting property-based public APIs.
 *
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 *
 * @comment 9223372036854775807 is the value of `Long.MAX_VALUE`
 *
 * @run junit/othervm
 *      -Djdk.httpclient.keepalive.timeout=9223372036854775807
 *      DurationOverflowTest
 *
 * @comment `h3` infra is also enabled for this test since `j.h.k.timeout.h3`
 *          defaults to `j.h.k.timeout.h2`
 * @run junit/othervm
 *      -Djdk.httpclient.keepalive.timeout.h2=9223372036854775807
 *      -DallowedInfras=h2,h2s,h3
 *      DurationOverflowTest
 *
 * @run junit/othervm
 *      -Djdk.httpclient.keepalive.timeout.h3=9223372036854775807
 *      -DallowedInfras=h3
 *      DurationOverflowTest
 */

public class DurationOverflowTest {

    private static final String CLASS_NAME = DurationOverflowTest.class.getSimpleName();

    private static final Logger LOGGER = Utils.getDebugLogger(CLASS_NAME::toString, Utils.DEBUG);

    private static final SSLContext SSL_CONTEXT = SimpleSSLContext.findSSLContext();

    private static final List<Infra> INFRAS = loadInfras();

    private static final class Infra implements AutoCloseable {

        private static final AtomicInteger SERVER_COUNTER = new AtomicInteger();

        private final String serverId;

        private final HttpTestServer server;

        private final Supplier<HttpClient.Builder> clientBuilderSupplier;

        private final Supplier<HttpRequest.Builder> requestBuilderSupplier;

        private final boolean secure;

        private Infra(
                String serverId,
                HttpTestServer server,
                Supplier<HttpClient.Builder> clientBuilderSupplier,
                Supplier<HttpRequest.Builder> requestBuilderSupplier,
                boolean secure) {
            this.serverId = serverId;
            this.server = server;
            this.clientBuilderSupplier = clientBuilderSupplier;
            this.requestBuilderSupplier = requestBuilderSupplier;
            this.secure = secure;
        }

        private static Infra of(Version version, boolean secure) {

            // Create the server and the request URI
            var sslContext = secure ? SSL_CONTEXT : null;
            var server = createServer(version, sslContext);
            server.getVersion();
            var handlerPath = "/%s/".formatted(CLASS_NAME);
            var requestUriScheme = secure ? "https" : "http";
            var requestUri = URI.create("%s://%s%s-".formatted(requestUriScheme, server.serverAuthority(), handlerPath));

            // Register the request handler
            var serverId = "" + SERVER_COUNTER.getAndIncrement();
            server.addHandler(
                    // Intentionally opting for receiving a body to cover code paths associated with its retrieval
                    new HttpTestEchoHandler(false),
                    handlerPath);

            // Create client & request builders
            Supplier<HttpClient.Builder> clientBuilderSupplier =
                    () -> createClientBuilderFor(version)
                            .version(version)
                            .sslContext(SSL_CONTEXT)
                            .proxy(NO_PROXY);
            Supplier<HttpRequest.Builder> requestBuilderSupplier =
                    () -> createRequestBuilder(requestUri, version);

            // Create the pair
            var pair = new Infra(serverId, server, clientBuilderSupplier, requestBuilderSupplier, secure);
            pair.server.start();
            LOGGER.log("Server[%s] is started at `%s`", serverId, server.serverAuthority());
            return pair;

        }

        private static HttpTestServer createServer(Version version, SSLContext sslContext) {
            try {
                return switch (version) {
                    case HTTP_1_1, HTTP_2 -> HttpTestServer.create(version, sslContext, null);
                    case HTTP_3 -> HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, null);
                };
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static HttpRequest.Builder createRequestBuilder(URI uri, Version version) {
            var requestBuilder = HttpRequest.newBuilder(uri).version(version).HEAD();
            if (Version.HTTP_3.equals(version)) {
                requestBuilder.setOption(HttpOption.H3_DISCOVERY, HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY);
            }
            return requestBuilder;
        }

        @Override
        public void close() {
            LOGGER.log("Server[%s] is stopping", serverId);
            server.stop();
        }

        @Override
        public String toString() {
            var version = server.getVersion();
            var versionString = version.toString();
            return switch (version) {
                case HTTP_1_1, HTTP_2 -> secure ? versionString.replaceFirst("_", "S_") : versionString;
                case HTTP_3 -> versionString;
            };
        }

    }

    private static List<Infra> loadInfras() {
        return Stream
                .of(System.getProperty("allowedInfras", "h1,h1s,h2,h2s,h3").split(","))
                .map(infra -> {
                    LOGGER.log("Loading test infrastructure: `%s`", infra);
                    return switch (infra) {
                        case "h1" -> Infra.of(Version.HTTP_1_1, false);
                        case "h1s" -> Infra.of(Version.HTTP_1_1, true);
                        case "h2" -> Infra.of(Version.HTTP_2, false);
                        case "h2s" -> Infra.of(Version.HTTP_2, true);
                        case "h3" -> Infra.of(Version.HTTP_3, true);
                        default -> throw new IllegalArgumentException("Unknown test infrastructure: " + infra);
                    };
                })
                .toList();
    }

    @AfterAll
    static void tearDownInfras() {
        LOGGER.log("Tearing down test infrastructure");
        Exception[] exceptionRef = {null};
        infras().forEach(infra -> {
            try {
                infra.close();
            } catch (Exception exception) {
                if (exceptionRef[0] == null) {
                    exceptionRef[0] = exception;
                } else {
                    exceptionRef[0].addSuppressed(exception);
                }
            }
        });
        if (exceptionRef[0] != null) {
            throw new RuntimeException("Failed tearing down one or more test infrastructures", exceptionRef[0]);
        }
    }

    private static Stream<Infra> infras() {
        return INFRAS.stream();
    }

    public static final Set<Duration> EXCESSIVE_DURATIONS = Set.of(
            Duration.MAX,
            // This triggers different exceptions than the ones triggered by `Duration.MAX`
            Duration.ofMillis(Long.MAX_VALUE));

    private static Stream<InfraDurationPair> infraDurationPairs() {
        return infras().flatMap(infra -> EXCESSIVE_DURATIONS.stream()
                .map(duration -> new InfraDurationPair(infra, duration)));
    }

    private record InfraDurationPair(Infra infra, Duration duration) {}

    @ParameterizedTest
    @MethodSource("infraDurationPairs")
    void testClientConnectTimeout(InfraDurationPair pair) throws Exception {
        testConfig(pair.infra, clientBuilder -> clientBuilder.connectTimeout(pair.duration), null);
    }

    @ParameterizedTest
    @MethodSource("infraDurationPairs")
    void testRequestTimeout(InfraDurationPair pair) throws Exception {
        testConfig(pair.infra, null, requestBuilder -> requestBuilder.timeout(pair.duration));
    }

    private static Stream<InfraDurationDurationTriple> infraDurationDurationTriples() {
        return infras().flatMap(infra -> EXCESSIVE_DURATIONS.stream()
                .flatMap(duration1 -> EXCESSIVE_DURATIONS.stream()
                        .map(duration2 -> new InfraDurationDurationTriple(infra, duration1, duration2))));
    }

    private record InfraDurationDurationTriple(Infra infra, Duration duration1, Duration duration2) {}

    @ParameterizedTest
    @MethodSource("infraDurationDurationTriples")
    void testClientConnectTimeoutAndRequestTimeout(InfraDurationDurationTriple triple) throws Exception {
        testConfig(
                triple.infra,
                clientBuilder -> clientBuilder.connectTimeout(triple.duration1),
                requestBuilder -> requestBuilder.timeout(triple.duration2));
    }

    private static void testConfig(
            Infra infra,
            Consumer<HttpClient.Builder> clientBuilderConsumer,
            Consumer<HttpRequest.Builder> requestBuilderConsumer)
            throws Exception {

        // Create the client
        var clientBuilder = infra.clientBuilderSupplier.get();
        if (clientBuilderConsumer != null) {
            clientBuilderConsumer.accept(clientBuilder);
        }
        try (var client = clientBuilder.build()) {

            // Create the request
            byte[] expectedBytes = "abc".repeat(8192).getBytes(US_ASCII);
            var requestBuilder = infra.requestBuilderSupplier.get()
                    // Intentionally opting for sending a body to cover code paths associated with its delivery
                    .POST(BodyPublishers.ofByteArray(expectedBytes));
            if (requestBuilderConsumer != null) {
                requestBuilderConsumer.accept(requestBuilder);
            }
            var request = requestBuilder.build();

            // Execute the request.
            // Doing it twice to touch code paths before & after a protocol upgrade, if present.
            for (int requestIndex = 0; requestIndex < 2; requestIndex++) {
                LOGGER.log("Executing request (attempt=%s)", requestIndex + 1);
                var response = client.send(request, BodyHandlers.ofByteArray());

                // Verify the response status code
                if (response.statusCode() != 200) {
                    var message = String.format(
                            "Unexpected status code: %s (attempt=%s)",
                            response.statusCode(), requestIndex + 1);
                    throw new AssertionError(message);
                }

                // Verify the response body
                int mismatchIndex = Arrays.mismatch(expectedBytes, response.body());
                if (mismatchIndex > 0) {
                    var message = String.format(
                            "Body mismatch at index %s (attempt=%s)",
                            mismatchIndex, requestIndex + 1);
                    throw new AssertionError(message);
                }

            }

        }

    }

}
