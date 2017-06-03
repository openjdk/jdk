/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.net;

/**
 * Choose a network interface to be the default for
 * outgoing IPv6 traffic that does not specify a scope_id (and which needs one).
 * We choose the first interface that is up and is (in order of preference):
 * 1. neither loopback nor point to point
 * 2. point to point
 * 3. loopback
 * 4. none.
 * Platforms that do not require a default interface implement a dummy
 * that returns null.
 */

import java.util.Enumeration;
import java.io.IOException;

class DefaultInterface {

    private static final NetworkInterface defaultInterface =
        chooseDefaultInterface();

    static NetworkInterface getDefault() {
        return defaultInterface;
    }

    /**
     * Choose a default interface. This method returns the first interface that
     * is both "up" and supports multicast. This method chooses an interface in
     * order of preference:
     * 1. neither loopback nor point to point
     *    ( prefer interfaces with dual IP support )
     * 2. point to point
     * 3. loopback
     *
     * @return  the chosen interface or {@code null} if there isn't a suitable
     *          default
     */
    private static NetworkInterface chooseDefaultInterface() {
        Enumeration<NetworkInterface> nifs;

        try {
           nifs = NetworkInterface.getNetworkInterfaces();
        } catch (IOException ignore) {
            // unable to enumerate network interfaces
            return null;
        }

        NetworkInterface preferred = null;
        NetworkInterface ppp = null;
        NetworkInterface loopback = null;

        while (nifs.hasMoreElements()) {
            NetworkInterface ni = nifs.nextElement();
            try {
                if (!ni.isUp() || !ni.supportsMulticast())
                    continue;

                boolean ip4 = false, ip6 = false;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isAnyLocalAddress()) {
                        if (addr instanceof Inet4Address) {
                            ip4 = true;
                        } else if (addr instanceof Inet6Address) {
                            ip6 = true;
                        }
                    }
                }

                boolean isLoopback = ni.isLoopback();
                boolean isPPP = ni.isPointToPoint();
                if (!isLoopback && !isPPP) {
                    // found an interface that is not the loopback or a
                    // point-to-point interface
                    if (preferred == null) {
                        preferred = ni;
                    } else if (ip4 && ip6){
                        return ni;
                    }
                }
                if (ppp == null && isPPP)
                    ppp = ni;
                if (loopback == null && isLoopback)
                    loopback = ni;

            } catch (IOException skip) { }
        }

        if (preferred != null) {
            return preferred;
        } else {
            return (ppp != null) ? ppp : loopback;
        }
    }
}
