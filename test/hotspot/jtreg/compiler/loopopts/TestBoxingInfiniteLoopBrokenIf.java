/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test
 * @bug 8378005
 * @summary Verify that an infinite loop with boxing does not crash when the containing If is broken
 * @library /test/lib /
 * @run driver TestBoxingInfiniteLoopBrokenIf
 */

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;

public class TestBoxingInfiniteLoopBrokenIf {
    static boolean flag;
    static volatile boolean testReturned;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
            new Scenario(0),
            new Scenario(1, "-XX:PerMethodTrapLimit=0", "-XX:CompileCommand=dontinline,*::valueOf", "-XX:CompileCommand=dontinline,*::throwWrongPath"),
            new Scenario(2, "-XX:PerMethodTrapLimit=0", "-XX:CompileCommand=dontinline,*::valueOf", "-XX:CompileCommand=dontinline,*::throwWrongPath",
                            "-XX:+IgnoreUnrecognizedVMOptions", "-XX:StressLongCountedLoop=1")
        );
        framework.start();
    }

    @Test
    static void test() {
        new Integer(0); // Enable EA + Loop opts

        if (flag) { // Always false
            throwWrongPath(); // Not inlined to simplify graph
        } else {
            // Infinite loop: j is always 0, Integer.valueOf(0) < 1 is always true.
            for (int j = 0; Integer.valueOf(j) < 1;) {
                j = 0;
            }
        }
    }

    @Run(test = "test")
    @Warmup(0)
    static void runTest(RunInfo info) {
        testReturned = false;
        Thread thread = new Thread(() -> {
            test();
            testReturned = true;
        });
        thread.setDaemon(true);
        thread.start();
        try {
            Thread.sleep(Utils.adjustTimeout(500));
        } catch (InterruptedException e) {
        }
        if (testReturned) {
            throw new RuntimeException("test() should not return: infinite loop was incorrectly optimized away");
        }
    }

    static void throwWrongPath() {
        throw new RuntimeException("Wrong path");
    }
}
