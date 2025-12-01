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
package jdk.internal.net.http;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.Set;

public final class HttpClientImplAccess {

    private static final Field openedConnections; // Set<> jdk.internal.net.http.HttpClientImpl#openedConnections

    static {
        try {
            openedConnections = Class.forName("jdk.internal.net.http.HttpClientImpl")
                    .getDeclaredField("openedConnections");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpClientImplAccess() {
        throw new AssertionError();
    }

    private static HttpClientImpl impl(final HttpClient client) {
        if (client instanceof HttpClientImpl impl) return impl;
        if (client instanceof HttpClientFacade facade) return facade.impl;
        return null;
    }

    /**
     * Returns the {@code jdk.internal.net.http.HttpClientImpl#openedConnections Set}.
     * Returns null if the underlying client isn't of type jdk.internal.net.http.HttpClientImpl.
     */
    public static Set<?> getOpenedConnections(final HttpClient client)
            throws IllegalAccessException {
        Objects.requireNonNull(client, "client");
        final HttpClientImpl clientImpl = impl(client);
        if (clientImpl == null) {
            return null;
        }
        openedConnections.setAccessible(true);
        return (Set<?>) openedConnections.get(clientImpl);
    }
}
