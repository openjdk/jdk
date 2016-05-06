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
 * @test SASymbolTableTest
 * @summary Walk symbol table using SA, with and without CDS.
 * @library /testlibrary
 * @modules java.base/jdk.internal.misc
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.memory
 *          jdk.hotspot.agent/sun.jvm.hotspot.runtime
 *          jdk.hotspot.agent/sun.jvm.hotspot.tools
 *          java.management
 * @build SASymbolTableTestAgent SASymbolTableTestAttachee jdk.test.lib.*
 * @run main SASymbolTableTest
 */

import jdk.test.lib.*;

/*
 * The purpose of this test is to validate that we can use SA to
 * attach a process and walk its SymbolTable, regardless whether
 * the attachee process runs in CDS mode or not.
 *
 * SASymbolTableTest Just sets up the agent and attachee processes.
 * The SymbolTable walking is done in the SASymbolTableTestAgent class.
 */
public class SASymbolTableTest {
    static String jsaName = "./SASymbolTableTest.jsa";

    public static void main(String[] args) throws Exception {
        if (!Platform.shouldSAAttach()) {
            System.out.println("SA attach not expected to work - test skipped.");
            return;
        }
        createArchive();
        run(true);
        run(false);
    }

    private static void createArchive()  throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + jsaName,
            "-Xshare:dump");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Loading classes to share");
        output.shouldHaveExitValue(0);
    }

    private static void run(boolean useArchive) throws Exception {
        String flag = useArchive ? "auto" : "off";

        // (1) Launch the attachee process
        ProcessBuilder attachee = ProcessTools.createJavaProcessBuilder(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:SharedArchiveFile=" + jsaName,
            "-Xshare:" + flag,
            "-showversion",                // so we can see "sharing" in the output
            "SASymbolTableTestAttachee");

        final Process p = attachee.start();

        // (2) Launch the agent process
        long pid = p.getPid();
        System.out.println("Attaching agent " + pid);
        ProcessBuilder tool = ProcessTools.createJavaProcessBuilder(
            "-XaddExports:jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED",
            "-XaddExports:jdk.hotspot.agent/sun.jvm.hotspot.memory=ALL-UNNAMED",
            "-XaddExports:jdk.hotspot.agent/sun.jvm.hotspot.runtime=ALL-UNNAMED",
            "-XaddExports:jdk.hotspot.agent/sun.jvm.hotspot.tools=ALL-UNNAMED",
            "SASymbolTableTestAgent",
            Long.toString(pid));
        OutputAnalyzer output = ProcessTools.executeProcess(tool);
        System.out.println(output.getOutput());
        output.shouldHaveExitValue(0);

        Thread t = new Thread() {
                public void run() {
                    try {
                        OutputAnalyzer output = new OutputAnalyzer(p);
                        System.out.println("STDOUT[");
                        System.out.print(output.getStdout());
                        System.out.println("]");
                        System.out.println("STDERR[");
                        System.out.print(output.getStderr());
                        System.out.println("]");
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            };
        t.start();

        Thread.sleep(2 * 1000);
        p.destroy();
        t.join();
    }
}
