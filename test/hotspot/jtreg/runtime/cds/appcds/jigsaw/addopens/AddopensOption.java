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
 * @bug 8352003
 * @summary Test handling of the --add-opens option.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AddopensOption
 */

import jdk.test.lib.process.OutputAnalyzer;

public class AddopensOption {

    public static void main(String[] args) throws Exception {
        final String moduleOption = "jdk.httpserver/sun.net.httpserver.simpleserver.Main";
        final String addOpensNio = "java.base/java.nio=ALL-UNNAMED";
        final String addOpensTimeFormat = "java.base/java.time.format=ALL-UNNAMED";
        final String loggingOption = "-Xlog:aot=debug,aot+module=debug,aot+heap=info,cds=debug,module=trace";
        final String versionPattern = "java.[0-9][0-9].*";
        final String subgraphCannotBeUsed = "subgraph jdk.internal.module.ArchivedBootLayer cannot be used because full module graph is disabled";
        final String warningIncubator = "WARNING: Using incubator modules: jdk.incubator.vector";
        String archiveName = TestCommon.getNewArchiveName("addopens-option");
        TestCommon.setCurrentArchiveName(archiveName);

        // dump a base archive with --add-opens jdk.java.base/java.time.format -m jdk.httpserver
        OutputAnalyzer oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-opens", addOpensTimeFormat,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // same modules specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-opens", addOpensTimeFormat,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          // version of the jdk.httpserver module, e.g. java 22-ea
          .shouldMatch(versionPattern)
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.httpserver");

        // different --add-opens specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-opens", addOpensNio,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Mismatched values for property jdk.module.addopens")
          .shouldContain("runtime java.base/java.nio=ALL-UNNAMED dump time java.base/java.time.format=ALL-UNNAMED")
          .shouldContain(subgraphCannotBeUsed);

        // no module specified during runtime
        oa = TestCommon.execCommon(
            loggingOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("jdk.httpserver specified during dump time but not during runtime")
          .shouldContain(subgraphCannotBeUsed);

        // dump an archive without the --add-opens option
        archiveName = TestCommon.getNewArchiveName("no-addopens-option");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with --add-opens option
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-opens", addOpensTimeFormat,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("java.base/java.time.format=ALL-UNNAMED specified during runtime but not during dump time")
          // version of the jdk.httpserver module, e.g. java 22-ea
          .shouldMatch(versionPattern)
          .shouldContain(subgraphCannotBeUsed);

        // dump an archive with -add-opens java.base/java.nio=ALL-UNNAMED
        archiveName = TestCommon.getNewArchiveName("addopens-java-nio");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-opens", addOpensNio,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldContain("Full module graph = enabled");

        // run with the same --add-opens
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-opens", addOpensNio,
            "-m", moduleOption,
            "-version");
        oa.shouldContain("optimized module handling: enabled")
          .shouldHaveExitValue(0);

        // dump an archive with multiple --add-modules args
        archiveName = TestCommon.getNewArchiveName("muti-addopens");
        TestCommon.setCurrentArchiveName(archiveName);
        oa = TestCommon.dumpBaseArchive(
            archiveName,
            loggingOption,
            "--add-opens", addOpensNio,
            "--add-opens", addOpensTimeFormat,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0);

        // run with the same multiple --add-modules args with a duplicate --add-opens entry
        oa = TestCommon.execCommon(
            loggingOption,
            "--add-opens", addOpensTimeFormat,
            "--add-opens", addOpensNio,
            "--add-opens", addOpensTimeFormat,
            "-m", moduleOption,
            "-version");
        oa.shouldHaveExitValue(0)
          .shouldMatch("aot,module.*Restored from archive: entry.0x.*name jdk.httpserver");
    }
}
