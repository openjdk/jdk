/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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
 * @summary Verifies both recoverable and unrecoverable Windows stack overflow handling
 * @requires os.family == "windows" & (os.arch == "amd64" | os.arch == "aarch64")
 * @requires !jdk.static
 * @library /test/lib
 * @run main/native TestWindowsStackOverflow fatal
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 TestWindowsStackOverflow recoverable
 * @run main/othervm -Xcomp -XX:-TieredCompilation TestWindowsStackOverflow recoverable
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestWindowsStackOverflow {
    private static final int EXPECTED_OVERFLOW_COUNT = 5;

    private static native void bangToRed();

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "fatal" -> testFatalOverflow();
            case "native" -> testNativeOverflow();
            case "recoverable" -> testRecoverableOverflow();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static void testFatalOverflow() throws Exception {
        // Make this test call the `bangToRed()` function, which makes a native
        // thread bang the stack into the red zone, after which we check whether
        // HotSpot terminated with the unrecoverable overflow.
        OutputAnalyzer output = ProcessTools.executeTestJava(
                "-XX:-CreateCoredumpOnCrash",
                "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                TestWindowsStackOverflow.class.getName(),
                "native");
        output.shouldNotHaveExitValue(0);
        output.shouldContain("An unrecoverable stack overflow has occurred.");
    }

    private static void testNativeOverflow() {
        System.loadLibrary("TestWindowsStackOverflow");
        bangToRed();

        // HotSpot should have terminated with an unrecoverable overflow.
        String msg = "Unexpected return from native red-zone bang, expected termination";
        throw new AssertionError(msg);
    }

    private static void testRecoverableOverflow() {
        int caughtCount = 0;

        // On Windows, HotSpot uses both EXCEPTION_STACK_OVERFLOW as well as
        // EXCEPTION_ACCESS_VIOLATION to detect overflows and deliver them to
        // the Java application.  This test attempts to cause multiple stack
        // overflow errors to check whether both Windows exceptions are handled.
        for (int idx = 0; idx < EXPECTED_OVERFLOW_COUNT; idx++) {
            try {
                recurse();
            } catch (StackOverflowError err) {
                caughtCount += 1;
            }
        }

        if (caughtCount != EXPECTED_OVERFLOW_COUNT) {
            String msg = "Expected " + EXPECTED_OVERFLOW_COUNT
                    + " overflows, caught " + caughtCount;
            throw new RuntimeException(msg);
        }
    }

    private static void recurse() {
        recurse();
    }
}
