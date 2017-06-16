/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Lookup/reverse lookup class for regression test 4773521
 * @test
 * @bug 4773521
 * @summary Test that reverse lookups of IPv4 addresses work when IPv6
 *          is enabled
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 *        Lookup
 * @run main Lookup root
 *
 */
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;

public class Lookup {
    private static final String HOST = "icann.org";
    private static final String SKIP = "SKIP";
    private static final String CLASS_PATH = System.getProperty(
            "test.class.path");

    public static void main(String args[]) throws IOException {
        String addr = null;
        String ipv4Name = null;
        if (args.length == 0) {
            // First check that host resolves to IPv4 address
            try {
                InetAddress ia = InetAddress.getByName(HOST);
                addr = ia.getHostAddress();
            } catch (UnknownHostException e) {
                System.out.print(SKIP);
                return;
            }
        } else {
            String tmp = lookupWithIPv4Prefer();
            System.out.println("IPv4 lookup results: [" + tmp + "]");
            if (SKIP.equals(tmp)) {
                System.out.println(HOST + " can't be resolved - test skipped.");
                return;
            }

            String[] strs = tmp.split(":");
            addr = strs[0];
            ipv4Name = strs[1];
        }

        // reverse lookup
        InetAddress ia = InetAddress.getByName(addr);
        String name = ia.getHostName();
        if (args.length == 0) {
            System.out.print(addr + ":" + name);
            return;
        } else {
            System.out.println("(default) " + addr + "--> " + name);
            if (!ipv4Name.equals(name)) {
                throw new RuntimeException("Mismatch between default"
                        + " and java.net.preferIPv4Stack=true results");
            }
        }
    }

    static String lookupWithIPv4Prefer() throws IOException {
        String java = JDKToolFinder.getTestJDKTool("java");
        String testClz = Lookup.class.getName();
        List<String> cmd = List.of(java, "-Djava.net.preferIPv4Stack=true",
                "-cp", CLASS_PATH, testClz);
        System.out.println("Executing: " + cmd);
        return new OutputAnalyzer(new ProcessBuilder(cmd).start()).getOutput();
    }
}

