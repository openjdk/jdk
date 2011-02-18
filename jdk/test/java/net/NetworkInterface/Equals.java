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
 * @bug 7003398
 * @run main/othervm Equals
 */
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;

public class Equals {

    public static void main(String args[]) throws Exception {

        Enumeration nifs1 = NetworkInterface.getNetworkInterfaces();
        HashMap<String,Integer> hashes = new HashMap<>();
        HashMap<String,NetworkInterface> nicMap = new HashMap<>();

        while (nifs1.hasMoreElements()) {
            NetworkInterface ni = (NetworkInterface)nifs1.nextElement();
            hashes.put(ni.getName(),ni.hashCode());
            nicMap.put(ni.getName(),ni);
        }

        System.setSecurityManager(new SecurityManager());

        Enumeration nifs2 = NetworkInterface.getNetworkInterfaces();
        while (nifs2.hasMoreElements()) {
            NetworkInterface ni = (NetworkInterface)nifs2.nextElement();
            NetworkInterface niOrig = nicMap.get(ni.getName());

            int h = hashes.get(ni.getName());
            if (h != ni.hashCode()) {
                throw new RuntimeException ("Hashcodes different for " +
                        ni.getName());
            }
            if (!ni.equals(niOrig)) {
                throw new RuntimeException ("equality different for " +
                        ni.getName());
            }
        }
    }
}
