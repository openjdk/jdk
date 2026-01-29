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

import java.net.URI;
import java.util.Locale;

import jdk.internal.net.http.Origin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary verify the behaviour of jdk.internal.net.http.Origin
 * @modules java.net.http/jdk.internal.net.http
 * @run junit OriginTest
 */
class OriginTest {

    @ParameterizedTest
    @ValueSource(strings = {"foo", "Bar", "HttPS", "HTTP"})
    void testInvalidScheme(final String scheme) throws Exception {
        final String validHost = "127.0.0.1";
        final int validPort = 80;
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> {
            new Origin(scheme, validHost, validPort);
        });
        assertTrue(iae.getMessage().contains("scheme"),
                "unexpected exception message: " + iae.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "https"})
    void testValidScheme(final String scheme) throws Exception {
        final String validHost = "127.0.0.1";
        final int validPort = 80;
        final Origin o1 = new Origin(scheme, validHost, validPort);
        assertEquals(validHost, o1.host(), "unexpected host");
        assertEquals(validPort, o1.port(), "unexpected port");
        assertEquals(scheme, o1.scheme(), "unexpected scheme");

        final URI uri = URI.create(scheme + "://" + validHost + ":" + validPort);
        final Origin o2 = Origin.from(uri);
        assertNotNull(o2, "null Origin for URI " + uri);
        assertEquals(validHost, o2.host(), "unexpected host");
        assertEquals(validPort, o2.port(), "unexpected port");
        assertEquals(scheme, o2.scheme(), "unexpected scheme");
    }

    @ParameterizedTest
    @ValueSource(strings = {"JDK.java.net", "[::1]", "[0:0:0:0:0:0:0:1]"})
    void testInvalidHost(final String host) throws Exception {
        final String validScheme = "http";
        final int validPort = 8000;
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> {
            new Origin(validScheme, host, validPort);
        });
        assertTrue(iae.getMessage().contains("host"),
                "unexpected exception message: " + iae.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "localhost", "jdk.java.net", "::1", "0:0:0:0:0:0:0:1"})
    void testValidHost(final String host) throws Exception {
        final String validScheme = "https";
        final int validPort = 42;
        final Origin o1 = new Origin(validScheme, host, validPort);
        assertEquals(host, o1.host(), "unexpected host");
        assertEquals(validPort, o1.port(), "unexpected port");
        assertEquals(validScheme, o1.scheme(), "unexpected scheme");

        String uriHost = host;
        if (host.contains(":")) {
            uriHost = "[" + host + "]";
        }
        final URI uri = URI.create(validScheme + "://" + uriHost + ":" + validPort);
        final Origin o2 = Origin.from(uri);
        assertNotNull(o2, "null Origin for URI " + uri);
        assertEquals(host, o2.host(), "unexpected host");
        assertEquals(validPort, o2.port(), "unexpected port");
        assertEquals(validScheme, o2.scheme(), "unexpected scheme");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void testInvalidPort(final int port) throws Exception {
        final String validScheme = "http";
        final String validHost = "127.0.0.1";
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
                () -> new Origin(validScheme, validHost, port));
        assertTrue(iae.getMessage().contains("port"),
                "unexpected exception message: " + iae.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1024, 80, 8080, 42})
    void testValidPort(final int port) throws Exception {
        final String validScheme = "https";
        final String validHost = "localhost";
        final Origin o1 = new Origin(validScheme, validHost, port);
        assertEquals(validHost, o1.host(), "unexpected host");
        assertEquals(port, o1.port(), "unexpected port");
        assertEquals(validScheme, o1.scheme(), "unexpected scheme");

        final URI uri = URI.create(validScheme + "://" + validHost + ":" + port);
        final Origin o2 = Origin.from(uri);
        assertNotNull(o2, "null Origin for URI " + uri);
        assertEquals(validHost, o2.host(), "unexpected host");
        assertEquals(port, o2.port(), "unexpected port");
        assertEquals(validScheme, o2.scheme(), "unexpected scheme");
    }

    @Test
    void testInferredPort() throws Exception {
        final URI httpURI = URI.create("http://localhost");
        final Origin httpOrigin = Origin.from(httpURI);
        assertNotNull(httpOrigin, "null Origin for URI " + httpURI);
        assertEquals("localhost", httpOrigin.host(), "unexpected host");
        assertEquals(80, httpOrigin.port(), "unexpected port");
        assertEquals("http", httpOrigin.scheme(), "unexpected scheme");


        final URI httpsURI = URI.create("https://[::1]");
        final Origin httpsOrigin = Origin.from(httpsURI);
        assertNotNull(httpsOrigin, "null Origin for URI " + httpsURI);
        assertEquals("::1", httpsOrigin.host(), "unexpected host");
        assertEquals(443, httpsOrigin.port(), "unexpected port");
        assertEquals("https", httpsOrigin.scheme(), "unexpected scheme");
    }

    @Test
    void testFromURI() {
        // non-lower case URI scheme is expected to be converted to lowercase in the Origin
        // constructed through Origin.from(URI)
        for (final String scheme : new String[]{"httPs", "HTTP"}) {
            final String expectedScheme = scheme.toLowerCase(Locale.ROOT);
            final URI uri = URI.create(scheme + "://localhost:1234");
            final Origin origin = Origin.from(uri);
            assertNotNull(origin, "null Origin for URI " + uri);
            assertEquals("localhost", origin.host(), "unexpected host");
            assertEquals(1234, origin.port(), "unexpected port");
            assertEquals(expectedScheme, origin.scheme(), "unexpected scheme");
        }
        // URI without a port is expected to be defaulted to port 80 or 443 for http and https
        // schemes respectively
        for (final String scheme : new String[]{"http", "https"}) {
            final int expectedPort = switch (scheme) {
                case "http" -> 80;
                case "https" -> 443;
                default -> fail("unexpected scheme: " + scheme);
            };
            final URI uri = URI.create(scheme + "://localhost");
            final Origin origin = Origin.from(uri);
            assertNotNull(origin, "null Origin for URI " + uri);
            assertEquals("localhost", origin.host(), "unexpected host");
            assertEquals(expectedPort, origin.port(), "unexpected port");
            assertEquals(scheme, origin.scheme(), "unexpected scheme");
        }
    }
}
