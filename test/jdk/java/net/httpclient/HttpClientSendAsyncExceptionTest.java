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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.UnsupportedProtocolVersionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8368249
 * @summary Verifies exceptions thrown by `HttpClient::sendAsync`
 * @library /test/jdk/java/net/httpclient/lib /test/lib
 * @run junit HttpClientSendAsyncExceptionTest
 */

class HttpClientSendAsyncExceptionTest {

    @Test
    void testClosedClient() {
        var client = HttpClient.newHttpClient();
        client.close();
        var request = HttpRequest.newBuilder(URI.create("https://example.com")).GET().build();
        var responseBodyHandler = HttpResponse.BodyHandlers.discarding();
        var responseFuture = client.sendAsync(request, responseBodyHandler);
        var exception = assertThrows(ExecutionException.class, responseFuture::get);
        var cause = assertThrowableInstanceOf(IOException.class, exception.getCause());
        assertContains(cause.getMessage(), "closed");
    }

    @Test
    void testH3IncompatClient() {
        SSLParameters h3IncompatSslParameters = new SSLParameters(new String[0], new String[]{"foo"});
        try (var h3IncompatClient = HttpClient.newBuilder()
                // Provide `SSLParameters` incompatible with QUIC's TLS requirements to disarm the HTTP/3 support
                .sslParameters(h3IncompatSslParameters)
                .build()) {
            var h3Request = HttpRequest.newBuilder(URI.create("https://example.com"))
                    .GET()
                    .version(HttpClient.Version.HTTP_3)
                    .setOption(HttpOption.H3_DISCOVERY, HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY)
                    .build();
            var responseBodyHandler = HttpResponse.BodyHandlers.discarding();
            var responseFuture = h3IncompatClient.sendAsync(h3Request, responseBodyHandler);
            var exception = assertThrows(ExecutionException.class, responseFuture::get);
            var cause = assertThrowableInstanceOf(UnsupportedProtocolVersionException.class, exception.getCause());
            assertEquals("HTTP3 is not supported", cause.getMessage());
        }
    }

    @Test
    void testConnectMethod() {
        try (var client = HttpClient.newHttpClient()) {
            // The default `HttpRequest` builder does not allow `CONNECT`.
            // Hence, we create our custom `HttpRequest` instance:
            var connectRequest = new HttpRequest() {

                @Override
                public Optional<BodyPublisher> bodyPublisher() {
                    return Optional.empty();
                }

                @Override
                public String method() {
                    return "CONNECT";
                }

                @Override
                public Optional<Duration> timeout() {
                    return Optional.empty();
                }

                @Override
                public boolean expectContinue() {
                    return false;
                }

                @Override
                public URI uri() {
                    return URI.create("https://example.com");
                }

                @Override
                public Optional<HttpClient.Version> version() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Collections.emptyMap(), (_, _) -> true);
                }

            };
            var responseBodyHandler = HttpResponse.BodyHandlers.discarding();
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> client.sendAsync(connectRequest, responseBodyHandler));
            assertContains(exception.getMessage(), "Unsupported method CONNECT");
        }
    }

    static List<ExceptionTestCase> exceptionTestCases() {

        // `RuntimeException`
        List<ExceptionTestCase> testCases = new ArrayList<>();
        var runtimeException = new RuntimeException();
        testCases.add(new ExceptionTestCase(
                "RuntimeException",
                _ -> { throw runtimeException; },
                exception -> {
                    assertThrowableInstanceOf(IOException.class, exception);
                    assertThrowableSame(runtimeException, exception.getCause());
                }));

        // `Error`
        var error = new Error();
        testCases.add(new ExceptionTestCase(
                "Error",
                _ -> { throw error; },
                exception -> assertThrowableSame(error, exception)));

        // `CancellationException`
        var cancellationException = new CancellationException();
        testCases.add(new ExceptionTestCase(
                "CancellationException",
                _ -> { throw cancellationException; },
                exception -> assertThrowableSame(cancellationException, exception)));

        // `IOException` (needs sneaky throw)
        var ioException = new IOException();
        testCases.add(new ExceptionTestCase(
                "IOException",
                _ -> { sneakyThrow(ioException); throw new AssertionError(); },
                exception -> assertThrowableSame(ioException, exception)));

        // `UncheckedIOException`
        var uncheckedIOException = new UncheckedIOException(ioException);
        testCases.add(new ExceptionTestCase(
                "UncheckedIOException(IOException)",
                _ -> { throw uncheckedIOException; },
                exception -> assertThrowableSame(uncheckedIOException, exception.getCause())));

        return testCases;

    }

    private static <T extends Throwable> T assertThrowableInstanceOf(Class<T> expectedClass, Throwable actual) {
        if (!expectedClass.isInstance(actual)) {
            var message = "Was expecting `%s`".formatted(expectedClass.getCanonicalName());
            throw new AssertionError(message, actual);
        }
        return expectedClass.cast(actual);
    }

    private static void assertThrowableSame(Throwable expected, Throwable actual) {
        if (expected != actual) {
            var message = "Was expecting `%s`".formatted(expected.getClass().getCanonicalName());
            throw new AssertionError(message, actual);
        }
    }

    private record ExceptionTestCase(
            String description,
            HttpResponse.BodyHandler<Void> throwingResponseBodyHandler,
            Consumer<Throwable> exceptionVerifier) {

        @Override
        public String toString() {
            return description;
        }

    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    @ParameterizedTest
    @MethodSource("exceptionTestCases")
    void testIOExceptionWrap(ExceptionTestCase testCase, TestInfo testInfo) throws Exception {
        var version = HttpClient.Version.HTTP_1_1;
        try (var server = HttpServerAdapters.HttpTestServer.create(version);
             var client = HttpServerAdapters.createClientBuilderFor(version).proxy(NO_PROXY).build()) {

            // Configure the server to respond with 200 containing a single byte
            var serverHandlerPath = "/%s/%s/".formatted(
                    testInfo.getTestClass().map(Class::getSimpleName).orElse("unknown-class"),
                    testInfo.getTestMethod().map(Method::getName).orElse("unknown-method"));
            HttpServerAdapters.HttpTestHandler serverHandler = exchange -> {
                try (exchange) {
                    exchange.sendResponseHeaders(200, 1);
                    exchange.getResponseBody().write(new byte[]{0});
                }
            };
            server.addHandler(serverHandler, serverHandlerPath);
            server.start();

            // Verify the execution failure
            var requestUri = URI.create("http://" + server.serverAuthority() + serverHandlerPath);
            var request = HttpRequest.newBuilder(requestUri).version(version).build();
            // We need to make `sendAsync()` execution fail.
            // There are several ways to achieve this.
            // We choose to use a throwing response handler.
            var responseFuture = client.sendAsync(request, testCase.throwingResponseBodyHandler);
            var exception = assertThrows(ExecutionException.class, responseFuture::get);
            testCase.exceptionVerifier.accept(exception.getCause());

        }

    }

    private static void assertContains(String target, String expected) {
        assertTrue(target.contains(expected), "does not contain `" + expected + "`: " + target);
    }

}
