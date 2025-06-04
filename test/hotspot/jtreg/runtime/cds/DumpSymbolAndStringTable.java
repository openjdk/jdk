/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059510 8213445
 * @summary Test jcmd VM.symboltable, VM.stringtable and VM.systemdictionary options
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI DumpSymbolAndStringTable
 */
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.WhiteBox;

public class DumpSymbolAndStringTable {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        boolean sharingEnabled = wb.isSharingEnabled();

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new PidJcmdExecutor().getCommandLine("VM.symboltable", "-verbose"));
        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "jcmd-symboltable");
        final String sharedSymbolsHeader = "Shared symbols:\n";
        try {
            output.shouldContain("24 2: DumpSymbolAndStringTable\n");
            if (sharingEnabled) {
                output.shouldContain(sharedSymbolsHeader);
                output.shouldContain("17 65535: java.lang.runtime\n");
            }
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }

        pb.command(new PidJcmdExecutor().getCommandLine("VM.stringtable", "-verbose"));
        output = CDSTestUtils.executeAndLog(pb, "jcmd-stringtable");
        final String sharedStringsHeader = "Shared strings:\n";
        try {
            output.shouldContain("24: DumpSymbolAndStringTable\n");
            if (sharingEnabled && wb.canWriteJavaHeapArchive()) {
                output.shouldContain(sharedStringsHeader);
                if (!wb.isSharedInternedString("MILLI_OF_SECOND")) {
                    throw new RuntimeException("'MILLI_OF_SECOND' should be a shared string");
                }
            }
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }

        pb.command(new PidJcmdExecutor().getCommandLine("VM.systemdictionary"));
        output = CDSTestUtils.executeAndLog(pb, "jcmd-systemdictionary");
        try {
            output.shouldContain("System Dictionary for 'app' class loader statistics:");
            output.shouldContain("Number of buckets");
            output.shouldContain("Number of entries");
            output.shouldContain("Maximum bucket size");
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }

        pb.command(new PidJcmdExecutor().getCommandLine("VM.systemdictionary", "-verbose"));
        output = CDSTestUtils.executeAndLog(pb, "jcmd-systemdictionary");
        try {
            output.shouldContain("Dictionary for loader data: 0x");
            output.shouldContain("^java.lang.String");
        } catch (RuntimeException e) {
            output.shouldContain("Unknown diagnostic command");
        }
    }
}
