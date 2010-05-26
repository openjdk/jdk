/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6717876
 * @summary Make java.net.NetworkInterface.getIndex() public
 */

import java.net.*;
import java.util.Enumeration;

public class IndexTest {
    public static void main(String[] args) throws Exception {
        Enumeration<NetworkInterface> netifs = NetworkInterface.getNetworkInterfaces();
        NetworkInterface nif = null;
        while (netifs.hasMoreElements()) {
            nif = netifs.nextElement();
            int index = nif.getIndex();
            if (index >= 0) {
                NetworkInterface nif2 = NetworkInterface.getByIndex(index);
                if (! nif.equals(nif2)) {
                    throw new RuntimeException("both interfaces should be equal");
                }
            }
        }
        try {
            nif = NetworkInterface.getByIndex(-1);
            throw new RuntimeException("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // OK
        }
        // In all likelyhood, this interface should not exist.
        nif = NetworkInterface.getByIndex(Integer.MAX_VALUE - 1);
        if (nif != null) {
            throw new RuntimeException("getByIndex() should have returned null");
        }
    }
}
