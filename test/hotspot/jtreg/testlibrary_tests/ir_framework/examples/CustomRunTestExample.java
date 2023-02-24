/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.examples;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.test.TestVM; // Only used for Javadocs

/*
 * @test
 * @summary Example test to use the new test framework.
 * @library /test/lib /
 * @run driver ir_framework.examples.CustomRunTestExample
 */

/**
 * If there is no warm-up specified, the Test Framework will do the following:
 * <ol>
 *     <li><p>Invoke @Run method {@link TestVM#WARMUP_ITERATIONS} many times. Note that the @Run method
 *            is responsible to invoke the @Test methods to warm it up properly. This is not done by the framework. Not
 *            invoking a @Test method will result in an -Xcomp like compilation of the method as there is no profile
 *            information for it. The @Run method can do any arbitrary argument setup and return value verification and
 *            can invoke the @Test methods multiple times in a single invocation of the @Run method or even skip some
 *            test invocations.</li>
 *     <li><p>After the warm-up, the @Test methods are compiled (there can be multiple @Test methods).</li>
 *     <li><p>Invoke the @Run method once again.</li>
 * </ol>
 * <p>
 *
 * Configurable things for custom run tests:
 * <ul>
 *     <li><p>At @Test methods:</li>
 *     <ul>
 *         <li><p>@IR: Arbitrary number of @IR rules.</li>
 *         <li><p>No @Warmup, this must be set at @Run method.</li>
 *         <li><p>No @Arguments, the arguments are set by @Run method.</li>
 *     </ul>
 *     <li><p>At @Run method:</li>
 *     <ul>
 *         <li><p>@Warmup: Change warm-up iterations of @Run method (defined by default by
 *                         TestVM.WARMUP_ITERATIONS)</li>
 *         <li><p>{@link Run#test}: Specify any number of @Test methods. They cannot be shared with other @Check or @Run
 *                                  methods.</li>
 *         <li><p>{@link Run#mode}: Choose between normal invocation as described above or {@link RunMode#STANDALONE}.
 *                                  STANDALONE only invokes the @Run method once without warm-up or a compilation by the
 *                                  Test Framework. The only thing done by the framework is the verification of any @IR
 *                                  rules afterwards. The STANDALONE @Run method needs to make sure that a C2 compilation
 *                                  is reliably triggered if there are any @IR rules.</li>
 *         <li><p>No @IR annotations</li>
 *     </ul>
 * </ul>
 *
 * @see Run
 * @see Test
 * @see RunMode
 * @see TestFramework
 */
public class CustomRunTestExample {

    public static void main(String[] args) {
        TestFramework.run(); // equivalent to TestFramework.run(CustomRunTestExample.class)
    }

    @Test
    public int test(int x) {
        return x;
    }

    // Run method for test(). Invoked directly by Test Framework instead of test().
    // Can do anything you like. It's also possible to skip or do multiple invocations of test()
    @Run(test = "test") // Specify the @Test method for which this method is a runner.
    public void basicRun() {
        int returnValue = test(34);
        if (returnValue != 34) {
            throw new RuntimeException("Must match");
        }
    }

    @Test
    public int test2(int x) {
        return x;
    }

    // This version of @Run passes the RunInfo object as an argument. No other arguments and combinations are allowed.
    @Run(test = "test2")
    public void runWithRunInfo(RunInfo runInfo) {
        // We could invoke a test multiple times or even skip some invocations completely. This might have an influence
        // on profiling and possible @IR rules as the graph could be different.
        if (runInfo.isWarmUp()) {
            // Do something only when warming this method up (i.e. the IR framework has not queued this method for
            // compilation, yet).
        }
        if (RunInfo.getRandom().nextBoolean()) {
            int returnValue = test2(34);
            if (returnValue != 34) {
                throw new RuntimeException("Must match");
            }
        }
    }

    @Test
    public int test3(int x) {
        return x;
    }

    // This version of @Run uses a user defined @Warmup.
    @Run(test = "test3")
    @Warmup(100)
    public void runWithWarmUp() {
        int returnValue = test3(34);
        if (returnValue != 34) {
            throw new RuntimeException("Must match");
        }
    }

    @Test
    public int test3a(int x) {
        return x;
    }

    // To simulate -Xcomp, we can specify a zero warm-up. The IR framework directly compiles the @Test method test3a()
    // before any invocation of it (called over this @Run method). Afterwards the IR framework invokes the @Run method
    // exactly once.
    @Run(test = "test3a")
    @Warmup(0)
    public void runSimulateXcomp() {
        int returnValue = test3a(34);
        if (returnValue != 34) {
            throw new RuntimeException("Must match");
        }
    }

    @Test
    public int test4(int x) {
        return x;
    }

    // This version of @Run is only invoked once by the Test Framework. There is no warm-up and no compilation done
    // by the Test Framework. The only thing done by the framework is @IR rule verification.
    @Run(test = "test4", mode = RunMode.STANDALONE)
    public void runOnlyOnce() {
        int returnValue = test4(34);
        if (returnValue != 34) {
            throw new RuntimeException("Must match");
        }
    }

    @Test
    public int test5(int x) {
        return x;
    }

    @Test
    public int test6(int x) {
        return x;
    }

    // This version of @Run can run multiple test methods and get them IR checked as part of this custom run test.
    @Run(test = {"test5", "test6"})
    public void runMultipleTests() {
        int returnValue = test5(34);
        if (returnValue != 34) {
            throw new RuntimeException("Must match");
        }
        returnValue = test6(42);
        if (returnValue != 42) {
            throw new RuntimeException("Must match");
        }
    }

    @Test
    public int test7(int x) {
        return x;
    }

    // RunMode.STANDALONE gives the user full control over how the associated @Test method is compiled: The IR framework
    // only invokes this @Run method once, without any additional warm-up iterations, and does NOT initiate a compilation.
    // This is entirely left to the @Run method to do. When also doing IR matching, it needs to be ensured that a C2
    // compilation is triggered. Otherwise, IR matching will fail due to a missing C2 compilation output to match on.
    @Run(test = "test7", mode = RunMode.STANDALONE)
    public void run() {
        final int interpreterResult = test7(34); // First invocation of test7() with interpreter
        int compiledResult = 0;
        for (int i = 0; i < 10000; i++) {
            compiledResult = test7(34); // At some point, test7() is normally C1 and C2 compiled.
            if (interpreterResult != compiledResult) {
                // At some point, compiledResult is the value returned by C1 compiled code and later C2 compiled code
                // of test7(). These values should always match the value returned by the interpreter.
                throw new RuntimeException("Different result compared to interpreter run");
            }
        }
        // At this point, test7() is definitely C2 compiled due to the high number of invocations in the loop above.
        // There is not further invocation of this method (and hence of test7()).
    }
}
