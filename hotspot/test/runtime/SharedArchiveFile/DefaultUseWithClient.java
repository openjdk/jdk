/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test DefaultUseWithClient
 * @summary Test default behavior of sharing with -client
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main DefaultUseWithClient
 * @bug 8032224
 */

import jdk.test.lib.*;
import java.io.File;

public class DefaultUseWithClient {
    public static void main(String[] args) throws Exception {
        String fileName = "DefaultUseWithClient.jsa";

        // On 32-bit windows CDS should be on by default in "-client" config
        // Skip this test on any other platform
        boolean is32BitWindows = (Platform.isWindows() && Platform.is32bit());
        if (!is32BitWindows) {
            System.out.println("Test only applicable on 32-bit Windows. Skipping");
            return;
        }

        // create the archive
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./" + fileName,
           "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./" + fileName,
           "-client",
           "-XX:+PrintSharedSpaces",
           "-version");

        output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("sharing");
        } catch (RuntimeException e) {
            // if sharing failed due to ASLR or similar reasons,
            // check whether sharing was attempted at all (UseSharedSpaces)
            output.shouldContain("UseSharedSpaces:");
        }
        output.shouldHaveExitValue(0);
   }
}
