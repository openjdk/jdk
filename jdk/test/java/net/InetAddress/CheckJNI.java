/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4889870 4890033
   @summary java -Xcheck:jni failing in net code on Solaris / [Datagram]Socket.getLocalAddress() failure
   @run main/othervm -Xcheck:jni CheckJNI
*/

import java.net.*;
import java.util.*;

public class CheckJNI {
    static Socket s;
    static ServerSocket server;
    static DatagramSocket dg1, dg2;

    public static void main (String[] args) throws Exception {
        /* try to invoke as much java.net native code as possible */

        System.out.println ("Testing IPv4 Socket/ServerSocket");
        server = new ServerSocket (0);
        s = new Socket ("127.0.0.1", server.getLocalPort());
        s.close();
        server.close();

        System.out.println ("Testing IPv4 DatagramSocket");
        dg1 = new DatagramSocket (0, InetAddress.getByName ("127.0.0.1"));
        dg2 = new DatagramSocket (0, InetAddress.getByName ("127.0.0.1"));
        testDatagrams (dg1, dg2);

        /* Use NetworkInterface to find link local IPv6 addrs to test */

        Enumeration ifs = NetworkInterface.getNetworkInterfaces();
        server = new ServerSocket (0);

        while (ifs.hasMoreElements()) {
            NetworkInterface nif = (NetworkInterface)ifs.nextElement();
            Enumeration addrs = nif.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = (InetAddress) addrs.nextElement();
                if (addr instanceof Inet6Address) {
                    Inet6Address ia6 = (Inet6Address) addr;
                    if (ia6.isLinkLocalAddress()) {
                        System.out.println ("Testing IPv6 Socket");
                        s = new Socket (ia6, server.getLocalPort());
                        s.close();

                        System.out.println ("Testing IPv6 DatagramSocket");
                        dg1 = new DatagramSocket (0, ia6);
                        dg2 = new DatagramSocket (0, ia6);
                        testDatagrams (dg1, dg2);
                    }
                }
            }
        }
        server.close();
        System.out.println ("OK");
    }

    static void testDatagrams (DatagramSocket s1, DatagramSocket s2) throws Exception {
        DatagramPacket p1 = new DatagramPacket (
                "hello world".getBytes(),
                0, "hello world".length(), s2.getLocalAddress(),
                s2.getLocalPort()
        );

        DatagramPacket p2 = new DatagramPacket (new byte[128], 128);
        s1.send (p1);
        s2.receive (p2);
        s1.close ();
        s2.close ();
    }
}
