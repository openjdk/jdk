/*
 * Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6521014 6543428
 * @summary IOException thrown when Socket tries to bind to an local IPv6 address on SuSE Linux
 */


import java.net.*;
import java.io.*;
import java.util.*;


/*
 *
 * What this testcase is to test is a (weird) coupling through the
 * cached_scope_id field of java.net.Inet6Address. Native method
 * NET_InetAddressToSockaddr as in Linux platform will try to write
 * and read this field, therefore Inet6Address becomes 'stateful'.
 * So the coupling. Certain executive order, e.g. two methods use
 * the same Inet6Address instance as illustrated in this test case,
 * will show side effect of such coupling.
 *
 * And on Windows, NET_InetAddressToSockaddr() did not assign appropriate
 * sin6_scope_id value to sockaddr_in6 structure if there's no one coming
 * with Inet6Address instance, which caused bind exception. This test use
 * link-local address without %scope suffix, so it is also going to test
 * that.
 *
 */
public class B6521014 {

    static InetAddress sin;

    static Inet6Address getLocalAddr () throws Exception {
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while (e.hasMoreElements()) {
            NetworkInterface ifc = (NetworkInterface) e.nextElement();
            if (!ifc.isUp())
                continue;
            Enumeration addrs = ifc.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = (InetAddress)addrs.nextElement();
                if (a instanceof Inet6Address) {
                    Inet6Address ia6 = (Inet6Address) a;
                    if (ia6.isLinkLocalAddress()) {
                        // remove %scope suffix
                        return (Inet6Address)InetAddress.getByAddress(ia6.getAddress());
                    }
                }
            }
        }
        return null;
    }

    static void test1() throws Exception {
        ServerSocket ssock;
        Socket sock;
        int port;

        ssock = new ServerSocket(0);
        port = ssock.getLocalPort();
        sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(sin, port), 100);
        } catch (SocketTimeoutException e) {
            // time out exception is okay
            System.out.println("timed out when connecting.");
        }
    }

    static void test2() throws Exception {
        Socket sock;
        ServerSocket ssock;
        int port;

        ssock = new ServerSocket(0);
        ssock.setSoTimeout(100);
        port = ssock.getLocalPort();
        sock = new Socket();
        sock.bind(new InetSocketAddress(sin, 0));
        try {
            sock.connect(new InetSocketAddress(sin, port), 100);
        } catch (SocketTimeoutException e) {
            // time out exception is okay
            System.out.println("timed out when connecting.");
        }
    }

    public static void main(String[] args) throws Exception {
        sin = getLocalAddr();
        if (sin == null) {
            System.out.println("Cannot find a link-local address.");
            return;
        }

        try {
            test1();
            test2();
        } catch (IOException e) {
            throw new RuntimeException("Test failed: cannot create socket.", e);
        }
    }
}
