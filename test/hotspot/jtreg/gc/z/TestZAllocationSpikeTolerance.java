/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestZAllocationSpikeTolerance
 * @bug 8389866
 * @summary Verifies TestZAllocationSpikeTolerance range
 *          - fails gracefully for invalid value
 *          - succeeds for valid value
 * @library /test/lib
 * @requires vm.gc.Z
 * @run driver gc.z.TestZAllocationSpikeTolerance
 */
package gc.z;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestZAllocationSpikeTolerance {
    public static void main(String[] args) throws Exception {
        testInvalidValue();
        testValidValue();
    }

    private static void testInvalidValue() throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UseZGC",
            "-XX:ZAllocationSpikeTolerance=-1", // invalid range
            "-version"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Ensure no crash (no assert failure)
        output.shouldNotContain("assert");

        // Expected graceful error output
        output.shouldContain("ZAllocationSpikeTolerance");
        output.shouldContain("outside the allowed range");
        output.shouldContain("Error: A fatal exception has occurred. Program will exit.");

        // Graceful exit with error code 1
        output.shouldHaveExitValue(1);
    }

    private static void testValidValue() throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:+UseZGC",
            "-XX:ZAllocationSpikeTolerance=1", // valid range
            "-version"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        output.shouldHaveExitValue(0);
    }
}
