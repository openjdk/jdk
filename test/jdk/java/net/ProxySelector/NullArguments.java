/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4937962 8318150
 * @summary ProxySelector.connectFailed and .select never throw IllegalArgumentException
 * @run junit NullArguments
 */
import java.net.*;
import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class NullArguments {

    public static Stream<ProxySelector> testProxies() {
        return Stream.of(
                ProxySelector.getDefault(),
                ProxySelector.of(new InetSocketAddress(1234)));
    }

    @ParameterizedTest
    @MethodSource("testProxies")
    void testNullArguments(ProxySelector ps) throws URISyntaxException {
        Assumptions.assumeTrue(ps != null, "Skipping null selector");
        assertThrows(IllegalArgumentException.class,
                () -> ps.select(null),
                "Expected IllegalArgumentException!");
        URI uri = new URI("http://java.sun.com");
        SocketAddress sa = new InetSocketAddress("localhost", 80);
        IOException ioe = new IOException("dummy IOE");
        assertThrows(IllegalArgumentException.class,
                () -> ps.connectFailed(uri, sa, null),
                "Expected IllegalArgumentException!");
        assertThrows(IllegalArgumentException.class,
                () -> ps.connectFailed(uri, null, ioe),
                "Expected IllegalArgumentException!");
        assertThrows(IllegalArgumentException.class,
                () -> ps.connectFailed(null, sa, ioe),
                "Expected IllegalArgumentException!");
    }
}
