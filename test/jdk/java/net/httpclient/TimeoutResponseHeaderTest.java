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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/*
 * @test id=retriesDisabled
 * @bug 8208693
 * @summary Verifies `HttpRequest::timeout` is effective for *response header*
 *          timeouts when all retry mechanisms are disabled.
 *
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build TimeoutResponseTestSupport
 *
 * @run junit/othervm
 *      -Djdk.httpclient.auth.retrylimit=0
 *      -Djdk.httpclient.disableRetryConnect
 *      -Djdk.httpclient.redirects.retrylimit=0
 *      -Dtest.requestTimeoutMillis=1000
 *      TimeoutResponseHeaderTest
 */

/*
 * @test id=retriesEnabledForResponseFailure
 * @bug 8208693
 * @summary Verifies `HttpRequest::timeout` is effective for *response header*
 *          timeouts, where some initial responses are intentionally configured
 *          to fail to trigger retries.
 *
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build TimeoutResponseTestSupport
 *
 * @run junit/othervm
 *      -Djdk.httpclient.auth.retrylimit=0
 *      -Djdk.httpclient.disableRetryConnect
 *      -Djdk.httpclient.redirects.retrylimit=3
 *      -Dtest.requestTimeoutMillis=1000
 *      -Dtest.responseFailureWaitDurationMillis=600
 *      TimeoutResponseHeaderTest
 */

/**
 * Verifies {@link HttpRequest#timeout() HttpRequest.timeout()} is effective
 * for <b>response header</b> timeouts.
 */
class TimeoutResponseHeaderTest extends TimeoutResponseTestSupport {

    private static final Logger LOGGER = Utils.getDebugLogger(
            TimeoutResponseHeaderTest.class.getSimpleName()::toString, Utils.DEBUG);

    static {
        ServerRequestPair.SERVER_HANDLER_BEHAVIOUR =
                ServerRequestPair.ServerHandlerBehaviour.BLOCK_BEFORE_HEADER_DELIVERY;
    }

    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSend(ServerRequestPair pair) throws Exception {
        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> assertThrows(
                    HttpTimeoutException.class,
                    () -> {
                        LOGGER.log("Sending the request");
                        client.send(pair.request(), HttpResponse.BodyHandlers.discarding());
                    }));
        }
    }

    @ParameterizedTest
    @MethodSource("serverRequestPairs")
    void testSendAsync(ServerRequestPair pair) throws Exception {
        try (HttpClient client = pair.createClientWithEstablishedConnection()) {
            assertTimeoutPreemptively(REQUEST_TIMEOUT.multipliedBy(2), () -> {
                LOGGER.log("Sending the request asynchronously");
                CompletableFuture<HttpResponse<Void>> responseFuture =
                        client.sendAsync(pair.request(), HttpResponse.BodyHandlers.discarding());
                Exception exception = assertThrows(ExecutionException.class, () -> {
                    LOGGER.log("Obtaining the response");
                    responseFuture.get();
                });
                assertInstanceOf(HttpTimeoutException.class, exception.getCause());
            });
        }
    }

}
