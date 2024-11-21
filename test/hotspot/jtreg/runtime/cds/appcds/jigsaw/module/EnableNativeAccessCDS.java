/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8342089
 * @summary Test consistency of --enable-native-access option for CDS dump time and runtime
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver EnableNativeAccessCDS
 */

import jdk.test.lib.process.OutputAnalyzer;

public class EnableNativeAccessCDS {
    public static void main(String[] args) throws Exception {
        final String module0 = "java.base";
        final String module1 = "jdk.httpserver";
        final String disabledOptimizedModule = "Disabling optimized module handling";
        final String loggingOption = "-Xlog:cds=debug,cds+module=debug,cds+heap=info,module=trace";

        String archiveName = TestCommon.getNewArchiveName("native-access");
        TestCommon.setCurrentArchiveName(archiveName);

        // dump a base archive with --enable-native-access=java.base
        OutputAnalyzer oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--enable-native-access", module0,
            "-version");
        oa.shouldHaveExitValue(0);

        // same module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module0,
            "-version");
        oa.shouldHaveExitValue(0)
        .shouldContain("use_full_module_graph = true");

        // different module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module1,
            "-version");
        oa.shouldHaveExitValue(0)
        .shouldContain("Mismatched --enable-native-access module name(s).")
        .shouldContain(disabledOptimizedModule);

        // no module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0)
        .shouldContain("--enable-native-access module name(s) found in archive but not specified during runtime")
        .shouldContain(disabledOptimizedModule);

        // dump an archive without the module option
        archiveName = TestCommon.getNewArchiveName("no-native-access-modules");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with --enable-native-access
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module0,
            "-version");
        oa.shouldHaveExitValue(0)
        .shouldContain("--enable-native-access module name(s) specified during runtime but not found in archive")
        .shouldContain(disabledOptimizedModule);

        // dump an archive with multiple modules with native access
        archiveName = TestCommon.getNewArchiveName("multiple-native-access-modules");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--enable-native-access", module0 + "," + module1,
            "-version");
        oa.shouldHaveExitValue(0);

        // same module specified during runtime but in a different order
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module1 + "," + module0,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("use_full_module_graph = true");

        // same module specified during runtime but specifying --enable-native-access twice
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module0,
            "--enable-native-access", module1,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("use_full_module_graph = true");

        // run with only one same module
        oa = TestCommon.execCommon(
            loggingOption,
            "--enable-native-access", module0,
            "-version");
        oa.shouldHaveExitValue(0)
            .shouldContain("Mismatched --enable-native-access module name(s).")
            .shouldContain(disabledOptimizedModule);
    }
}
