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
 * @test SharedStringsAuto
 * @summary Test -Xshare:auto with shared strings.
 * Feature support: G1GC only, compressed oops/kptrs, 64-bit os, not on windows
 * @requires (sun.arch.data.model != "32") & (os.family != "windows")
 * @requires (vm.opt.UseCompressedOops == null) | (vm.opt.UseCompressedOops == true)
 * @requires (vm.gc=="G1" | vm.gc=="null")
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run main SharedStringsRunAuto
 */

import jdk.test.lib.*;
import java.io.File;

public class SharedStringsRunAuto {
    public static void main(String[] args) throws Exception {
        // Dump
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=./SharedStringsRunAuto.jsa",
            "-XX:+UseCompressedOops", "-XX:+UseG1GC",
            "-XX:+PrintSharedSpaces",
            "-Xshare:dump");

        new OutputAnalyzer(pb.start())
            .shouldContain("Loading classes to share")
            .shouldContain("Shared string table stats")
            .shouldHaveExitValue(0);

        // Run with -Xshare:auto
        pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./SharedStringsRunAuto.jsa",
           "-XX:+UseCompressedOops", "-XX:+UseG1GC",
           "-Xshare:auto",
           "-version");

        new OutputAnalyzer(pb.start())
            .shouldMatch("(java|openjdk) version")
            .shouldHaveExitValue(0);
    }
}
