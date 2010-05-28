/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4402708
 *
 * @run main/othervm -Djava.net.preferIPv6Addresses=true LookupIPv6
 *
 * @summary Ensure that java.rmi.Naming.lookup can handle URLs containing
 *          IPv6 addresses.
 */

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class LookupIPv6 {
    public static void main(String[] args) throws Exception {
        // use loopback IPv6 address to avoid lengthy socket connection delays
        String[] urls = {
            "rmi://[0000:0000:0000:0000:0000:0000:0000:0001]/foo",
            "//[0:0:0:0:0:0:0:1]:88/foo",
            "rmi://[0::0:0:0:1]/foo:bar",
            "//[::1]:88"
        };
        for (int i = 0; i < urls.length; i++) {
            try {
                Naming.lookup(urls[i]);
            } catch (MalformedURLException ex) {
                throw ex;
            } catch (Exception ex) {
                // URLs are bogus, lookups expected to fail
            }
        }

        /* Attempt to use IPv6-based URL to look up object in local registry.
         * Since not all platforms support IPv6, this portion of the test may
         * be a no-op in some cases.  On supporting platforms, the first
         * element of the array returned by InetAddress.getAllByName should be
         * an Inet6Address since this test is run with
         * -Djava.net.preferIPv6Addresses=true.
         */
        InetAddress localAddr = InetAddress.getAllByName(null)[0];
        if (localAddr instanceof Inet6Address) {
            System.out.println("IPv6 detected");
            Registry reg;
            try {
                reg = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
            } catch (Exception ex) {
                reg = LocateRegistry.getRegistry();
            }
            reg.rebind("foo", reg);
            Naming.lookup("rmi://[" + localAddr.getHostAddress() + "]/foo");
        }
    }
}
