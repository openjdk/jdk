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
 *
 */

/*
 * @test
 * @bug 8370102
 * @requires vm.debug
 * @library / /test/lib
 * @summary Test to ensure the signatures in -XX:+TraceBytecodes are printed in edge cases
 * @run driver TraceBytecodesSignatures
 */

import java.io.IOException;
import java.util.List;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TraceBytecodesSignatures {

    public static void main(String[] args)
            throws InterruptedException, IOException {
        if (args.length == 1 && "worker".equals(args[0])) {
            runTest();
            return;
        }
        // Enable byteocde tracing but disable compilation of the key methods
        // that we will trace.
        String[] processArgs = new String[] {
            "-XX:+TraceBytecodes",
            exclude("runTest"),
            exclude("testSameMethod"),
            exclude("testSameMethodHelper"),
            excludeConstructor(),
            exclude("testSelfRecursive"),
            exclude("testSelfRecursiveHelper"),
            klass(),
            "worker"
        };
        // Create a VM process and trace its bytecodes.
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(processArgs);
        OutputAnalyzer oa = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0);
        analyze(oa.stdoutAsLines());
    }

    private static void runTest() {
        testSameMethod(true);
        testSameMethod(false);
        new TestConstructor();
        new TestConstructor();
        testSelfRecursive(3);
    }

    // Expect 3 signature prints:
    // 1. The first call
    // 2. The first call switch back after the helper is called
    // 3. The second call
    private static final int EXPECTED_SAME_METHOD = 3;

    // Expect 2 signature prints:
    // 1. The first call of the no-arg constructor
    // 2. Switch back after calling the 1-arg constructor
    // 3. The second call of the no-arg constructor
    // 4. Switch back again
    private static final int EXPECTED_CONSTRUCTOR = 4;

    // Expect 10 signature prints:
    // 1. The first recursive call
    // 2. The first switch back after the helper is called.
    // 3. The second recursive call
    // 4. The second switch back
    // 5. The third recursive call
    // 6. The third switch back
    // 7. The fourth recursive call: base case hit
    // 8-10. Switch back to i=1, i=2, i=3, respectively
    private static final int EXPECTED_SELF_RECURSIVE_METHOD = 10;

    // Ensure that the expected signatures are printed the correct nr of times.
    // The signatures here need to be changed if any of the test code is changed.
    private static void analyze(List<String> lines) {
        System.out.println("Analyzing " + lines.size() + " lines");
        int testSameMethodCount = 0;
        int testConstructorCount = 0;
        int testSelfRecursiveCount = 0;
        for (String line : lines) {
            if (line.contains("static void TraceBytecodesSignatures.testSameMethod(jboolean)")) {
                testSameMethodCount++;
            } else if (line.contains("virtual void TraceBytecodesSignatures$TestConstructor.<init>()")) {
                testConstructorCount++;
            } else if (line.contains("static void TraceBytecodesSignatures.testSelfRecursive(jint)")) {
                testSelfRecursiveCount++;
            }
        }
        if (testSameMethodCount != EXPECTED_SAME_METHOD) {
            throw new RuntimeException("testSameMethod: "
                                       + EXPECTED_SAME_METHOD
                                       + " != "
                                       + testSameMethodCount);
        }
        if (testConstructorCount != EXPECTED_CONSTRUCTOR) {
            throw new RuntimeException("testConstructor: "
                                       + EXPECTED_CONSTRUCTOR
                                       + " != "
                                       + testConstructorCount);
        }
        if (testSelfRecursiveCount != EXPECTED_SELF_RECURSIVE_METHOD) {
            throw new RuntimeException("testSelfRecursive: "
                                       + EXPECTED_SELF_RECURSIVE_METHOD
                                       + " != "
                                       + testSelfRecursiveCount);
        }
    }

    // Use a variable to do some arithmetic on to represent work.
    // This work should not produce method invocations, hence integer arithmetic.
    private static int globalState = 0;

    private static void testSameMethod(boolean other) {
        globalState = 1;
        if (other) {
            testSameMethodHelper();
        }
        globalState = 1;
    }

    private static void testSameMethodHelper() {
        globalState = 5;
    }

    private static final class TestConstructor {
        // Represents some kind of mutable state, not necessarily a object attribute.
        private static String foo = "test";

        public TestConstructor() {
            this("bar");
        }

        public TestConstructor(String other) {
            foo = other;
        }
    }

    private static void testSelfRecursive(int i) {
        if (i == 0) {
            return;
        }
        globalState += 2;
        // Ensure to generate another method call within the recursive method.
        // This will trigger the method switches more often.
        testSelfRecursiveHelper();
        globalState -= 2;
        testSelfRecursive(i - 1);
    }

    private static void testSelfRecursiveHelper() {
        globalState = -1;
    }

    // CompileCommand is accepted even if the VM does not use JIT compilation.
    private static String exclude(String methodName) {
        return "-XX:CompileCommand=exclude," + klass() + "." + methodName;
    }

    private static String excludeConstructor() {
        return "-XX:CompileCommand=exclude," + klass() + "$TestConstructor.<init>";
    }

    private static String klass() {
        return TraceBytecodesSignatures.class.getName();
    }
}
