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

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test id=retriesDisabled
 * @bug 8208693
 * @summary Verifies `HttpRequest::timeout` is effective for *response body*
 *          timeouts when all retry mechanisms are disabled.
 *
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build TimeoutResponseTestSupport
 *
 * @run junit/othervm
 *      -Djdk.httpclient.auth.retrylimit=0
 *      -Djdk.httpclient.disableRetryConnect
 *      -Djdk.httpclient.redirects.retrylimit=0
 *      -Dtest.requestTimeoutMillis=1000
 *      TimeoutResponseBodyTest
 */

/*
 * @test id=retriesEnabledForResponseFailure
 * @bug 8208693
 * @summary Verifies `HttpRequest::timeout` is effective for *response body*
 *          timeouts, where some initial responses are intentionally configured
 *          to fail to trigger retries.
 *
 * @library /test/lib
 *          /test/jdk/java/net/httpclient/lib
 * @build TimeoutResponseTestSupport
 *
 * @run junit/othervm
 *      -Djdk.httpclient.auth.retrylimit=0
 *      -Djdk.httpclient.disableRetryConnect
 *      -Djdk.httpclient.redirects.retrylimit=3
 *      -Dtest.requestTimeoutMillis=1000
 *      -Dtest.responseFailureWaitDurationMillis=600
 *      TimeoutResponseBodyTest
 */

/**
 * Verifies {@link HttpRequest#timeout() HttpRequest.timeout()} is effective
 * for <b>response body</b> timeouts.
 *
 * @implNote
 *
 * Using a response body subscriber (i.e., {@link InputStream}) of type that
 * allows gradual consumption of the response body after successfully building
 * an {@link HttpResponse} instance to ensure timeouts are propagated even
 * after the {@code HttpResponse} construction.
 * <p>
 * Each test is provided a pristine ephemeral client to avoid any unexpected
 * effects due to pooling.
 */
class TimeoutResponseBodyTest extends TimeoutResponseTestSupport {

    private static final Logger LOGGER = Utils.getDebugLogger(
            TimeoutResponseBodyTest.class.getSimpleName()::toString, Utils.DEBUG);

    /**
     * Tests timeouts using
     * {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler) HttpClient::send}
     * against a server blocking without delivering the response body.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSendOnMissingBody(ServerRequestPair pair) throws Exception {

        ServerRequestPair.SERVER_HANDLER_BEHAVIOUR =
                ServerRequestPair.ServerHandlerBehaviour.BLOCK_BEFORE_BODY_DELIVERY;

        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> {
                LOGGER.log("Sending the request");
                HttpResponse<InputStream> response = client.send(
                        pair.request(), HttpResponse.BodyHandlers.ofInputStream());
                LOGGER.log("Consuming the obtained response");
                verifyResponseBodyDoesNotArrive(response);
            });
        }

    }

    /**
     * Tests timeouts using
     * {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler) HttpClient::sendAsync}
     * against a server blocking without delivering the response body.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSendAsyncOnMissingBody(ServerRequestPair pair) throws Exception {

        ServerRequestPair.SERVER_HANDLER_BEHAVIOUR =
                ServerRequestPair.ServerHandlerBehaviour.BLOCK_BEFORE_BODY_DELIVERY;

        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> {
                LOGGER.log("Sending the request asynchronously");
                CompletableFuture<HttpResponse<InputStream>> responseFuture = client.sendAsync(
                        pair.request(), HttpResponse.BodyHandlers.ofInputStream());
                LOGGER.log("Obtaining the response");
                HttpResponse<InputStream> response = responseFuture.get();
                LOGGER.log("Consuming the obtained response");
                verifyResponseBodyDoesNotArrive(response);
            });
        }

    }

    private static void verifyResponseBodyDoesNotArrive(HttpResponse<InputStream> response) {
        assertEquals(200, response.statusCode());
        IOException exception = assertThrows(
                IOException.class,
                () -> {
                    try (InputStream responseBodyStream = response.body()) {
                        int readByte = responseBodyStream.read();
                        fail("Unexpected read byte: " + readByte);
                    }
                });
        if (!(exception.getCause() instanceof HttpTimeoutException)) {
            throw new AssertionError("was expecting a cause of type `HttpTimeoutException`", exception);
        }
    }

    /**
     * Tests timeouts using
     * {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler) HttpClient::send}
     * against a server delivering the response body very slowly.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSendOnSlowBody(ServerRequestPair pair) throws Exception {

        ServerRequestPair.SERVER_HANDLER_BEHAVIOUR =
                ServerRequestPair.ServerHandlerBehaviour.DELIVER_BODY_SLOWLY;

        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> {
                LOGGER.log("Sending the request");
                HttpResponse<InputStream> response = client.send(
                        pair.request(), HttpResponse.BodyHandlers.ofInputStream());
                LOGGER.log("Consuming the obtained response");
                verifyResponseBodyArrivesSlow(response);
            });
        }

    }

    /**
     * Tests timeouts using
     * {@link HttpClient#sendAsync(HttpRequest, HttpResponse.BodyHandler) HttpClient::sendAsync}
     * against a server delivering the response body very slowly.
     */
    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSendAsyncOnSlowBody(ServerRequestPair pair) throws Exception {

        ServerRequestPair.SERVER_HANDLER_BEHAVIOUR =
                ServerRequestPair.ServerHandlerBehaviour.DELIVER_BODY_SLOWLY;

        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> {
                LOGGER.log("Sending the request asynchronously");
                CompletableFuture<HttpResponse<InputStream>> responseFuture = client.sendAsync(
                        pair.request(), HttpResponse.BodyHandlers.ofInputStream());
                LOGGER.log("Obtaining the response");
                HttpResponse<InputStream> response = responseFuture.get();
                LOGGER.log("Consuming the obtained response");
                verifyResponseBodyArrivesSlow(response);
            });
        }

    }

    private static void verifyResponseBodyArrivesSlow(HttpResponse<InputStream> response) {
        assertEquals(200, response.statusCode());
        IOException exception = assertThrows(
                IOException.class,
                () -> {
                    try (InputStream responseBodyStream = response.body()) {
                        int i = 0;
                        int l = ServerRequestPair.CONTENT_LENGTH;
                        for (; i < l; i++) {
                            LOGGER.log("Reading byte %s/%s", i, l);
                            int readByte = responseBodyStream.read();
                            if (readByte < 0) {
                                break;
                            }
                            assertEquals(i, readByte);
                        }
                        fail("Should not have reached here! (i=%s)".formatted(i));
                    }
                });
        if (!(exception.getCause() instanceof HttpTimeoutException)) {
            throw new AssertionError("was expecting a cause of type `HttpTimeoutException`", exception);
        }
    }

}
