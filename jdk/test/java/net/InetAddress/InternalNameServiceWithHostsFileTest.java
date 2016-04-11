/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8134577
 * @summary Test the internal NameService implementation which is enabled via
 *          the system property jdk.net.hosts.file. This property specifies
 *          a file name that contains address host mappings, similar to those in
 *          /etc/hosts file. TestHosts-III file  exist, with a set of ipv4 and ipv6
 *          mappings
 * @run main/othervm -Dsun.net.inetaddr.ttl=0  InternalNameServiceWithHostsFileTest
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class InternalNameServiceWithHostsFileTest {
    public static void main(String args[]) throws Exception {

        // System.getProperty("test.src", ".");
        String hostsFileName = System.getProperty("test.src", ".")
                + "/TestHosts-III";
        System.setProperty("jdk.net.hosts.file", hostsFileName);
        System.setProperty("sun.net.inetaddr.ttl", "0");

        // fe80::1
        byte[] expectedIpv6Address = { (byte) 0xfe, (byte) 0x80, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1 };
        // fe00::0
        byte[] expectedIpv6LocalAddress = { (byte) 0xfe, (byte) 0x00, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        // 10.2.3.4
        byte[] expectedIpv4Address = { 10, 2, 3, 4 };
        //
        byte[] expectedIpv6LocalhostAddress = { 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 1 };

        try {
            // 10.2.3.4  testHost.testDomain
            testHostsMapping(expectedIpv4Address, "testHost.testDomain");
            // ::1     ip6-localhost ip6-loopback
            testHostsMapping(expectedIpv6LocalhostAddress, "ip6-localhost");
            // fe00::0 ip6-localnet
            testHostsMapping(expectedIpv6LocalAddress, "ip6-localnet");
            // fe80::1 link-local-host
            testHostsMapping(expectedIpv6Address, "link-local-host");

        } catch (UnknownHostException uhEx) {
            System.out.println("UHE unexpected caught == " + uhEx.getMessage());
        }
    }

    private static void testHostsMapping(byte[] expectedIpAddress, String hostName)
            throws UnknownHostException {
        InetAddress testAddress;
        byte[] rawIpAddress;
        testAddress = InetAddress.getByName(hostName);
        System.out
                .println("############################  InetAddress == "
                        + testAddress);

        rawIpAddress = testAddress.getAddress();
        if (!Arrays.equals(rawIpAddress, expectedIpAddress)) {
            System.out.println("retrieved address == "
                    + Arrays.toString(rawIpAddress)
                    + " not equal to expected address == "
                    + Arrays.toString(expectedIpAddress));
            throw new RuntimeException(
                    "retrieved address not equal to expected address");
        }
        System.out.println("retrieved address == "
                + Arrays.toString(rawIpAddress)
                + " equal to expected address == "
                + Arrays.toString(expectedIpAddress));
    }
}
