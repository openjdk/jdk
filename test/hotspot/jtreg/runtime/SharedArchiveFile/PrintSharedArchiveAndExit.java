/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8066670
 * @summary Testing -XX:+PrintSharedArchiveAndExit option
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;


public class PrintSharedArchiveAndExit {
    public static void main(String[] args) throws Exception {
        String archiveName = "PrintSharedArchiveAndExit.jsa";
        CDSOptions opts = (new CDSOptions()).setArchiveName(archiveName);
        OutputAnalyzer out = CDSTestUtils.createArchive(opts);
        CDSTestUtils.checkDump(out);

        // (1) With a valid archive
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./" + archiveName,
                "-XX:+PrintSharedArchiveAndExit", "-version");
        out = CDSTestUtils.executeAndLog(pb, "print-shared-archive-and-version");
        if (!CDSTestUtils.isUnableToMap(out)) {
            out.shouldContain("archive is valid")
                .shouldNotContain("java version")     // Should not print JVM version
                .shouldHaveExitValue(0);              // Should report success in error code.
        }

        pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions", "-XX:SharedArchiveFile=./" + archiveName,
                "-XX:+PrintSharedArchiveAndExit");
        out = CDSTestUtils.executeAndLog(pb, "print-shared-archive");
        if (!CDSTestUtils.isUnableToMap(out)) {
            out.shouldContain("archive is valid")
                .shouldNotContain("Usage:")           // Should not print JVM help message
                .shouldHaveExitValue(0);               // Should report success in error code.
        }
    }
}
