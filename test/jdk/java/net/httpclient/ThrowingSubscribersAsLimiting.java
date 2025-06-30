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

/*
 * @test
 * @summary Verifies the behavior of `limiting()` on unexpected exceptions
 *          thrown at various places (`onSubscriber()`, `onNext()`, etc.) when
 *          the request is performed _synchronously_ using `HttpClient::send`.
 *          Even though throwing exceptions at such points is against the
 *          Reactive Streams contract, we make sure that `HttpClient` gets its
 *          resources released on such misbehaving user code.
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build AbstractThrowingSubscribers
 *        ReferenceTracker
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true ThrowingSubscribersAsLimiting
 */

import org.testng.annotations.Test;

import java.net.http.HttpResponse;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ThrowingSubscribersAsLimiting extends AbstractThrowingSubscribers {

    @Test(dataProvider = "variants")
    public void test(String uri, boolean sameClient, Thrower thrower) throws Exception {
        test(uri, sameClient, thrower, false);
    }

    void test(String uri, boolean sameClient, Thrower thrower, boolean async) throws Exception {
        uri = uri + "-" + URICOUNT.incrementAndGet();
        String name = String.format(
                "testThrowingAsLimiting%s(%s, %b, %s)",
                (async ? "Async" : ""), uri, sameClient, thrower);
        Supplier<HttpResponse.BodyHandler<Stream<String>>> handlerSupplier =
                () -> HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofLines(),
                        Long.MAX_VALUE);
        testThrowing(
                name,
                uri,
                sameClient,
                handlerSupplier,
                this::finish,
                thrower,
                async,
                excludes(SubscriberType.OFFLINE));
    }

    private Void finish(Where where, HttpResponse<Stream<String>> response, Thrower thrower) {
        switch (where) {
            case BODY_HANDLER:
            case GET_BODY:
            case BODY_CF:
                return shouldHaveThrown(where, response, thrower);
        }
        try {
            // noinspection ResultOfMethodCallIgnored
            response.body().toList();
        } catch (Error | Exception throwable) {
            Throwable cause = findCause(throwable, thrower);
            if (cause != null) {
                System.out.println(now() + "Got expected exception in " + where + ": " + cause);
                return null;
            }
            throw causeNotFound(where, throwable);
        }
        return shouldHaveThrown(where, response, thrower);
    }

}
