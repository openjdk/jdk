/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * If the platform has IPv6 we spawn a child process simulating the
 * effect of being launched from node.js. We check that IPv6 is available in the child
 * and report back as appropriate.
 */

import jdk.test.lib.Utils;
import java.io.*;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class UnixSocketTest {

    static boolean hasIPv6() throws Exception {
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                if (inetAddress instanceof Inet6Address) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class Child {
        public static void main(String[] args) throws Exception {
            System.out.write('X');
            System.out.flush();
            if (hasIPv6()) {
                System.out.println("Y"); // GOOD
            } else
                System.out.println("N"); // BAD
        }
    }

    public static void main(String args[]) throws Exception {

        if (!hasIPv6()) {
            return; // can only test if IPv6 is present
        }
        UnixDomainSocket sock = Launcher.launchWithUnixDomainSocket("UnixSocketTest$Child");
        if (sock.read() != 'X') {
            System.exit(-2);
        }
        if (sock.read() != 'Y') {
            System.exit(-2);
        }
    }
}
