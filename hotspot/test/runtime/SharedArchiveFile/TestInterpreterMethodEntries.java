/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test InterpreterMethodEntries
 * @bug 8169711
 * @summary Test interpreter method entries for intrinsics with CDS (class data sharing)
 *          and different settings of the intrinsic flag during dump/use of the archive.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main TestInterpreterMethodEntries
 */

import java.lang.Math;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestInterpreterMethodEntries {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
          // Dump and use shared archive with different flag combinations
          dumpAndUseSharedArchive("+", "-");
          dumpAndUseSharedArchive("-", "+");
        } else {
          // Call intrinsified java.lang.Math::fma()
          Math.fma(1.0, 2.0, 3.0);

          byte[] buffer = new byte[256];
          // Call intrinsified java.util.zip.CRC32::update()
          CRC32 crc32 = new CRC32();
          crc32.update(buffer, 0, 256);

          // Call intrinsified java.util.zip.CRC32C::updateBytes(..)
          CRC32C crc32c = new CRC32C();
          crc32c.update(buffer, 0, 256);
        }
    }

    private static void dumpAndUseSharedArchive(String dump, String use) throws Exception {
        String dumpFMA    = "-XX:" + dump + "UseFMA";
        String dumpCRC32  = "-XX:" + dump + "UseCRC32Intrinsics";
        String dumpCRC32C = "-XX:" + dump + "UseCRC32CIntrinsics";
        String useFMA     = "-XX:" + use  + "UseFMA";
        String useCRC32   = "-XX:" + use  + "UseCRC32Intrinsics";
        String useCRC32C  = "-XX:" + use  + "UseCRC32CIntrinsics";

        // Dump shared archive
        String filename = "./TestInterpreterMethodEntries" + dump + ".jsa";
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:dump",
            dumpFMA, dumpCRC32, dumpCRC32C);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        CDSTestUtils.checkDump(output);

        // Use shared archive
        pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + filename,
            "-Xshare:on",
            useFMA, useCRC32, useCRC32C,
            "TestInterpreterMethodEntries", "run");
        output = new OutputAnalyzer(pb.start());
        if (CDSTestUtils.isUnableToMap(output)) {
          System.out.println("Unable to map shared archive: test did not complete; assumed PASS");
          return;
        }
        output.shouldHaveExitValue(0);
    }
}

