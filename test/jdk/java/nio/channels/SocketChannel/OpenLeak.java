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
 * @build OpenLeak
 * @run junit/othervm OpenLeak
 */

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

public class OpenLeak {

    private static final int MAX_LOOP = 250000;

    @Test
    public void test() throws Exception {
        InetAddress lo = InetAddress.getLoopbackAddress();

        // Try to find a suitable port that will cause a
        // Connection Rejected exception
        // port 47 is reserved - there should be nothing there...
        InetSocketAddress isa = new InetSocketAddress(lo, 47);
        try (SocketChannel sc1 = SocketChannel.open(isa)) {
            // If we manage to connect, let's try to use some other
            // port.
            // port 51 is reserved too - there should be nothing there...
            isa = new InetSocketAddress(lo, 51);
            try (SocketChannel sc2 = SocketChannel.open(isa)) {};
            // OK, last attempt...
            // port 61 is reserved too - there should be nothing there...
            isa = new InetSocketAddress(lo, 61);
            try (SocketChannel sc3 = SocketChannel.open(isa)) {};
            Assumptions.abort("Could not find a suitable port");
            return;
        } catch (ConnectException x) { }

        // create an unresolved address to test another path
        //   where close should be called
        SocketAddress sa = InetSocketAddress.createUnresolved(isa.getHostString(), isa.getPort());

        System.err.println("Expecting Connection Refused for " + isa);
        System.err.println("Expecting UnresolvedAddressException for " + sa);
        int i = 0;
        try {
            for (i = 0; i < MAX_LOOP; i++) {
                try {
                    SocketChannel.open(sa);
                    fail("This should not happen");
                } catch (UnresolvedAddressException x) {
                    if (i < 5 || i >= MAX_LOOP - 5) {
                        // print a message for the first five and last 5 exceptions
                        System.err.println(x);
                    }
                }
                try {
                    SocketChannel.open(isa);
                    fail("This should not happen");
                } catch (ConnectException x) {
                    if (i < 5 || i >= MAX_LOOP - 5) {
                        // print a message for the first five and last 5 exceptions
                        System.err.println(x);
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed at " + i + " with " + t);
            throw t;
        }
    }

}
