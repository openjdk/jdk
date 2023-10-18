/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316969
 * @summary Test handling of module option (-m).
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver ModuleOption
 */

import jdk.test.lib.process.OutputAnalyzer;

public class ModuleOption {
    public static void main(String[] args) throws Exception {
        final String moduleOption = "jdk.compiler/com.sun.tools.javac.Main";
        final String loggingOption = "-Xlog:cds=debug,cds+module=debug";
        final String javacVersionPattern = "javac.[0-9][0-9][-].*";
        String archiveName = TestCommon.getNewArchiveName("module-option");
        TestCommon.setCurrentArchiveName(archiveName);

        OutputAnalyzer oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // same module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          // version of the jdk.compiler module, e.g. javac 22-ea
          .shouldMatch(javacVersionPattern)
          .shouldMatch("cds,module.*Restored from archive: entry.0x.*name jdk.compiler");

        // different module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-m", "jdk.httpserver/sun.net.httpserver.simpleserver.Main",
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Mismatched modules: runtime jdk.httpserver dump time jdk.compiler");

        // no module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Module jdk.compiler specified during dump time but not during runtime");

        // dump an archive without the module option
        archiveName = TestCommon.getNewArchiveName("no-module-option");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with module option
        oa = TestCommon.execCommon(
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Module jdk.compiler specified during runtime but not during dump time")
          // version of the jdk.compiler module, e.g. javac 22-ea
          .shouldMatch(javacVersionPattern);
    }
}
