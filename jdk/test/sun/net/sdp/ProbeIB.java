/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
        Scanner s = new Scanner(new File("/etc/path_to_inst"));
        try {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (line.startsWith("#"))
                    continue;
                String[] fields = line.split("\\s+");
                if (!fields[2].equals("\"ibd\""))
                    continue;
                String name = fields[2].substring(1, fields[2].length()-1) + fields[1];
                NetworkInterface ni = NetworkInterface.getByName(name);
                if (ni != null) {
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        System.out.println(addrs.nextElement().getHostAddress());
                    }
                }
            }
        } finally {
            s.close();
        }
    }
}
