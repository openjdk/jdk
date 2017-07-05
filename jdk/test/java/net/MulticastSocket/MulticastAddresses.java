/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 * @bug 4488458
 * @summary Test that MutlicastSocket.joinGroup is working for
 *          various multicast and non-multicast addresses.
 */
import java.net.*;
import java.util.Enumeration;
import java.io.IOException;

public class MulticastAddresses {

    public static void main(String args[]) throws Exception {

        boolean ipv6_available = false;
        NetworkInterface ni = null;

        /*
         * Examine the network interfaces and determine :-
         *
         * 1. If host has IPv6 support
         * 2. Get reference to a non-loopback interface
         */
        Enumeration nifs = NetworkInterface.getNetworkInterfaces();
        while (nifs.hasMoreElements()) {
            NetworkInterface this_ni = (NetworkInterface)nifs.nextElement();

            Enumeration addrs = this_ni.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = (InetAddress)addrs.nextElement();
                if (addr instanceof Inet6Address) {
                    ipv6_available = true;
                }

                if (!addr.isLoopbackAddress() && ni == null) {
                    ni = this_ni;
                }
            }

            if (ipv6_available) {
                break;
            }
        }

        int failures = 0;

        String multicasts[] = {
                "224.80.80.80",
                "ff01::1",
                "ff02::1234",
                "ff05::a",
                "ff0e::1234:a" };

        String non_multicasts[] = {
                "129.1.1.1",
                "::1",
                "::129.1.1.1",
                "fe80::a00:20ff:fee5:bc02" };

        MulticastSocket s = new MulticastSocket();

        /* test valid multicast addresses */

        for (int i=0; i<multicasts.length; i++) {
            InetAddress ia = InetAddress.getByName(multicasts[i]);
            if (ia instanceof Inet6Address && !ipv6_available) {
                continue;
            }

            System.out.println("Test: " + ia);

            try {

                System.out.print("    joinGroup(InetAddress) ");
                s.joinGroup(ia);
                s.leaveGroup(ia);
                System.out.println("    Passed.");

                System.out.print("    joinGroup(InetAddress,NetworkInterface) ");
                s.joinGroup(new InetSocketAddress(ia,0), ni);
                s.leaveGroup(new InetSocketAddress(ia,0), ni);
                System.out.println("    Passed.");
            } catch (IOException e) {
                failures++;
                System.out.println("Failed: " + e.getMessage());
            }

        }

        /* test non-multicast addresses */

        for (int i=0; i<non_multicasts.length; i++) {
            InetAddress ia = InetAddress.getByName(non_multicasts[i]);
            if (ia instanceof Inet6Address && !ipv6_available) {
                continue;
            }

            boolean failed = false;

            System.out.println("Test: " + ia + " ");
            try {
                System.out.println("    joinGroup(InetAddress) ");
                s.joinGroup(ia);

                System.out.println("Failed!! -- incorrectly joined group");
                failed = true;
            } catch (IOException e) {
                System.out.println("    Passed: " + e.getMessage());
            }

            if (failed) {
                s.leaveGroup(ia);
                failures++;
            }
        }

        /* done */

        s.close();

        if (failures > 0) {
            throw new Exception(failures + " test(s) failed - see log file.");
        }
    }

}
