/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6548464
 * @summary SocketChannel.open(SocketAddress) leaks file descriptor if
 *     connection cannot be established
 * @requires vm.flagless
 * @build OpenLeak
 * @run junit/othervm OpenLeak
 */

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;


public class OpenLeak {

    static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    static final boolean IS_WINDOWS_2016 = OS_NAME.contains("windows") && OS_NAME.contains("2016");

    // On Windows Server 2016 trying to connect to port 47 consumes the
    // whole connect timeout - which makes the test fail in timeout.
    // We skip this part of the test on Windows Server 2016
    static final boolean TEST_WITH_RESERVED_PORT = !IS_WINDOWS_2016;

    private static final int MAX_LOOP = 250000;


    // Try to find a suitable port to provoke a "Connection Refused"
    // error.
    private static InetSocketAddress findSuitableRefusedAddress(InetSocketAddress isa)
            throws IOException {
        if (!TEST_WITH_RESERVED_PORT) return null;
        var addr = isa.getAddress();
        try (SocketChannel sc1 = SocketChannel.open(isa)) {
            // If we manage to connect, let's try to use some other
            // port.
            // port 51 is reserved too - there should be nothing there...
            isa = new InetSocketAddress(addr, 51);
            try (SocketChannel sc2 = SocketChannel.open(isa)) {
            }
            // OK, last attempt...
            // port 61 is reserved too - there should be nothing there...
            isa = new InetSocketAddress(addr, 61);
            try (SocketChannel sc3 = SocketChannel.open(isa)) {
            }
            System.err.println("Could not find a suitable port");
            return null;
        } catch (ConnectException x) {
        }
        return isa;
    }

    private static InetSocketAddress createUnresolved(InetSocketAddress isa, InetSocketAddress def) {
       var sa = isa == null ? def : isa;
       return InetSocketAddress.createUnresolved(sa.getHostString(), sa.getPort());
    }


    // Builds a list of test cases
    static List<Object[]> testCases() throws Exception {
        InetAddress lo = InetAddress.getLoopbackAddress();

        // Try to find a suitable port that will cause a
        // Connection Refused exception
        // port 47 is reserved - there should be nothing there...
        InetSocketAddress def = new InetSocketAddress(lo, 47);
        InetSocketAddress isa = findSuitableRefusedAddress(def);
        InetSocketAddress sa  = createUnresolved(isa, def);

        final List<Object[]> cases = new ArrayList<>();
        cases.add(new Object[]{sa, UnresolvedAddressException.class});
        if (isa != null) {
            cases.add(new Object[]{isa, ConnectException.class});
        }
        return cases;
    }

    @ParameterizedTest
    @MethodSource("testCases")
    public void test(SocketAddress sa, Class<? extends Throwable> expectedException) throws Exception {
        System.err.printf("%nExpecting %s for %s%n", expectedException, sa);

        int i = 0;
        try {
            for (i = 0; i < MAX_LOOP; i++) {
                Throwable x =
                        assertThrows(expectedException, () -> SocketChannel.open(sa));
                if (i < 5 || i >= MAX_LOOP - 5) {
                    // print a message for the first five and last 5 exceptions
                    System.err.println(x);
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed at " + i + " with " + t);
            throw t;
        }
    }

}
