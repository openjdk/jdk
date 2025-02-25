/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the output for CountBytecodes and validate that the counter
 *          does not overflow for more than 2^32 bytecodes counted.
 * @library /test/lib
 * @run main/othervm/timeout=300 -Xint -XX:+CountBytecodes CountBytecodesTest
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CountBytecodesTest {
    private final static long iterations = 1L << 32;

    public static void main(String args[]) throws Exception {
        if (args.length == 1 && args[0].equals("test")) {
            for (long i = 0; i < iterations; i++) {
                // Just iterating is enough to execute and count bytecodes.
            }
        } else {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("-Xint", "-XX:+CountBytecodes", "CountBytecodesTest", "test");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);

            // Output format: [BytecodeCounter::counter_value = 38676232802]
            output.stdoutShouldContain("BytecodeCounter::counter_value");
            String bytecodesStr = output.firstMatch("BytecodeCounter::counter_value\s*=\s*(\\d+)", 1);
            long bytecodes = Long.parseLong(bytecodesStr);

            Asserts.assertGTE(bytecodes, 4294967296L);
        }
    }
}
