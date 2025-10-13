/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025 SAP SE. All rights reserved.
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
 * @bug 8350642
 * @requires vm.debug & vm.bits == "64"
 * @requires vm.flagless
 * @summary Test the output for CountBytecodes and validate that the counter
 *          does not overflow for more than 2^32 bytecodes counted.
 * @library /test/lib
 * @run driver/timeout=300 CountBytecodesTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CountBytecodesTest {
    private final static long iterations = (1L << 32) / 9;

    public static void main(String args[]) throws Exception {
        if (args.length == 1 && args[0].equals("test")) {
            for (long i = 0; i < iterations; i++) {
                // Just iterating is enough to execute and count bytecodes.
                // According to javap -c this loop translates to the following 9 bytecodes:
                // 19: lload_1
                // 20: ldc2_w        #17
                // 23: lcmp
                // 24: ifge          34
                // 27: lload_1
                // 28: lconst_1
                // 29: ladd
                // 30: lstore_1
                // 31: goto          19
                //
                // Thus we can divide the 2^32 by 9 to set the minimum number of iterations
                // while maintaining execution of more than 2^32 bytecodes.
            }
        } else {
            OutputAnalyzer output = ProcessTools.executeLimitedTestJava("-Xint", "-XX:+CountBytecodes", "CountBytecodesTest", "test");
            output.shouldHaveExitValue(0);

            // Output format: [BytecodeCounter::counter_value = 38676232802]
            output.stdoutShouldContain("BytecodeCounter::counter_value");
            String bytecodesStr = output.firstMatch("BytecodeCounter::counter_value\s*=\s*(\\d+)", 1);
            long bytecodes = Long.parseLong(bytecodesStr);

            System.out.println("Executed bytecodes: " + bytecodes);

            Asserts.assertGTE(bytecodes, 4294967296L);
        }
    }
}
