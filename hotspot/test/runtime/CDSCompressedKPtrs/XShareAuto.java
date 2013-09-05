/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @run main XShareAuto
 */

import com.oracle.java.testlibrary.*;

public class XShareAuto {
    public static void main(String[] args) throws Exception {
        if (!Platform.is64bit()) {
            System.out.println("ObjectAlignmentInBytes for CDS is only " +
                "supported on 64bit platforms; this plaform is " +
                System.getProperty("sun.arch.data.model"));
            System.out.println("Skipping the test");
            return;
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./sample.jsa",
            "-Xshare:dump");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
            "-server", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa", "-version");
        output = new OutputAnalyzer(pb.start());
        output.shouldNotContain("sharing");
        output.shouldHaveExitValue(0);

        pb = ProcessTools.createJavaProcessBuilder(
            "-server", "-Xshare:auto", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./sample.jsa", "-version");
        output = new OutputAnalyzer(pb.start());
        try {
            output.shouldContain("sharing");
            output.shouldHaveExitValue(0);
        } catch (RuntimeException e) {
            // If this failed then check that it would also be unable
            // to share even if -Xshare:on is specified.  If so, then
            // return a success status.
            pb = ProcessTools.createJavaProcessBuilder(
                "-server", "-Xshare:on", "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=./sample.jsa", "-version");
            output = new OutputAnalyzer(pb.start());
            output.shouldContain("Could not allocate metaspace at a compatible address");
            output.shouldHaveExitValue(1);
        }
    }
}
