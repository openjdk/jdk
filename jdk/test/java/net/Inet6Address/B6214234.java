/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 6214234 6967937
 * @summary IPv6 scope_id for local addresses not set in Solaris 10
 */

import java.net.*;
import java.util.*;

public class B6214234 {

    public static void main (String[] args) throws Exception {
        String osname = System.getProperty ("os.name");
        String version = System.getProperty ("os.version");
        if (!"SunOS".equals (osname)) {
            System.out.println ("Test only runs on Solaris");
            return;
        }
        String[] v = version.split("\\.");
        int verNumber = Integer.parseInt (v[0]) * 100 + Integer.parseInt (v[1]);
        if (verNumber < 510) {
            System.out.println ("Test only runs on Solaris versions 10 or higher");
            return;
        }
        Inet6Address addr = getLocalAddr();
        if (addr == null) {
            System.out.println ("Could not find a link-local address");
            return;
        }
        if (addr.getScopeId() == 0) {
            System.out.println("addr: "+ addr);
            throw new RuntimeException ("Non zero scope_id expected");
        }
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
