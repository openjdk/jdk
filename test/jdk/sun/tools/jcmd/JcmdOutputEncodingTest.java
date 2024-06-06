/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.JDKToolLauncher;

/*
 * @test
 * @bug 8222491 8273187 8308033
 * @summary Tests if we handle the encoding of jcmd output correctly.
 * @library /test/lib
 * @run main JcmdOutputEncodingTest
 */
public class JcmdOutputEncodingTest {

    public static void main(String[] args) throws Exception {
        testThreadDump();
    }

    private static void testThreadDump() throws Exception {
        String marker = "markerName" + "\u00e4\u0bb5".repeat(60);
        Charset cs = StandardCharsets.UTF_8;
        Thread.currentThread().setName(marker);

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jcmd");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        launcher.addVMArg("-Dfile.encoding=" + cs);
        launcher.addVMArg("-Dsun.stdout.encoding=" + cs);
        launcher.addToolArg(Long.toString(ProcessTools.getProcessId()));
        boolean isVirtualThread = Thread.currentThread().isVirtual();
        Path threadDumpFile = null;
        if (isVirtualThread) {
            // "jcmd Thread.print" will not print thread dumps of virtual threads.
            // So we use "Thread.dump_to_file" command instead and dump the thread
            // stacktraces in a file
            threadDumpFile = Files.createTempFile(Path.of("."), "jcmd", ".tdump").toAbsolutePath();
            launcher.addToolArg("Thread.dump_to_file");
            launcher.addToolArg("-overwrite");
            launcher.addToolArg(threadDumpFile.toString());
        } else {
            launcher.addToolArg("Thread.print");
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(launcher.getCommand());
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder, null, cs);
        output.shouldHaveExitValue(0);
        if (isVirtualThread) {
            // verify the file containing the thread dump has the expected text
            try (var br = Files.newBufferedReader(threadDumpFile, cs)) {
                String line = null;
                boolean found = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains(marker)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    output.reportDiagnosticSummary();
                    throw new RuntimeException("'" + marker + "' missing in thread dump in file "
                            + threadDumpFile);
                }
            }
        } else {
            output.shouldContain(marker);
        }
    }
}
