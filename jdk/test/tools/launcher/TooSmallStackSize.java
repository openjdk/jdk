/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6762191
 * @ignore 8146751
 * @summary Setting stack size to 16K causes segmentation fault
 * @compile TooSmallStackSize.java
 * @run main TooSmallStackSize
 */

/*
 * The primary purpose of this test is to make sure we can run with a 16k stack
 * size without crashing. Also this test will determine the minimum allowed
 * stack size for the platform (as provided by the JVM error message when a very
 * small stack is used), and then verify that the JVM can be launched with that stack
 * size without a crash or any error messages.
 */

public class TooSmallStackSize extends TestHelper {
    /* for debugging. Normally false. */
    static final boolean verbose = false;

    static void printTestOutput(TestResult tr) {
        System.out.println("*** exitValue = " + tr.exitValue);
        for (String x : tr.testOutput) {
            System.out.println(x);
        }
    }

    /*
     * Returns the minimum stack size this platform will allowed based on the
     * contents of the error message the JVM outputs when too small of a
     * -Xss size was used.
     *
     * The TestResult argument must contain the result of having already run
     * the JVM with too small of a stack size.
     */
    static String getMinStackAllowed(TestResult tr) {
        /*
         * The JVM output will contain in one of the lines:
         *   "The stack size specified is too small, Specify at least 100k"
         * Although the actual size will vary. We need to extract this size
         * string from the output and return it.
         */
        String matchStr = "Specify at least ";
        for (String x : tr.testOutput) {
            int match_idx = x.indexOf(matchStr);
            if (match_idx >= 0) {
                int size_start_idx = match_idx + matchStr.length();
                int k_start_idx = x.indexOf("k", size_start_idx);
                return x.substring(size_start_idx, k_start_idx + 1); // include the "k"
            }
        }

        System.out.println("FAILED: Could not get the stack size from the output");
        throw new RuntimeException("test fails");
    }

    /*
     * Run the JVM with the specified stack size.
     *
     * Returns the minimum allowed stack size gleaned from the error message,
     * if there is an error message. Otherwise returns the stack size passed in.
     */
    static String checkStack(String stackSize) {
        String min_stack_allowed;
        TestResult tr;

        if (verbose)
            System.out.println("*** Testing " + stackSize);
        tr = doExec(javaCmd, "-Xss" + stackSize, "-version");
        if (verbose)
            printTestOutput(tr);

        if (tr.isOK()) {
            System.out.println("PASSED: got no error message with stack size of " + stackSize);
            min_stack_allowed = stackSize;
        } else {
            if (tr.contains("The stack size specified is too small")) {
                System.out.println("PASSED: got expected error message with stack size of " + stackSize);
                min_stack_allowed = getMinStackAllowed(tr);
            } else {
                // Likely a crash
                System.out.println("FAILED: Did not get expected error message with stack size of " + stackSize);
                throw new RuntimeException("test fails");
            }
        }

        return min_stack_allowed;
    }

    /*
     * Run the JVM with the minimum allowed stack size. This should always succeed.
     */
    static void checkMinStackAllowed(String stackSize) {
        TestResult tr = null;

        if (verbose)
            System.out.println("*** Testing " + stackSize);
        tr = doExec(javaCmd, "-Xss" + stackSize, "-version");
        if (verbose)
            printTestOutput(tr);

        if (tr.isOK()) {
            System.out.println("PASSED: VM launched with minimum allowed stack size of " + stackSize);
        } else {
            // Likely a crash
            System.out.println("FAILED: VM failed to launch with minimum allowed stack size of " + stackSize);
            throw new RuntimeException("test fails");
        }
    }

    public static void main(String... args) {
        /*
         * The result of a 16k stack size should be a quick exit with a complaint
         * that the stack size is too small. However, for some win32 builds, the
         * stack is always at least 64k, and this also sometimes is the minimum
         * allowed size, so we won't see an error in this case.
         *
         * This test case will also produce a crash on some platforms if the fix
         * for 6762191 is not yet in place.
         */
        checkStack("16k");

        /*
         * Try with a 32k stack size, which is the size that the launcher will
         * set to if you try setting to anything smaller. This should produce the same
         * result as setting to 16k if the fix for 6762191 is in place.
         */
        String min_stack_allowed = checkStack("32k");

        /*
         * Try again with a the minimum stack size that was given in the error message
         */
        checkMinStackAllowed(min_stack_allowed);
    }
}
