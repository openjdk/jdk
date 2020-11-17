/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package compiler.debug;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8252219
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Tests that compilations with the same seed yield the same IGVN
 *          trace.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressIGVN
 */

public class TestStressIGVN {

    static String igvnTrace(int stressSeed) throws Exception {
        String className = TestStressIGVN.class.getName();
        String[] procArgs = {
            "-Xcomp", "-XX:-TieredCompilation", "-XX:-Inline",
            "-XX:CompileOnly=" + className + "::sum", "-XX:+TraceIterativeGVN",
            "-XX:+StressIGVN", "-XX:StressSeed=" + stressSeed,
            className, "10"};
        ProcessBuilder pb  = ProcessTools.createJavaProcessBuilder(procArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        return out.getStdout();
    }

    static void sum(int n) {
        int acc = 0;
        for (int i = 0; i < n; i++) acc += i;
        System.out.println(acc);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (int s = 0; s < 10; s++) {
                Asserts.assertEQ(igvnTrace(s), igvnTrace(s),
                    "got different IGVN traces for the same seed");
            }
        } else if (args.length > 0) {
            sum(Integer.parseInt(args[0]));
        }
    }
}
