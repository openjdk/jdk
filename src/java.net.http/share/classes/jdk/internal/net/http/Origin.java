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

import sun.net.util.IPAddressUtil;

/**
 * Represents an origin server to which a HTTP request is targeted.
 *
 * @param scheme The scheme of the origin (for example: https). Unlike the application layer
 *               protocol (which can be a finer grained protocol like h2, h3 etc...),
 *               this is actually a scheme. Only {@code http} and {@code https} literals are
 *               supported. Cannot be null.
 * @param host   The host of the origin, cannot be null. If the host is an IPv6 address,
 *               then it must not be enclosed in square brackets ({@code '['} and {@code ']'}).
 *               If the host is a DNS hostname, then it must be passed as a lower case String.
 * @param port   The port of the origin. Must be greater than 0.
 */
public record Origin(String scheme, String host, int port) {
    public Origin {
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(host);
        if (!isValidScheme(scheme)) {
            throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            throw new IllegalArgumentException("Invalid host: " + host);
        }
        // expect DNS hostname to be passed as lower case
        if (isDNSHostName(host) && !host.toLowerCase(Locale.ROOT).equals(host)) {
            throw new IllegalArgumentException("non-lowercase hostname: " + host);
        }
        if (port <= 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
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
        final String lcaseScheme = scheme.toLowerCase(Locale.ROOT);
        if (!isValidScheme(lcaseScheme)) {
            throw new IllegalArgumentException("Unsupported scheme: " + scheme);
        }
        final String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("missing host in URI");
        }
        String effectiveHost;
        if (host.startsWith("[") && host.endsWith("]")) {
            // strip the square brackets from IPv6 host
            effectiveHost = host.substring(1, host.length() - 1);
        } else {
            effectiveHost = host;
        }
        assert !effectiveHost.isEmpty() : "unexpected URI host: " + host;
        // If the host is a DNS hostname, then convert the host to lower case.
        // The DNS hostname is expected to be ASCII characters and is case-insensitive.
        //
        // Its usage in areas like SNI too match this expectation - RFC-6066, section 3:
        // "HostName" contains the fully qualified DNS hostname of the server,
        // as understood by the client.  The hostname is represented as a byte
        // string using ASCII encoding without a trailing dot. ... DNS hostnames
        // are case-insensitive.
        if (isDNSHostName(effectiveHost)) {
            effectiveHost = effectiveHost.toLowerCase(Locale.ROOT);
        }
        int port = uri.getPort();
        if (port == -1) {
            port = switch (lcaseScheme) {
                case "http" -> 80;
                case "https" -> 443;
                // we have already verified that this is a valid scheme, so this
                // should never happen
                default -> throw new AssertionError("Unsupported scheme: " + scheme);
            };
        }
        return new Origin(lcaseScheme, effectiveHost, port);
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

    private static boolean isValidScheme(final String scheme) {
        // only "http" and "https" literals allowed
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static boolean isDNSHostName(final String host) {
        final boolean isLiteral = IPAddressUtil.isIPv4LiteralAddress(host)
                || IPAddressUtil.isIPv6LiteralAddress(host);

        return !isLiteral;
    }
}
