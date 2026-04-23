/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package compiler.c2.igvn;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8379629
 * @summary Validate parsing of the StressDeepIGVNRevisit flag.
 * @library /test/lib
 * @requires vm.compiler2.enabled
 * @requires vm.debug
 * @requires vm.flagless
 * @run driver ${test.main.class}
 */
public class TestStressDeepIGVNRevisitFlag {

    public static void main(String[] args) throws Exception {
        run(0, null, "-XX:StressDeepIGVNRevisit=all");
        run(0, null, "-XX:StressDeepIGVNRevisit=random");
        run(1, "Unrecognized value foo for StressDeepIGVNRevisit", "-XX:StressDeepIGVNRevisit=foo");
        run(1, "StressDeepIGVNRevisit cannot be used with disabled UseDeepIGVNRevisit",
            "-XX:-UseDeepIGVNRevisit", "-XX:StressDeepIGVNRevisit=all");
    }

    private static void run(int expectedExitValue, String expectedOutput, String... extraArgs) throws Exception {
        String[] args = new String[extraArgs.length + 1];
        System.arraycopy(extraArgs, 0, args, 0, extraArgs.length);
        args[args.length - 1] = "-version";
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        if (expectedOutput != null) {
            out.shouldContain(expectedOutput);
        }
        out.shouldHaveExitValue(expectedExitValue);
    }
}
