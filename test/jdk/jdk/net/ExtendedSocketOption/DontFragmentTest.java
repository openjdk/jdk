/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8243099 8285671
 * @modules jdk.net
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.IPSupport
 * @run main/othervm DontFragmentTest ipv4
 * @run main/othervm DontFragmentTest ipv6
 */

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.net.ExtendedSocketOptions.IP_DONTFRAGMENT;

public class DontFragmentTest {

    private static boolean isMacos;

    public static void main(String[] args) throws IOException {
        isMacos = Platform.isOSX();
        boolean ipv6 = args[0].equals("ipv6");
        if (ipv6 && !IPSupport.hasIPv6()) {
            System.out.println("No IPv6 support detected, skipping IPv6 test case");
        } else {
            testDatagramChannel();
            StandardProtocolFamily fam = ipv6 ? INET6 : INET;
            System.out.println("Family = " + fam);
            testDatagramChannel(args, fam);
            try (DatagramSocket c = new DatagramSocket()) {
                testDatagramSocket(c);
            }
            try (DatagramChannel dc = DatagramChannel.open(fam)) {
                var c = dc.socket();
                testDatagramSocket(c);
            }
            try (MulticastSocket mc = new MulticastSocket()) {
                testDatagramSocket(mc);
            }
        }
    }

    /**
     * Returns true if the option is supported, false if not supported.
     * Throws exception if it is not supported, but should be
     */
    static boolean checkSupported(DatagramChannel c1) throws IOException {
        boolean supported = c1.supportedOptions().contains(IP_DONTFRAGMENT);

        if (!isMacos && !supported) {
            throw new RuntimeException("IP_DONTFRAGMENT should be supported");
        }
        return supported;
    }

    static boolean checkSupported(DatagramSocket c1) throws IOException {
        boolean supported = c1.supportedOptions().contains(IP_DONTFRAGMENT);

        if (!isMacos && !supported) {
            throw new RuntimeException("IP_DONTFRAGMENT should be supported");
        }
        return supported;
    }

    public static void testDatagramChannel() throws IOException {
        try (DatagramChannel c1 = DatagramChannel.open()) {

            if (!checkSupported(c1)) {
                return;
            }
            if (c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should not be set");
            }
            c1.setOption(IP_DONTFRAGMENT, true);
            if (!c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should be set");
            }
            c1.setOption(IP_DONTFRAGMENT, false);
            if (c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should not be set");
            }
        }
    }

    public static void testDatagramChannel(String[] args, ProtocolFamily fam) throws IOException {
        try (DatagramChannel c1 = DatagramChannel.open(fam)) {

            if (!checkSupported(c1)) {
                return;
            }
            if (c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should not be set");
            }
            c1.setOption(IP_DONTFRAGMENT, true);
            if (!c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should be set");
            }
            c1.setOption(IP_DONTFRAGMENT, false);
            if (c1.getOption(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT should not be set");
            }
        }
    }

    public static void testDatagramSocket(DatagramSocket c1) throws IOException {
        if (!checkSupported(c1)) {
            return;
        }
        if (c1.getOption(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT should not be set");
        }
        c1.setOption(IP_DONTFRAGMENT, true);
        if (!c1.getOption(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT should be set");
        }
        c1.setOption(IP_DONTFRAGMENT, false);
        if (c1.getOption(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT should not be set");
        }
        c1.close();
    }
}
