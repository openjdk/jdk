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

/*
 * @test
 * @summary Test verifies that VM still can execute java code, allocate
 *          memory, and call GC in the VMDeath event callback.
 *
 * @bug 8367902
 * @requires vm.jvmti
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native TestAllocatingInVMDeath
 */
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestAllocatingInVMDeath {
    public static String UPCALL_MARKER = "Hello from upCall. ";

    public static void main(String[] args) throws Exception {
    ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "-agentlib:TestAllocatingInVMDeath",
                "--enable-native-access=ALL-UNNAMED",
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "DoWork");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        String output = oa.getOutput();
        System.err.println("DoWork output:");
        System.err.println(output);
        Asserts.assertTrue(oa.getExitValue() == 0);
        Asserts.assertTrue(output.contains(UPCALL_MARKER));
    }
}

class DoWork {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    // This method is called from VMDeath event callback.
    static void upCall() {
        // Do some work including memory allocation, so it can't be optimized by compiler.
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        String result = TestAllocatingInVMDeath.UPCALL_MARKER + now.format(formatter);
        WHITE_BOX.fullGC();
        System.out.println(result);
    }

    public static void main(String argv[]) throws Exception {
        System.out.println("Hello from DoWork main().");
    }
}
