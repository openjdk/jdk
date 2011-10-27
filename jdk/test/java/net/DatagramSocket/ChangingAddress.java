/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7084030
 * @summary Tests that DatagramSocket.getLocalAddress returns the right local
 *          address after connect/disconnect.
 */
import java.net.*;

public class ChangingAddress {

    static void check(DatagramSocket ds, InetAddress expected) {
        InetAddress actual = ds.getLocalAddress();
        if (!expected.equals(actual)) {
            throw new RuntimeException("Expected:"+expected+" Actual"+
                                       actual);
        }
    }

    public static void main(String[] args) throws Exception {
        InetAddress lh = InetAddress.getLocalHost();
        SocketAddress remote = new InetSocketAddress(lh, 1234);
        InetAddress wildcard = InetAddress.getByAddress
                               ("localhost", new byte[]{0,0,0,0});
        try (DatagramSocket ds = new DatagramSocket()) {
            check(ds, wildcard);

            ds.connect(remote);
            check(ds, lh);

            ds.disconnect();
            check(ds, wildcard);
       }
    }
}
