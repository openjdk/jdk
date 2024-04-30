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

import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @summary Example test to use setup method (provide arguments and set fields).
 * @library /test/lib /
 * @run driver ir_framework.examples.SetupExample
 */

/**
 * This file shows some examples of how to use a setup method, annotated with {@link Setup}, and referenced by
 * a test method with @Arguments(setup = "setupMethodName").
 *
 * @see Setup
 * @see Arguments
 * @see Test
 */
public class SetupExample {
    private static final Random RANDOM = Utils.getRandomInstance();

    int iFld1, iFld2;

    public static void main(String[] args) {
        TestFramework.run();
    }

    // ----------------- Random but Linked --------------
    @Setup
    static Object[] setupLinkedII() {
        int r = RANDOM.nextInt();
        return new Object[]{ r, r + 42 };
    }

    @Test
    @Arguments(setup = "setupLinkedII")
    static int testSetupLinkedII(int a, int b) {
        return b - a;
    }

    @Check(test = "testSetupLinkedII")
    static void checkSetupLinkedII(int res) {
        if (res != 42) {
            throw new RuntimeException("wrong result " + res);
        }
    }

    // ----------------- Random Arrays --------------
    static int[] generateI(int len) {
        int[] a = new int[len];
        for (int i = 0; i < len; i++) {
            a[i] = RANDOM.nextInt();
        }
        return a;
    }

    @Setup
    static Object[] setupRandomArrayII() {
        // Random length, so that AutoVectorization pre/main/post and drain loops are tested
        int len = RANDOM.nextInt(20_000);
        int[] a = generateI(len);
        int[] b = generateI(len);
        return new Object[] { a, b };
    }

    @Test
    @Arguments(setup = "setupRandomArrayII")
    static Object[] testAdd(int[] a, int[] b) {
        int[] c = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
        return new Object[] { a, b, c };
    }

    @Check(test = "testAdd")
    static void checkAdd(Object[] res) {
        int[] a = (int[])res[0];
        int[] b = (int[])res[1];
        int[] c = (int[])res[2];
        for (int i = 0; i < a.length; i++) {
            if (c[i] != a[i] + b[i]) {
                throw new RuntimeException("wrong values: " + a[i] + " " + b[i] + " " + c[i]);
            }
        }
    }

    // ----------------- Setup Fields ---------------
    @Setup
    void setupFields() {
        int r = RANDOM.nextInt();
        iFld1 = r;
        iFld2 = r + 42;
    }

    @Test
    @Arguments(setup = "setupFields")
    int testSetupFields() {
        return iFld2 - iFld1;
    }

    @Check(test = "testSetupFields")
    static void checkSetupFields(int res) {
        if (res != 42) {
            throw new RuntimeException("wrong result " + res);
        }
    }

    // ----------------- Deterministic Values -------
    @Setup
    Object[] setupDeterministic(SetupInfo info) {
        // This value increments with every invocation of the setup method: 0, 1, 2, ...
        int cnt = info.invocationCounter();

        // Return true with low frequency. If we did this randomly, we can get unlucky
        // and never return true. So doing it deterministically can be helpful when we
        // want "low frequency" but a guaranteed "true" at some point.
        return new Object[]{ cnt % 1_000 };
    }

    @Test
    @Arguments(setup = "setupDeterministic")
    @IR(counts = {IRNode.STORE_OF_FIELD, "iFld1", "1",
                  IRNode.STORE_OF_FIELD, "iFld2", "1"})
    void testLowProbabilityBranchDeterministic(int x) {
        if (x == 7) {
            // unlikely branch -> guaranteed taken -> in profile -> not trapped -> in IR
            iFld1 = 42;
        } else {
            // likely branch
            iFld2 = 77;
        }
    }
}
