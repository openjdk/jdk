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

/*
 * @test
 * @summary Example test to show Verify.checkEQ with IR framework.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver verify.examples.TestVerifyInCheckMethod
 */

package verify.examples;

import compiler.lib.verify.*;
import compiler.lib.ir_framework.*;

/**
 * Example to show the use of Verify.checkEQ in @Check method.
 */
public class TestVerifyInCheckMethod {
    public static int[] INPUT_A = new int[100];
    static {
        for (int i = 0; i < INPUT_A.length; i++) {
            INPUT_A[i] = i;
        }
    }
    public static float INPUT_B = 42;

    // Must make sure to clone input arrays, if it is mutated in the test.
    public static Object GOLD = test(INPUT_A.clone(), INPUT_B);;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Setup
    public static Object[] setup() {
        // Must make sure to clone input arrays, if it is mutated in the test.
        return new Object[] {INPUT_A.clone(), INPUT_B};
    }

    @Test
    @Arguments(setup = "setup")
    public static Object test(int[] a, float b) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (int)(a[i] * b);
        }
        // Since we have more than one value, we wrap them in an Object[].
        return new Object[] {a, b};
    }

    @Check(test = "test")
    public static void check(Object result) {
        Verify.checkEQ(result, GOLD);
    }
}
