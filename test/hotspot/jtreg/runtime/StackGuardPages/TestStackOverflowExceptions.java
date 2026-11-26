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
 * @test id=unrecoverable
 * @bug 8339569
 * @summary Verifies unrecoverable stack overflow handling
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @library /test/lib
 * @run main/native ${test.main.class} fatal
 */

/*
 * @test id=interpreter-recoverable-java
 * @bug 8339569
 * @summary Verifies recoverable stack overflow handling in the interpreter
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm -Xint ${test.main.class} recoverable-java
 */

/*
 * @test id=c1-recoverable-java
 * @bug 8339569
 * @summary Verifies recoverable stack overflow handling in C1
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 ${test.main.class} recoverable-java
 */

/*
 * @test id=c2-recoverable-java
 * @bug 8339569
 * @summary Verifies recoverable stack overflow handling in C2
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation ${test.main.class} recoverable-java
 */

/*
 * @test id=interpreter-recoverable-native
 * @bug 8339569
 * @summary Verifies native execution can access all yellow pages before
 *          and after a recoverable stack overflow in the interpreter
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xint ${test.main.class} recoverable-native
 */

/*
 * @test id=c1-recoverable-native
 * @bug 8339569
 * @summary Verifies native execution can access all yellow pages before
 *          and after a recoverable stack overflow in C1
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @requires vm.compiler1.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xcomp -XX:TieredStopAtLevel=1 ${test.main.class} recoverable-native
 */

/*
 * @test id=c2-recoverable-native
 * @bug 8339569
 * @summary Verifies native execution can access all yellow pages before
 *          and after a recoverable stack overflow in C2
 * @requires os.family == "windows" | os.family == "linux" | os.family == "mac"
 * @requires vm.flagless
 * @requires vm.compiler2.enabled
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xcomp -XX:-TieredCompilation ${test.main.class} recoverable-native
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestStackOverflowExceptions {
    private static native void bangRedZone();
    private static native void bangYellowZone(int redPageCount, int yellowPageCount);

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "fatal" -> testFatalOverflow();
            case "native" -> testNativeOverflow();
            case "recoverable-java" -> testRecoverableJavaOverflow();
            case "recoverable-native" -> testRecoverableNativeYellow();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static void testFatalOverflow() throws Exception {
        // Spawn a new JVM process that calls the `bangRedZone()` function,
        // which then makes a native thread bang a red zone page, after which we
        // check whether HotSpot terminated with the unrecoverable overflow.
        OutputAnalyzer output = ProcessTools.executeTestJava(
                "-XX:-CreateCoredumpOnCrash",
                "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
                TestStackOverflowExceptions.class.getName(),
                "native");
        output.shouldNotHaveExitValue(0);
        output.shouldContain("An irrecoverable stack overflow has occurred.");
    }

    private static void testNativeOverflow() {
        System.loadLibrary("TestStackOverflowExceptions");

        // Bang a single address in the Red zone, which should terminate the JVM.
        bangRedZone();

        // HotSpot should have terminated with an unrecoverable overflow.
        String msg = "Unexpected return from native red-zone bang, expected termination";
        throw new RuntimeException(msg);
    }

    private static void testRecoverableJavaOverflow() {
        triggerRecoverableJavaOverflow();

        // On Windows, the first stack overflow is handled using
        // `EXCEPTION_STACK_OVERFLOW`, while subsequent overflows are handled
        // using `EXCEPTION_ACCESS_VIOLATION`.  To ensure that both code paths
        // are working correctly, we trigger the recoverable overflow twice,
        // checking each time whether HotSpot raised `StackOverflowError`.
        triggerRecoverableJavaOverflow();
    }

    private static void testRecoverableNativeYellow() {
        System.loadLibrary("TestStackOverflowExceptions");

        WhiteBox whiteBox = WhiteBox.getWhiteBox();
        int redPages = whiteBox.getIntxVMFlag("StackRedPages").intValue();
        int yellowPages = whiteBox.getIntxVMFlag("StackYellowPages").intValue();
        bangYellowZone(redPages, yellowPages);

        // Permissions for stack pages change after `EXCEPTION_STACK_OVERFLOW`,
        // which Windows raises after a recoverable overflow.  So to ensure that
        // accesses to the yellow zone work with either set of page permissions,
        // we intersperse the yellow zone accesses with a recoverable overflow.
        triggerRecoverableJavaOverflow();

        bangYellowZone(redPages, yellowPages);
    }

    private static void triggerRecoverableJavaOverflow() {
        try {
            recurse();
        } catch (StackOverflowError err) {
            return;
        }

        throw new RuntimeException("Expected StackOverflowError");
    }

    private static void recurse() {
        recurse();
    }
}
