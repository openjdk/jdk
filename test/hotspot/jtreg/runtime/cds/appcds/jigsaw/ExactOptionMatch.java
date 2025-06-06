/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary FMG are supported if the exact values are used for training/assembly/run for
 *          --add-exports, --add-modules, and -enable-native-access.
 * @bug 8352437
 * @modules java.logging
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver ExactOptionMatch
 */

import jdk.test.lib.process.OutputAnalyzer;

public class ExactOptionMatch {
    record Option(String cmdLine, String property, String valueA, String valueB) {}

    static Option[] allOptions = new Option[] {
        new Option("--add-opens",
                   "jdk.module.addopens",
                   "java.base/java.util.concurrent.regex=ALL-UNNAMED",
                   "java.base/sun.security.x509=ALL-UNNAMED"),
        new Option("--add-exports",
                   "jdk.module.addexports",
                   "java.base/jdk.internal.misc=ALL-UNNAMED",
                   "java.base/jdk.internal.misc=ALL-SYSTEM"),
        new Option("--add-modules",
                   "jdk.module.addmods",
                   "java.base",
                   "java.logging"),
        new Option("--enable-native-access",
                   "jdk.module.enable.native.access",
                   "java.base",
                   "java.logging"),
    };

    static final String FMG_DISABLED = "initial full module graph: disabled";
    static final String FMG_ENABLED = "use_full_module_graph = true";

    public static void main(String[] args) throws Exception {
        OutputAnalyzer out;

        for (Option o : allOptions) {
            TestCommon.startNewArchiveName();
            String archiveName = TestCommon.getCurrentArchiveName();

            // (1) Dump = specified, Run = not specified
            TestCommon.dumpBaseArchive(archiveName, o.cmdLine(), o.valueA())
                .shouldHaveExitValue(0);

            TestCommon.execCommon("-Xlog:aot", "-Xlog:cds", "--version")
                .shouldMatch("Mismatched values for property " + o.property() + ": j.*specified during dump time but not during runtime")
                .shouldContain(FMG_DISABLED);

            // (2) Dump = not specified, Run = specified
            TestCommon.dumpBaseArchive(archiveName)
                .shouldHaveExitValue(0);

            TestCommon.execCommon("-Xlog:aot", "-Xlog:cds", o.cmdLine(), o.valueA(), "--version")
                .shouldMatch("Mismatched values for property " + o.property() + ": j.*specified during runtime but not during dump time")
                .shouldContain(FMG_DISABLED);

            // (3) Dump = specified twice
            TestCommon.dumpBaseArchive(archiveName, o.cmdLine(), o.valueA(), o.cmdLine(), o.valueB())
                .shouldHaveExitValue(0);

            // (3.1) Run = specified once
            TestCommon.execCommon("-Xlog:aot", "-Xlog:cds", o.cmdLine(), o.valueA(), "--version")
                .shouldMatch("Mismatched values for property " + o.property() + ": runtime.*dump time")
                .shouldContain(FMG_DISABLED);

            // (3.2) Run = specified twice (same order)
            //       Should still be able to use FMG.
            TestCommon.execCommon("-Xlog:aot", "-Xlog:cds", o.cmdLine(), o.valueA(), o.cmdLine(), o.valueB(), "--version")
                .shouldContain(FMG_ENABLED);

            // (3.3) Run = specified twice (but in different order)
            //       Should still be able to use FMG (values are sorted by CDS).
            TestCommon.execCommon("-Xlog:aot", "-Xlog:cds", o.cmdLine(), o.valueB(), o.cmdLine(), o.valueA(), "--version")
                .shouldContain(FMG_ENABLED);
        }
    }
}
