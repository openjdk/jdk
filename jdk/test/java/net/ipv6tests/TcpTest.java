/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4868820
 * @summary IPv6 support for Windows XP and 2003 server
 */

import java.net.*;
import java.io.*;

public class TcpTest extends Tests {
    static ServerSocket server, server1, server2;
    static Socket c1, c2, c3, s1, s2, s3;
    static InetAddress s1peer, s2peer;

    static InetAddress ia4any;
    static InetAddress ia6any;
    static Inet6Address ia6addr;
    static Inet4Address ia4addr;

    static {
        ia6addr = getFirstLocalIPv6Address ();
        ia4addr = getFirstLocalIPv4Address ();
        try {
            ia4any = InetAddress.getByName ("0.0.0.0");
            ia6any = InetAddress.getByName ("::0");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main (String[] args) throws Exception {
        checkDebug(args);
        if (ia6addr == null) {
            System.out.println ("No IPV6 addresses: exiting test");
            return;
        }
        dprintln ("Local Addresses");
        dprintln (ia4addr.toString());
        dprintln (ia6addr.toString());
        test1 (0);
        test1 (5100);
        test2();
        test3();
        test4();
    }

    /* basic TCP connectivity test using IPv6 only and IPv4/IPv6 together */

    static void test1 (int port) throws Exception {
        server = new ServerSocket (port);
        if (port == 0) {
            port = server.getLocalPort();
        }
        // try Ipv6 only
        c1 = new Socket ("::1", port);
        s1 = server.accept ();
        simpleDataExchange (c1, s1);
        s1.close ();
        c1.close();
        // try with both IPv4 and Ipv6
        c1 = new Socket ("127.0.0.1", port);
        c2 = new Socket ("::1", port);
        s1 = server.accept();
        s2 = server.accept();
        s1peer = s1.getInetAddress();
        s2peer = s2.getInetAddress();
        if (s1peer instanceof Inet6Address) {
            t_assert ((s2peer instanceof Inet4Address));
            simpleDataExchange (c2, s1);
            simpleDataExchange (c1, s2);
        } else {
            t_assert ((s2peer instanceof Inet6Address));
            simpleDataExchange (c1, s1);
            simpleDataExchange (c2, s2);
        }
        c1.close();
        c2.close();
        s1.close();
        s2.close();
        server.close ();
        System.out.println ("Test1: OK");
    }

    /** bind tests:
     *  1. bind to specific address IPv4 only (any port)
     *  2. bind to specific address IPv6 only (any port)
     *  3. bind to specific address IPv4 only (specific port)
     *  4. bind to specific address IPv4 only (specific port)
     *  5. bind to any address IPv4 (test collision)
     */

    static void test2 () throws Exception {

        server = new ServerSocket ();
        InetSocketAddress sadr = new InetSocketAddress (ia4addr, 0);
        server.bind (sadr);
        dprintln ("server bound to " + sadr);
        int port = server.getLocalPort();
        InetSocketAddress sadr6 = new InetSocketAddress (ia6addr, port);

        c1 = new Socket (ia4addr, port);
        try {
            dprintln ("connecting to " + ia6addr);
            c2 = new Socket ();
            c2.connect (sadr6, 1000);
            throw new RuntimeException ("connect to IPv6 address should be refused");
        } catch (IOException e) { }
        server.close ();
        c1.close ();

        /* now try IPv6 only */

        server = new ServerSocket ();
        sadr = new InetSocketAddress (ia6addr, 0);
        dprintln ("binding to " + sadr);
        server.bind (sadr);
        port = server.getLocalPort();

        c1 = new Socket (ia6addr, port);
        try {
            c2 = new Socket (ia4addr, port);
            throw new RuntimeException ("connect to IPv4 address should be refused");
        } catch (IOException e) { }
        server.close ();
        c1.close ();

        /* now try IPv6 specific port only */

        server = new ServerSocket ();
        sadr = new InetSocketAddress (ia6addr, 5200);
        server.bind (sadr);
        port = server.getLocalPort();
        t_assert (port == 5200);

        c1 = new Socket (ia6addr, port);
        try {
            c2 = new Socket (ia4addr, port);
            throw new RuntimeException ("connect to IPv4 address should be refused");
        } catch (IOException e) { }
        server.close ();
        c1.close ();

        /* now try IPv4 specific port only */

        server = new ServerSocket ();
        sadr = new InetSocketAddress (ia4addr, 5200);
        server.bind (sadr);
        port = server.getLocalPort();
        t_assert (port == 5200);

        c1 = new Socket (ia4addr, port);

        try {
            c2 = new Socket (ia6addr, port);
            throw new RuntimeException ("connect to IPv6 address should be refused");
        } catch (IOException e) { }
        server.accept().close();
        c1.close ();
        server.close();
        System.out.println ("Test2: OK");
    }

    /* Test timeouts on accept(), connect() */

    static void test3 () throws Exception {
        server = new ServerSocket (0);
        server.setSoTimeout (5000);
        int port = server.getLocalPort();
        long t1 = System.currentTimeMillis();
        try {
            server.accept ();
            throw new RuntimeException ("accept should not have returned");
        } catch (SocketTimeoutException e) {}
        t1 = System.currentTimeMillis() - t1;
        checkTime (t1, 5000);

        c1 = new Socket ();
        c1.connect (new InetSocketAddress (ia4addr, port), 1000);
        s1 = server.accept ();
        simpleDataExchange (c1,s1);
        c2 = new Socket ();
        c2.connect (new InetSocketAddress (ia6addr, port), 1000);
        s2 = server.accept ();
        simpleDataExchange (c2,s2);
        c3 = new Socket ();
        c3.connect (new InetSocketAddress (ia6addr, port), 1000);
        s3 = server.accept ();
        c2.close (); s2.close();
        server.close();
        simpleDataExchange (c3,s3);
        c1.close (); c2.close();
        s1.close (); s2.close();

        System.out.println ("Test3: OK");
    }

    /* Test: connect to IPv4 mapped address  */

    static void test4 () throws Exception {
        server = new ServerSocket (0);
        int port = server.getLocalPort();

        /* create an IPv4 mapped address corresponding to local host */

        byte[] b = {0,0,0,0,0,0,0,0,0,0,(byte)0xff,(byte)0xff,0,0,0,0};
        byte[] ia4 = ia4addr.getAddress();
        b[12] = ia4[0];
        b[13] = ia4[1];
        b[14] = ia4[2];
        b[15] = ia4[3];

        InetAddress dest = InetAddress.getByAddress (b);
        c1 = new Socket (dest, port);
        s1 = server.accept ();
        simpleDataExchange (c1,s1);
        c1.close ();
        s1.close ();
        server.close ();
        System.out.println ("Test4: OK");
    }
}
