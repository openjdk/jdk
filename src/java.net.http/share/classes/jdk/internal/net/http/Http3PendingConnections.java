/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.net.http.AltServicesRegistry.AltService;
import jdk.internal.net.http.Http3ClientImpl.ConnectionRecovery;
import jdk.internal.net.http.Http3ClientImpl.PendingConnection;
import jdk.internal.net.http.Http3ClientImpl.StreamLimitReached;

import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/**
 * This class keeps track of pending HTTP/3 connections
 * to avoid making two connections to the same server
 * in parallel. Methods in this class are not atomic.
 * Therefore, it is expected that they will be called
 * while holding a lock in order to ensure atomicity.
 */
class Http3PendingConnections {

    private final Map<String,ConnectionRecovery> pendingAdvertised = new ConcurrentHashMap<>();
    private final Map<String,ConnectionRecovery> pendingUnadvertised = new ConcurrentHashMap<>();

    Http3PendingConnections() {}


    // Called when recovery is needed for a given connection, with
    // the request that got the StreamLimitException
    // Should be called while holding Http3ClientImpl.lock
    void streamLimitReached(String key, Http3Connection connection) {
        var altSvc = connection.connection().getSourceAltService().orElse(null);
        var advertised = altSvc != null && altSvc.wasAdvertised();
        var queue = advertised ? pendingAdvertised : pendingUnadvertised;
        queue.computeIfAbsent(key, k -> new StreamLimitReached(connection));
    }

    // Remove a ConnectionRecovery after the connection was established
    // Should be called while holding Http3ClientImpl.lock
    ConnectionRecovery removeCompleted(String connectionKey, Exchange<?> origExchange, Http3Connection conn) {
        var altSvc = Optional.ofNullable(conn)
                .map(Http3Connection::connection)
                .flatMap(HttpQuicConnection::getSourceAltService)
                .orElse(null);
        var discovery = Optional.ofNullable(origExchange)
                .map(Exchange::request)
                .map(HttpRequestImpl::http3Discovery)
                .orElse(null);
        var advertised = (altSvc != null && altSvc.wasAdvertised())
                || discovery == ALT_SVC;
        if (advertised) {
            return pendingAdvertised.remove(connectionKey);
        } else {
            return pendingUnadvertised.remove(connectionKey);
        }
    }

    // Lookup a ConnectionRecovery for the given request with the
    // given key.
    // Should be called while holding Http3ClientImpl.lock
    ConnectionRecovery lookupFor(String key, HttpRequestImpl request) {

        var discovery = request.http3Discovery();

        // if ALT_SVC only look in advertised
        if (discovery == ALT_SVC) {
            return pendingAdvertised.get(key);
        }

        // if HTTP_3_ONLY look first in pendingUnadvertised
        var unadvertised = pendingUnadvertised.get(key);
        if (discovery == HTTP_3_URI_ONLY && unadvertised != null) {
            return unadvertised;
        }

        // then look in advertised
        var advertised = pendingAdvertised.get(key);
        if (advertised instanceof PendingConnection pending) {
            var altSvc = pending.altSvc();
            var sameOrigin = altSvc != null &&  altSvc.originHasSameAuthority();
            assert altSvc != null; // pending advertised should have altSvc
            if (discovery == ANY || sameOrigin) return advertised;
        }

        // if HTTP_3_ONLY, nothing found, stop here
        assert discovery != HTTP_3_URI_ONLY || unadvertised == null;
        if (discovery == HTTP_3_URI_ONLY) return null;

        // if ANY return advertised if found, otherwise unadvertised
        if (advertised != null) return advertised;
        return unadvertised;
    }

    // Adds a pending connection for the given request with the given
    // key and altSvc.
    // Should be called while holding Http3ClientImpl.lock
    PendingConnection addPending(String key, HttpRequestImpl request, AltService altSvc) {
        var discovery = request.http3Discovery();
        var advertised = altSvc != null && altSvc.wasAdvertised();
        var sameOrigin = altSvc == null || altSvc.originHasSameAuthority();
        // if advertised and same origin, we don't use pendingUnadvertised
        // but pendingAdvertised even if discovery is HTTP_3_URI_ONLY
        if (discovery == HTTP_3_URI_ONLY && (!advertised || !sameOrigin)) {
            PendingConnection pendingConnection = new PendingConnection(null);
            pendingUnadvertised.put(key, pendingConnection);
            return pendingConnection;
        }
        assert discovery != HTTP_3_URI_ONLY || advertised && sameOrigin;
        if (advertised) {
            PendingConnection pendingConnection = new PendingConnection(altSvc);
            pendingAdvertised.put(key, pendingConnection);
            return pendingConnection;
        }
        if (discovery == ANY) {
            assert !advertised;
            PendingConnection pendingConnection = new PendingConnection(null);
            pendingUnadvertised.put(key, pendingConnection);
            return pendingConnection;
        }
        // last case - if we reach here we're ALT_SVC but couldn't
        // find an advertised alt service.
        assert discovery == ALT_SVC;
        return null;
    }
}

