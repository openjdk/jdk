/*
 * Copyright (c) 2019, SAP SE. All rights reserved.
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

import jdk.test.lib.*;
import jdk.test.lib.process.*;

/*
 * @test TestAbortVMOnSafepointTimeout
 * @summary Check if VM can kill thread which doesn't reach safepoint.
 * @bug 8219584
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

public class TestAbortVMOnSafepointTimeout {

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            int result = test_loop(3);
            System.out.println("This message would occur after some time with result " + result);
            return;
        }

        testWith(500, 500);
    }

    static int test_loop(int x) {
        int sum = 0;
        if (x != 0) {
            // Long running loop without safepoint.
            for (int y = 1; y < Integer.MAX_VALUE; ++y) {
                if (y % x == 0) ++sum;
            }
        }
        return sum;
    }

    public static void testWith(int sfpt_interval, int timeout_delay) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+SafepointTimeout",
                "-XX:+SafepointALot",
                "-XX:+AbortVMOnSafepointTimeout",
                "-XX:SafepointTimeoutDelay=" + timeout_delay,
                "-XX:GuaranteedSafepointInterval=" + sfpt_interval,
                "-XX:-TieredCompilation",
                "-XX:-UseCountedLoopSafepoints",
                "-XX:LoopStripMiningIter=0",
                "-XX:LoopUnrollLimit=0",
                "-XX:CompileCommand=compileonly,TestAbortVMOnSafepointTimeout::test_loop",
                "-Xcomp",
                "-XX:-CreateCoredumpOnCrash",
                "-Xms64m",
                "TestAbortVMOnSafepointTimeout",
                "runTestLoop"
        );

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        if (Platform.isWindows()) {
            output.shouldMatch("Safepoint sync time longer than");
        } else {
            output.shouldMatch("SIGILL");
            if (Platform.isLinux()) {
                output.shouldMatch("(sent by kill)");
            }
            output.shouldMatch("TestAbortVMOnSafepointTimeout.test_loop");
        }
        output.shouldNotHaveExitValue(0);
    }
}
