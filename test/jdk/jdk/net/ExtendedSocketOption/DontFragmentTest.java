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
 * @bug 8243099
 * @modules jdk.net
 * @run main/othervm DontFragmentTest ipv4
 * @run main/othervm DontFragmentTest ipv6
 */

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.net.ExtendedSocketOptions.IP_DONTFRAGMENT;

public class DontFragmentTest {

    public static void main(String[] args) throws IOException {
        testDatagramChannel();
        StandardProtocolFamily fam = args[0].equals("ipv4") ? INET : INET6;
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

    public static void testDatagramChannel() throws IOException {
        try (DatagramChannel c1 = DatagramChannel.open()) {

            if (!c1.supportedOptions().contains(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT not supported");
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

            if (!c1.supportedOptions().contains(IP_DONTFRAGMENT)) {
                throw new RuntimeException("IP_DONTFRAGMENT not supported");
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
        if (!c1.supportedOptions().contains(IP_DONTFRAGMENT)) {
            throw new RuntimeException("IP_DONTFRAGMENT not supported");
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
