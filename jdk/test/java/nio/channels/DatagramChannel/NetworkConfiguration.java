/*
 * Copyright (c) 2007, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.net.*;
import java.util.*;
import java.io.IOException;

/**
 * Helper class for multicasting tests.
 */

class NetworkConfiguration {

    private Map<NetworkInterface,List<InetAddress>> ip4Interfaces;
    private Map<NetworkInterface,List<InetAddress>> ip6Interfaces;

    private NetworkConfiguration(Map<NetworkInterface,List<InetAddress>> ip4Interfaces,
                                 Map<NetworkInterface,List<InetAddress>> ip6Interfaces)
    {
        this.ip4Interfaces = ip4Interfaces;
        this.ip6Interfaces = ip6Interfaces;
    }

    Iterable<NetworkInterface> ip4Interfaces() {
        return ip4Interfaces.keySet();
    }

    Iterable<NetworkInterface> ip6Interfaces() {
        return ip6Interfaces.keySet();
    }

    Iterable<InetAddress> ip4Addresses(NetworkInterface nif) {
        return ip4Interfaces.get(nif);
    }

    Iterable<InetAddress> ip6Addresses(NetworkInterface nif) {
        return ip6Interfaces.get(nif);
    }

    static NetworkConfiguration probe() throws IOException {
        Map<NetworkInterface,List<InetAddress>> ip4Interfaces =
            new HashMap<NetworkInterface,List<InetAddress>>();
        Map<NetworkInterface,List<InetAddress>> ip6Interfaces =
            new HashMap<NetworkInterface,List<InetAddress>>();

        // find the interfaces that support IPv4 and IPv6
        List<NetworkInterface> nifs = Collections
            .list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nif: nifs) {
            // ignore intertaces that are down or don't support multicast
            if (!nif.isUp() || !nif.supportsMulticast() || nif.isLoopback())
                continue;

            List<InetAddress> addrs = Collections.list(nif.getInetAddresses());
            for (InetAddress addr: addrs) {
                if (!addr.isAnyLocalAddress()) {
                    if (addr instanceof Inet4Address) {
                        List<InetAddress> list = ip4Interfaces.get(nif);
                        if (list == null) {
                            list = new LinkedList<InetAddress>();
                        }
                        list.add(addr);
                        ip4Interfaces.put(nif, list);
                    } else if (addr instanceof Inet6Address) {
                        List<InetAddress> list = ip6Interfaces.get(nif);
                        if (list == null) {
                            list = new LinkedList<InetAddress>();
                        }
                        list.add(addr);
                        ip6Interfaces.put(nif, list);
                    }
                }
            }
        }
        return new NetworkConfiguration(ip4Interfaces, ip6Interfaces);
    }
}
