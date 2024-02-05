/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.SetupInfo;

/*
 * @test
 * @summary Example test to use setup method (provide arguments and set fields).
 * @library /test/lib /
 * @run driver ir_framework.examples.SetupExample
 */

/**
 * This file shows some examples of how to use a setup method, annotated with {@link Setup}, and referenced by
 * a test method with @Arguments(setup = {"setupMethodName"}).
 *
 * @see Setup
 * @see Arguments
 * @see Test
 */
public class SetupExample {
    int iFld, iFld2, iFld3;
    public static void main(String[] args) {
        TestFramework.run();
    }

    // Test with static setup, test and check method.
    @Setup
    static Object[] setupTwoIntArrays() {
        int[] a = new int[10_000];
        int[] b = new int[10_000];
        for (int i = 0; i < a.length; i++) {
            a[i] = i - 2;
            b[i] = i + 2;
        }
        return new Object[]{a, b}; // passed as arguments to test method
    }

    @Test
    @Arguments(setup = "setupTwoIntArrays")
    static Object[] testWithSetupRandomIntArray(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            int aa = a[i];
            int bb = b[i];
            a[i] = aa + bb;
            b[i] = aa - bb;
        }
        return new Object[]{a, b}; // passed as argument to check method
    }

    @Check(test = "testWithSetupRandomIntArray")
    void checkTestWithSetupRandomIntArray(Object[] args) {
        int[] a = (int[])args[0]; // parse return values of test method
        int[] b = (int[])args[1];

        if (a.length != 10_000 || b.length != 10_000) {
            throw new RuntimeException("bad length");
        }

        for (int i = 0; i < a.length; i++) {
            if ((a[i] != 2 * i) || (b[i] != -4)) {
                throw new RuntimeException("bad value: " + i + ": " + a[i] + " " + b[i]);
            }
        }
    }

    // Test with non-static setup, test and check method.
    @Setup
    Object[] setupTestSetupArgumentsAndFields(SetupInfo info) {
        iFld  = info.invocationCounter();
        iFld2 = info.invocationCounter() + 1;
        iFld3 = info.invocationCounter() + 2;
        return new Object[]{info.invocationCounter()}; // passed as arguments to test method
    }

    @Test
    @Arguments(setup = "setupTestSetupArgumentsAndFields")
    int testSetupArgumentsAndFields(int argVal) {
        if ((iFld != argVal) || (iFld2 != argVal + 1) || (iFld3 != argVal + 2)) {
            throw new RuntimeException("bad values: setup -> test");
        }
        return argVal + 2; // passed as argument to check method
    }

    @Check(test = "testSetupArgumentsAndFields")
    void checkTestSetupArgumentsAndFields(int retVal) {
        if ((iFld + 2 != retVal) || (iFld2 + 1 != retVal) || (iFld3 != retVal)) {
            throw new RuntimeException("bad values: test -> check");
        }
    }
}
