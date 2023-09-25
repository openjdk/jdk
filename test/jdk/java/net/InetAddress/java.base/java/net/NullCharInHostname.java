/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.net.spi.InetAddressResolver.LookupPolicy;

public class NullCharInHostname {
    public static void main(String[] args) {
        var name = "foo\u0000bar";
        System.out.println("file.encoding = " + System.getProperty("file.encoding"));
        System.out.println("native.encoding = " + System.getProperty("native.encoding"));

        // This should throw IAE as it calls the internal impl
        try {
            var impl = new Inet6AddressImpl();
            var addrs = impl.lookupAllHostAddr(name, LookupPolicy.of(LookupPolicy.IPV4));
        } catch (UnknownHostException e0) {
            throw new RuntimeException(e0);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // This should throw UHE as before and not IAE for compatibility
        try {
            var addrs = InetAddress.getByName(name);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (UnknownHostException e0) {
            e0.printStackTrace();
        }
    }
}
