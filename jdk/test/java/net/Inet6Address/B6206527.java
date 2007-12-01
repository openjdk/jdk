/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test 1.1 05/01/05
 * @bug 6206527
 * @summary "cannot assign address" when binding ServerSocket on Suse 9
 */

import java.net.*;
import java.util.*;

public class B6206527 {

    public static void main (String[] args) throws Exception {
        Inet6Address addr = getLocalAddr();
        if (addr == null) {
            System.out.println ("Could not find a link-local address");
            return;
        }

        ServerSocket ss = new ServerSocket();
        System.out.println ("trying LL addr: " + addr);
        ss.bind(new InetSocketAddress(addr, 0));

        // need to remove the %scope suffix
        addr = (Inet6Address)InetAddress.getByAddress (
            addr.getAddress()
        );

        System.out.println ("trying LL addr: " + addr);
        ss = new ServerSocket();
        ss.bind(new InetSocketAddress(addr, 0));
    }

    public static Inet6Address getLocalAddr () throws Exception {
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while (e.hasMoreElements()) {
            NetworkInterface ifc = (NetworkInterface) e.nextElement();
            Enumeration addrs = ifc.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = (InetAddress)addrs.nextElement();
                if (a instanceof Inet6Address) {
                    Inet6Address ia6 = (Inet6Address) a;
                    if (ia6.isLinkLocalAddress()) {
                        return ia6;
                    }
                }
            }
        }
        return null;
    }
}
