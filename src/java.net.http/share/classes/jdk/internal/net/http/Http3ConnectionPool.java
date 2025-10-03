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

import java.net.http.HttpOption.Http3DiscoveryMode;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.net.http.common.Logger;

import static java.net.http.HttpOption.Http3DiscoveryMode.ALT_SVC;
import static java.net.http.HttpOption.Http3DiscoveryMode.ANY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;

/**
 * This class encapsulate the HTTP/3 connection pool managed
 * by an instance of {@link Http3ClientImpl}.
 */
class Http3ConnectionPool {
    /* Map key is "scheme:host:port" */
    private final Map<String,Http3Connection> advertised = new ConcurrentHashMap<>();
    /* Map key is "scheme:host:port" */
    private final Map<String,Http3Connection> unadvertised = new ConcurrentHashMap<>();

    private final Logger debug;
    Http3ConnectionPool(Logger logger) {
        this.debug = Objects.requireNonNull(logger);
    }

    // https:<host>:<port>
    String connectionKey(HttpRequestImpl request) {
        var uri = request.uri();
        var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        var host = uri.getHost();
        var port = uri.getPort();
        assert scheme.equals("https");
        if (port < 0) port = 443; // https
        return String.format("%s:%s:%d", scheme, host, port);
    }

    private Http3Connection lookupUnadvertised(String key, Http3DiscoveryMode discoveryMode) {
        var unadvertisedConn = unadvertised.get(key);
        if (unadvertisedConn == null) return null;
        if (discoveryMode == ANY) return unadvertisedConn;
        if (discoveryMode == ALT_SVC) return null;

        assert discoveryMode == HTTP_3_URI_ONLY : String.valueOf(discoveryMode);

        // Double check that if there is an alt service, it has same origin.
        final var altService = Optional.ofNullable(unadvertisedConn)
                .map(Http3Connection::connection)
                .flatMap(HttpQuicConnection::getSourceAltService)
                .orElse(null);

        if (altService == null || altService.originHasSameAuthority()) {
            return unadvertisedConn;
        }

        // We should never come here.
        assert false : "unadvertised connection with different origin: %s -> %s"
                .formatted(key, altService);
        return null;
    }

    Http3Connection lookupFor(HttpRequestImpl request) {
        var discoveryMode = request.http3Discovery();
        var key = connectionKey(request);

        Http3Connection unadvertisedConn = null;
        // If not ALT_SVC, we can use unadvertised connections
        if (discoveryMode != ALT_SVC) {
            unadvertisedConn = lookupUnadvertised(key, discoveryMode);
            if (unadvertisedConn != null && discoveryMode == HTTP_3_URI_ONLY) {
                if (debug.on()) {
                    debug.log("Direct HTTP/3 connection found for %s in connection pool %s",
                            discoveryMode, unadvertisedConn.connection().label());
                }
                return unadvertisedConn;
            }
        }

        // Then see if we have a connection which was advertised.
        var advertisedConn = advertised.get(key);
        // We can use it for HTTP3_URI_ONLY too if it has same origin
        if (advertisedConn != null) {
            final var altService = advertisedConn.connection()
                    .getSourceAltService().orElse(null);
            assert altService != null && altService.wasAdvertised();
            switch (discoveryMode) {
                case ANY -> {
                    return advertisedConn;
                }
                case ALT_SVC -> {
                    if (debug.on()) {
                        debug.log("HTTP/3 connection found for %s in connection pool %s",
                                discoveryMode, advertisedConn.connection().label());
                    }
                    return advertisedConn;
                }
                case HTTP_3_URI_ONLY -> {
                    if (altService != null && altService.originHasSameAuthority()) {
                        if (debug.on()) {
                            debug.log("Same authority HTTP/3 connection found for %s in connection pool %s",
                                    discoveryMode, advertisedConn.connection().label());
                        }
                        return advertisedConn;
                    }
                }
            }
        }

        if (unadvertisedConn != null) {
            assert discoveryMode != ALT_SVC;
            if (debug.on()) {
                debug.log("Direct HTTP/3 connection found for %s in connection pool %s",
                        discoveryMode, unadvertisedConn.connection().label());
            }
            return unadvertisedConn;
        }

        // do not log here: this produces confusing logs as this method
        // can be called several times when trying to establish a
        // connection, when no connection is found in the pool
        return null;
    }

    Http3Connection putIfAbsent(String key, Http3Connection c) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(c);
        assert key.equals(c.key());
        var altService = c.connection().getSourceAltService().orElse(null);
        if (altService != null && altService.wasAdvertised()) {
            return advertised.putIfAbsent(key, c);
        }
        assert altService == null || altService.originHasSameAuthority();
        return unadvertised.putIfAbsent(key, c);
    }

    Http3Connection put(String key, Http3Connection c) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(c);
        assert key.equals(c.key()) : "key mismatch %s -> %s"
                .formatted(key, c.key());
        var altService = c.connection().getSourceAltService().orElse(null);
        if (altService != null && altService.wasAdvertised()) {
            return advertised.put(key, c);
        }
        assert altService == null || altService.originHasSameAuthority();
        return unadvertised.put(key, c);
    }

    boolean remove(String key, Http3Connection c) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(c);
        assert key.equals(c.key()) : "key mismatch %s -> %s"
                .formatted(key, c.key());

        var altService = c.connection().getSourceAltService().orElse(null);
        if (altService != null && altService.wasAdvertised()) {
            boolean remUndavertised = unadvertised.remove(key, c);
            assert !remUndavertised
                    : "advertised connection found in unadvertised pool for " + key;
            return advertised.remove(key, c);
        }

        assert altService == null || altService.originHasSameAuthority();
        return unadvertised.remove(key, c);
    }

    void clear() {
        advertised.clear();
        unadvertised.clear();
    }

    java.util.stream.Stream<Http3Connection> values() {
        return java.util.stream.Stream.concat(
                advertised.values().stream(),
                unadvertised.values().stream());
    }

}

