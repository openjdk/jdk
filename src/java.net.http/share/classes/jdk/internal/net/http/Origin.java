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

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * Represents an origin server to which a HTTP request is targeted.
 *
 * @param scheme The scheme of the origin (for example: https). Unlike the application layer
 *               protocol (which can be a finer grained protocol like h2, h3 etc...),
 *               this is actually a scheme.
 * @param host   The host of the origin
 * @param port   The port of the origin
 */
public record Origin(String scheme, String host, int port) {
    public Origin {
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(host);
        if (port <= 0) {
            throw new IllegalArgumentException("Invalid port");
        }
    }

    @Override
    public String toString() {
        return scheme + "://" + toAuthority(host, port);
    }

    /**
     * {@return Creates and returns an Origin from an URI}
     *
     * @param uri The URI of the origin
     * @throws IllegalArgumentException if a Origin cannot be constructed from
     *                                  the given {@code uri}
     */
    public static Origin from(final URI uri) throws IllegalArgumentException {
        Objects.requireNonNull(uri);
        final String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("missing scheme in URI");
        }
        final String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("missing host in URI");
        }
        int port = uri.getPort();
        if (port == -1) {
            port = switch (scheme.toLowerCase(Locale.ROOT)) {
                case "http" -> 80;
                case "https" -> 443;
                default -> throw new IllegalArgumentException("Unsupported scheme: " + scheme);
            };
        }
        return new Origin(scheme, host, port);
    }

    static String toAuthority(final String host, final int port) {
        assert port > 0 : "invalid port: " + port;
        // borrowed from code in java.net.URI
        final boolean needBrackets = host.indexOf(':') >= 0
                && !host.startsWith("[")
                && !host.endsWith("]");
        if (needBrackets) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }
}
