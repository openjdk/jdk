/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005933
 * @summary Test that -Xshare:auto uses CDS when explicitly specified with -server.
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main XShareAuto
 */

import jdk.test.lib.*;

public class XShareAuto {
    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-server", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./XShareAuto.jsa", "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
            "-server", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./XShareAuto.jsa", "-version");
        output = new OutputAnalyzer(pb.start());
        // We asked for server but it could be aliased to something else
        if (output.getOutput().contains("Server VM")) {
            // In server case we don't expect to see sharing flag
            output.shouldNotContain("sharing");
            output.shouldHaveExitValue(0);
        }
        else {
            System.out.println("Skipping test - no Server VM available");
            return;
        }

        pb = ProcessTools.createJavaProcessBuilder(
            "-server", "-Xshare:auto", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./XShareAuto.jsa", "-XX:+PrintSharedSpaces", "-version");
        output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("sharing");
        } catch (RuntimeException e) {
            // if sharing failed due to ASLR or similar reasons,
            // check whether sharing was attempted at all (UseSharedSpaces)
            output.shouldContain("UseSharedSpaces:");
            output.shouldNotContain("Unable to map %s");
        }
        output.shouldHaveExitValue(0);
    }
}
