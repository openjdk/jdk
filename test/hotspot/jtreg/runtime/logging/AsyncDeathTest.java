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
 * @bug 8349755
 * @summary Perform recursive logging in async UL on purpose
 * @requires vm.flagless
 * @requires vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver AsyncDeathTest
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class AsyncDeathTest {
    public static void main(String[] args) throws Exception {
        // For deathtest we expect the VM to reach ShouldNotReachHere() and die
        ProcessBuilder pb =
            ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:async", "-Xlog:os,deathtest=debug", "-XX:-CreateCoredumpOnCrash", "--version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldNotContain("Induce a recursive log for testing");
        // For deathtest2 we expect the VM to ignore that recursive logging has been detected and is handled by printing synchronously.
        ProcessBuilder pb2 =
            ProcessTools.createLimitedTestJavaProcessBuilder("-Xlog:async", "-Xlog:os,deathtest2=debug", "--version");
        OutputAnalyzer output2 = new OutputAnalyzer(pb2.start());
        output2.shouldHaveExitValue(0);
        output2.shouldContain("Induce a recursive log for testing");
    }
}
