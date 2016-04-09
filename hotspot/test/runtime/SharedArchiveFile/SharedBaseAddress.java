/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test SharedBaseAddress
 * @summary Test variety of values for SharedBaseAddress, making sure
 *          VM handles normal values as well as edge values w/o a crash.
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main SharedBaseAddress
 */

import jdk.test.lib.*;

public class SharedBaseAddress {

    // shared base address test table
    private static final String[] testTable = {
        "1g", "8g", "64g","512g", "4t",
        "32t", "128t", "0",
        "1", "64k", "64M"
    };

    public static void main(String[] args) throws Exception {

        for (String testEntry : testTable) {
            String filename = "SharedBaseAddress" + testEntry + ".jsa";
            System.out.println("sharedBaseAddress = " + testEntry);

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:SharedArchiveFile=" + filename,
               "-XX:SharedBaseAddress=" + testEntry,
               "-Xshare:dump");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());

            output.shouldContain("Loading classes to share");

            try {
                pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:SharedArchiveFile=" + filename,
                    "-Xshare:on",
                    "-version");
                output = new OutputAnalyzer(pb.start());
                output.shouldContain("sharing");
                output.shouldHaveExitValue(0);
            } catch (RuntimeException e) {
                output.shouldContain("Unable to use shared archive");
                output.shouldHaveExitValue(1);
            }
        }
    }
}
