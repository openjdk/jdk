/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308903
 * @summary Test the contents of -Xlog:aot+map
 * @requires vm.flagless
 * @requires vm.cds
 * @library /test/lib
 * @run driver CDSMapTest
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.ArrayList;

public class CDSMapTest {
    public static void main(String[] args) throws Exception {
        doTest(false);

        if (Platform.is64bit()) {
            // There's no oop/klass compression on 32-bit.
            doTest(true);
        }
    }

    public static void doTest(boolean compressed) throws Exception {
        ArrayList<String> vmArgs = new ArrayList<>();

        // Use the same heap size as make/Images.gmk
        vmArgs.add("-Xmx128M");

        if (Platform.is64bit()) {
            // These options are available only on 64-bit.
            String sign = (compressed) ?  "+" : "-";
            vmArgs.add("-XX:" + sign + "UseCompressedOops");
        }

        String archiveFile = dump(vmArgs);
        exec(vmArgs, archiveFile);

    }

    static int id = 0;

    // Create a map file when creating the archive
    static String dump(ArrayList<String> args) throws Exception {
        String logName = "SharedArchiveFile" + (id++);
        String archiveName = logName + ".jsa";
        String mapName = logName + ".map";
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-Xlog:cds=debug")
            // filesize=0 ensures that a large map file not broken up in multiple files.
            .addPrefix("-Xlog:aot+map=debug,aot+map+oops=trace:file=" + mapName + ":none:filesize=0")
            .setArchiveName(archiveName)
            .addSuffix(args);
        CDSTestUtils.createArchiveAndCheck(opts);

        CDSMapReader.MapFile mapFile = CDSMapReader.read(mapName);
        CDSMapReader.validate(mapFile);

        return archiveName;
    }

    // Create a map file when using the archive
    static void exec(ArrayList<String> vmArgs, String archiveFile) throws Exception {
        String mapName = archiveFile + ".exec.map";
        vmArgs.add("-XX:SharedArchiveFile=" + archiveFile);
        vmArgs.add("-Xlog:cds=debug");
        vmArgs.add("-Xshare:on");
        vmArgs.add("-Xlog:aot+map=debug,aot+map+oops=trace:file=" + mapName + ":none:filesize=0");
        vmArgs.add("--version");
        String[] cmdLine = vmArgs.toArray(new String[vmArgs.size()]);
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(cmdLine);
        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "exec");
        out.shouldHaveExitValue(0);

        CDSMapReader.MapFile mapFile = CDSMapReader.read(mapName);
        CDSMapReader.validate(mapFile);
    }
}
