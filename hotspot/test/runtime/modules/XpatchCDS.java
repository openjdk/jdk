/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 * @run main XpatchCDS
 */

import java.io.File;
import jdk.test.lib.*;

public class XpatchCDS {

    public static void main(String args[]) throws Throwable {
        System.out.println("Test that -Xpatch and -Xshare:dump are incompatibable");
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xpatch:.", "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Cannot use the following option when dumping the shared archive: -Xpatch");

        System.out.println("Test that -Xpatch and -Xshare:on are incompatibable");
        String filename = "Xpatch.jsa";
        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:dump");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("ro space:"); // Make sure archive got created.

        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:on",
            "-Xpatch:.",
            "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("The shared archive file cannot be used with -Xpatch");

        output.shouldHaveExitValue(1);
    }
}
