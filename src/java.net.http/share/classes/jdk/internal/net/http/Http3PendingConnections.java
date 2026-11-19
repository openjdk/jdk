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
import jdk.internal.net.http.common.Log;

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
        var sameOrigin = (altSvc != null && altSvc.originHasSameAuthority());

        ConnectionRecovery recovered = null;
        if (advertised) {
            recovered = pendingAdvertised.remove(connectionKey);
        }
        if (discovery == ALT_SVC || recovered != null) return recovered;
        if (altSvc == null) {
            // for instance, there was an exception, so we don't
            // know if there was an altSvc because conn == null
            recovered = pendingAdvertised.get(connectionKey);
            if (recovered instanceof PendingConnection pending) {
                if (pending.exchange() == origExchange) {
                    pendingAdvertised.remove(connectionKey, recovered);
                    return recovered;
                }
            }
        }
        recovered = pendingUnadvertised.get(connectionKey);
        if (recovered instanceof PendingConnection pending) {
            if (pending.exchange() == origExchange) {
                pendingUnadvertised.remove(connectionKey, recovered);
                return pending;
            }
        }
        if (!sameOrigin && advertised) return null;
        return pendingUnadvertised.remove(connectionKey);
    }

    // Lookup a ConnectionRecovery for the given request with the
    // given key.
    // Should be called while holding Http3ClientImpl.lock
    ConnectionRecovery lookupFor(String key, HttpRequestImpl request, HttpClientImpl client) {

        var discovery = request.http3Discovery();

        // if ALT_SVC only look in advertised
        if (discovery == ALT_SVC) {
            return pendingAdvertised.get(key);
        }

        // if HTTP_3_ONLY look first in pendingUnadvertised
        var unadvertised = pendingUnadvertised.get(key);
        if (discovery == HTTP_3_URI_ONLY && unadvertised != null) {
            if (unadvertised instanceof PendingConnection) {
                return unadvertised;
            }
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
        assert discovery != HTTP_3_URI_ONLY || !(unadvertised instanceof PendingConnection);
        if (discovery == HTTP_3_URI_ONLY) {
            if (advertised != null && Log.http3()) {
                Log.logHttp3("{0} cannot be used for {1}: return null", advertised, request);
            }
            assert !(unadvertised instanceof PendingConnection);
            return unadvertised;
        }

        // if ANY return advertised if found, otherwise unadvertised
        if (advertised instanceof PendingConnection) return advertised;
        if (unadvertised instanceof PendingConnection) {
            if (client.client3().isEmpty()) {
                return unadvertised;
            }
            // if ANY and we have an alt service that's eligible for the request
            // and is not same origin as the request's URI authority, then don't
            // return unadvertised and instead return advertised (which may be null)
            final AltService altSvc = client.client3().get().lookupAltSvc(request).orElse(null);
            if (altSvc != null && !altSvc.originHasSameAuthority()) {
                return advertised;
            } else {
                return unadvertised;
            }
        }
        if (advertised != null) return advertised;
        return unadvertised;
    }

    // Adds a pending connection for the given request with the given
    // key and altSvc.
    // Should be called while holding Http3ClientImpl.lock
    PendingConnection addPending(String key, HttpRequestImpl request, AltService altSvc, Exchange<?> exchange) {
        var discovery = request.http3Discovery();
        var advertised = altSvc != null && altSvc.wasAdvertised();
        var sameOrigin = altSvc == null || altSvc.originHasSameAuthority();
        // if advertised and same origin, we don't use pendingUnadvertised
        // but pendingAdvertised even if discovery is HTTP_3_URI_ONLY
        // if we have an advertised altSvc with not same origin, we still
        // want to attempt HTTP_3_URI_ONLY at origin, as an unadvertised
        // connection. If advertised & same origin, we can use the advertised
        // service instead and use pendingAdvertised, even for HTTP_3_URI_ONLY
        if (discovery == HTTP_3_URI_ONLY && (!advertised || !sameOrigin)) {
            PendingConnection pendingConnection = new PendingConnection(null, exchange);
            var previous = pendingUnadvertised.put(key, pendingConnection);
            if (previous instanceof PendingConnection prev) {
                String msg = "previous unadvertised pending connection found!"
                        + " (originally created for %s #%s) while adding pending connection for %s"
                        .formatted(prev.exchange().request, prev.exchange().multi.id, exchange.multi.id);
                if (Log.errors()) Log.logError(msg);
                assert false : msg;
            }
            return pendingConnection;
        }
        assert discovery != HTTP_3_URI_ONLY || advertised && sameOrigin;
        if (advertised) {
            PendingConnection pendingConnection = new PendingConnection(altSvc, exchange);
            var previous = pendingAdvertised.put(key, pendingConnection);
            if (previous instanceof PendingConnection prev) {
                String msg = "previous pending advertised connection found!"
                        + " (originally created for %s #%s) while adding pending connection for %s"
                        .formatted(prev.exchange().request, prev.exchange().multi.id, exchange.multi.id);
                if (Log.errors()) Log.logError(msg);
                assert false : msg;
            }
            return pendingConnection;
        }
        if (discovery == ANY) {
            assert !advertised;
            PendingConnection pendingConnection = new PendingConnection(null, exchange);
            var previous = pendingUnadvertised.put(key, pendingConnection);
            if (previous instanceof PendingConnection prev) {
                String msg = ("previous unadvertised pending connection found for ANY!" +
                        " (originally created for %s #%s) while adding pending connection for %s")
                        .formatted(prev.exchange().request, prev.exchange().multi.id, exchange.multi.id);
                if (Log.errors()) Log.logError(msg);
                assert false : msg;
            }
            return pendingConnection;
        }
        // last case - if we reach here we're ALT_SVC but couldn't
        // find an advertised alt service.
        assert discovery == ALT_SVC;
        return null;
    }
}

