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

package testlibrary_tests.ir_framework.examples;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.TestVMException;
import compiler.lib.ir_framework.shared.TestFormatException;

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Example to show how to skip individual IR tests over commenting them out or problemlisting.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

/**
 * When a general jtreg test is failing, there are several options to mitigate that problem including uncommenting the
 * problematic part of the test out or just problemlisting.
 *
 * <p>
 * The IR Framework introduces another way: Use {@link Skip @Skip} at a {@link Test @Test}-annotated method to exclude
 * this test from being executed.
 *
 * <p>
 * The IR Framework provides the following additional flag to run those skipped tests on the fly without requiring
 * any additional test or IR Framework changes (also see <i>README.md</i>):
 * <ul>
 *     <li><p><b>-DIgnoreSkip=true:</b> The IR Framework executes all tests - including {@link Skip @Skip}-annotated
 *                                      tests.</li>
 * </ul>
 *
 * <p>
 * Below you find some examples.
 *
 * @see Skip
 */
public class SkipTestExample {
    public static void main(String[] args) {
        header("SkipTestExample Tests:");
        TestFramework.run();
        try {
            header("SkipTestExample Tests - -DIgnoreSkip=true:");
            TestFramework.runWithFlags("-DIgnoreSkip=true");
            Asserts.fail("Should report TestVMException");
        } catch (TestVMException e) {
            // Expected, uncomment to see the output:
            //throw e;
        }

        try {
            header("Violation Tests:");
            TestFramework.run(Violation.class);
            Asserts.fail("Should report TestFormatException");
        } catch (TestFormatException e) {
            // Expected, uncomment to see the output:
            //throw e;
        }
    }

    private static void header(String header) {
        System.out.println(header);
        System.out.println("=".repeat(header.length()));
    }

    // Skipped test that would otherwise fail.
    // - -DIgnoreSkip=true:      This test will fail and is reported as such.
    @Test
    @Skip
    public void testWithSkip() {
        throw new RuntimeException("skipped");
    }

    @Test
    @Skip
    public void testForRunner() {
    }

    // testWithSkip2() is skipped and thus the IR Framework is not executing this @Run method.
    // - -DIgnoreSkip=true:      This test will fail and is reported as such.
    @Run(test = "testForRunner")
    public void runWithSkip() {
        testForRunner();
        throw new RuntimeException("skipped");
    }
}

class Violation {
    @Test
    @Skip
    public void testSkip() {}

    @Test
    public void testNotSkipped() {}

    // Even though not all tests that are called from this @Run method are skipped, there is no way to modify the
    // code to call testSkip() but avoid calling testNotSkipped(). Therefore, the entire @Run method should be excluded
    // which in turn could be undesirable. Therefore, the IR Framework rejects such partially skipped tests with a
    // TestFormatException by requiring either all @Test methods to be skipped or none.
    @Run(test = {"testSkip",
                 "testNotSkipped"})
    public void runMultipleTests() {
        testSkip();
        testNotSkipped();
    }
}
