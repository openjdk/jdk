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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.http3.ConnectionSettings;

public final class Http3ConnectionAccess {

    private static final Field openedConnections; // Set<> jdk.internal.net.http.HttpClientImpl#openedConnections

    static {
        try {
            openedConnections = Class.forName("jdk.internal.net.http.HttpClientImpl")
                    .getDeclaredField("openedConnections");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Http3ConnectionAccess() {
        throw new AssertionError();
    }

    static HttpClientImpl impl(HttpClient client) {
        if (client instanceof HttpClientImpl impl) return impl;
        if (client instanceof HttpClientFacade facade) return facade.impl;
        return null;
    }

    static HttpRequestImpl impl(HttpRequest request) {
        if (request instanceof HttpRequestImpl impl) return impl;
        return null;
    }

    public static CompletableFuture<ConnectionSettings> peerSettings(HttpClient client, HttpResponse<?> resp) {
        try {
            Http3Connection conn = impl(client)
                    .client3()
                    .get()
                    .findPooledConnectionFor(impl(resp.request()), null);
            if (conn == null) {
                return MinimalFuture.failedFuture(new NoSuchElementException("no connection found"));
            }
            return conn.peerSettingsCF();
        } catch (Exception ex) {
            return MinimalFuture.failedFuture(ex);
        }
    }

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
