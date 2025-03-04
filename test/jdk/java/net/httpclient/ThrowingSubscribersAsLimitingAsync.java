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
 *          the request is performed _asynchronously_ using `HttpClient::sendAsync`.
 *          Even though throwing exceptions at such points is against the
 *          Reactive Streams contract, we make sure that `HttpClient` gets its
 *          resources released on such misbehaving user code.
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @build AbstractThrowingSubscribers
 *        ReferenceTracker
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true ThrowingSubscribersAsLimitingAsync
 */

import org.testng.annotations.Test;

public class ThrowingSubscribersAsLimitingAsync extends ThrowingSubscribersAsLimiting {

    @Override
    @Test(dataProvider = "variants")
    public void test(String uri, boolean sameClient, Thrower thrower) throws Exception {
        test(uri, sameClient, thrower, true);
    }

}
