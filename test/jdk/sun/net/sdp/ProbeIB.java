/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.Enumeration;

/**
 * Probes for InfiniBand devices plumbed with IP addresses.
 */

public class ProbeIB {
    public static void main(String[] args) throws IOException {
        Scanner s = new Scanner(new File(args[0]));
        try {
            while (s.hasNextLine()) {
                String link = s.nextLine();
                NetworkInterface ni = NetworkInterface.getByName(link);
                if (ni != null) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        System.out.println(addr.getHostAddress());
                    }
                }
            }
        } finally {
            s.close();
        }
    }
}
